package com.taczvr.server;

import com.taczvr.TaczVRConfig;
import com.taczvr.VrCommon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side record of where each VR player's gun is pointing.
 */
public final class ServerAimStore {
    /**
     * Aim packets older than this are ignored and the raw Vivecraft controller pose is used instead.
     */
    private static final long MAX_AGE_TICKS = 10;

    private static final Map<UUID, Entry> AIMS = new ConcurrentHashMap<>();
    // a shotgun asks for the aim once per pellet, keep the resolved aim for the rest of the tick
    private static final Map<UUID, Cached> RESOLVED = new ConcurrentHashMap<>();

    private record Entry(Vec3 originOffset, Vec3 direction, long gameTime) {
    }

    private record Cached(long gameTime, Vec3 playerPos, @Nullable Aim aim) {
    }

    public record Aim(Vec3 origin, Vec3 direction) {
    }

    private ServerAimStore() {
    }

    public static void update(ServerPlayer player, Vec3 originOffset, Vec3 direction) {
        if (!isFinite(originOffset) || !isFinite(direction) || direction.lengthSqr() < 1.0E-6) {
            return;
        }
        AIMS.put(player.getUUID(), new Entry(originOffset, direction.normalize(), player.getLevel().getGameTime()));
        RESOLVED.remove(player.getUUID());
    }

    /**
     * @return the aim of a VR player, or null if the player isn't in VR or the feature is off
     */
    @Nullable
    public static Aim getAim(ServerPlayer player) {
        // our client only sends aims while in VR, so they count even when the server's VR mod doesn't know the player
        // (a server without Vivecraft, for example)
        if (!TaczVRConfig.COMMON.serverVrAim.get() || !VrCommon.isVRPlayer(player) && !hasFreshAim(player)) {
            return null;
        }
        long now = player.getLevel().getGameTime();
        Cached cached = RESOLVED.get(player.getUUID());
        if (cached != null && cached.gameTime == now && cached.playerPos.equals(player.position())) {
            return cached.aim;
        }
        Aim aim = resolve(player);
        RESOLVED.put(player.getUUID(), new Cached(now, player.position(), aim));
        return aim;
    }

    /**
     * Our client sent a VR aim in the last few ticks, which it only does while in VR.
     */
    public static boolean hasFreshAim(ServerPlayer player) {
        Entry entry = AIMS.get(player.getUUID());
        return entry != null && player.getLevel().getGameTime() - entry.gameTime <= MAX_AGE_TICKS;
    }

    @Nullable
    private static Aim resolve(ServerPlayer player) {
        Vec3 origin = null;
        Vec3 dir = null;
        Entry entry = AIMS.get(player.getUUID());
        if (entry != null && player.getLevel().getGameTime() - entry.gameTime <= MAX_AGE_TICKS) {
            origin = player.position().add(entry.originOffset);
            dir = entry.direction;
        } else {
            // the client doesn't have this mod, use the main hand controller as the gun
            VrCommon.Hand hand = VrCommon.getMainHand(player);
            if (hand != null) {
                origin = hand.pos();
                dir = hand.dir();
            }
        }
        if (origin == null || dir == null) {
            return null;
        }
        Vec3 eye = VrCommon.getHeadPos(player);
        if (eye == null) {
            eye = player.getEyePosition();
        }
        double maxDist = TaczVRConfig.COMMON.maxAimOriginDistance.get() * Math.max(1.0, player.getBbHeight() / 1.8);
        if (origin.distanceToSqr(eye) > maxDist * maxDist) {
            return null;
        }
        return new Aim(clipToWalls(player, eye, origin), dir);
    }

    /**
     * Stops shooting through walls by poking the barrel through them: the bullet starts where
     * the line from the head to the muzzle first hits a block.
     */
    public static Vec3 clipToWalls(Player player, Vec3 eye, Vec3 origin) {
        BlockHitResult hit = player.getLevel().clip(new ClipContext(eye, origin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            return origin;
        }
        Vec3 back = eye.subtract(hit.getLocation());
        double len = back.length();
        if (len < 1.0E-4) {
            return eye;
        }
        return hit.getLocation().add(back.scale(Math.min(0.05, len) / len));
    }

    private static boolean isFinite(Vec3 v) {
        return Double.isFinite(v.x) && Double.isFinite(v.y) && Double.isFinite(v.z);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        AIMS.remove(event.getEntity().getUUID());
        RESOLVED.remove(event.getEntity().getUUID());
    }

    /**
     * Converts a direction to Minecraft pitch (degrees, positive is down).
     */
    public static float pitchOf(Vec3 dir) {
        return (float) Math.toDegrees(-Math.asin(Math.max(-1.0, Math.min(1.0, dir.y))));
    }

    /**
     * Converts a direction to Minecraft yaw (degrees).
     */
    public static float yawOf(Vec3 dir) {
        return (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
    }
}
