package com.taczvr.client;

import com.taczvr.compat.Buttons;
import com.taczvr.network.AssistPacket;
import com.taczvr.network.Net;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The assist menu for players the server lists (see the common config): a button in the pause screen with aim
 * assist, infinite ammo and glowing targets. The server decides who gets it and does the infinite ammo.
 */
public final class ClientAssist {
    private static final double AIM_RANGE = 128.0;
    // any player roughly in front of the gun gets locked onto, mobs only near where it points
    private static final double PLAYER_CONE_COS = Math.cos(Math.toRadians(75.0));
    private static final double MOB_CONE_COS = Math.cos(Math.toRadians(12.0));
    private static final double GLOW_RANGE = 96.0;

    private static boolean allowed = false;
    private static boolean aimAssist = false;
    private static boolean infiniteAmmo = false;
    private static boolean glow = false;

    private ClientAssist() {
    }

    public static void setAllowed(boolean value) {
        allowed = value;
        // after rejoining, the server has to hear the switches again
        if (allowed && (infiniteAmmo || aimAssist)) {
            send();
        }
    }

    static void reset() {
        allowed = false;
    }

    public static boolean allowed() {
        return allowed;
    }

    static boolean aimAssist() {
        return aimAssist;
    }

    static boolean infiniteAmmo() {
        return infiniteAmmo;
    }

    static boolean glow() {
        return glow;
    }

    static void setAimAssist(boolean value) {
        aimAssist = value;
        send();
    }

    static void setInfiniteAmmo(boolean value) {
        infiniteAmmo = value;
        send();
    }

    static void setGlow(boolean value) {
        glow = value;
    }

    private static void send() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null && Net.CHANNEL.isRemotePresent(connection.getConnection())) {
            Net.CHANNEL.sendToServer(new AssistPacket(infiniteAmmo, aimAssist));
        }
    }

    /**
     * Aim assist lock-on: the shot goes to the nearest player roughly in front of the gun (or, with no player around,
     * the mob closest to where it points), leading a moving target. The server then steers the bullet onto it.
     */
    static Vec3 assistAim(Minecraft mc, LocalPlayer player, Vec3 muzzle, Vec3 dir) {
        if (!allowed || !aimAssist || mc.level == null) {
            return dir;
        }
        double blocksPerTick = Math.max(1.0, bulletSpeed(player) / 20.0);
        Vec3 bestPlayer = null;
        double bestPlayerDistance = Double.MAX_VALUE;
        Vec3 bestMob = null;
        double bestMobCos = MOB_CONE_COS;
        AABB area = player.getBoundingBox().inflate(AIM_RANGE);
        for (Entity entity : mc.level.getEntities(player, area, ClientAssist::isTarget)) {
            boolean isPlayer = entity instanceof Player;
            Vec3 at = aimPoint(entity);
            double distance = at.distanceTo(muzzle);
            if (distance < 0.5 || distance > AIM_RANGE || isPlayer && distance >= bestPlayerDistance) {
                continue;
            }
            // where it will be when the bullet gets there
            Vec3 motion = new Vec3(entity.getX() - entity.xOld, entity.getY() - entity.yOld, entity.getZ() - entity.zOld);
            at = at.add(motion.scale(distance / blocksPerTick));
            Vec3 to = at.subtract(muzzle);
            double cos = to.dot(dir) / to.length();
            if (cos <= (isPlayer ? PLAYER_CONE_COS : bestMobCos) || !visible(mc, player, muzzle, at)) {
                continue;
            }
            if (isPlayer) {
                bestPlayerDistance = distance;
                bestPlayer = to.normalize();
            } else {
                bestMobCos = cos;
                bestMob = to.normalize();
            }
        }
        return bestPlayer != null ? bestPlayer : bestMob != null ? bestMob : dir;
    }

    private static boolean visible(Minecraft mc, LocalPlayer player, Vec3 from, Vec3 to) {
        BlockHitResult wall = mc.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        return wall.getType() == HitResult.Type.MISS || wall.getLocation().distanceTo(from) >= from.distanceTo(to) - 0.5;
    }

    private static boolean isTarget(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return entity instanceof LivingEntity living && living.isAlive() && !entity.isSpectator()
                && !(entity instanceof ArmorStand) && entity != mc.player
                && !(entity instanceof Player other && other.isCreative())
                && !(entity instanceof OwnableEntity pet && mc.player != null && mc.player.getUUID().equals(pet.getOwnerUUID()))
                && !entity.isPassengerOfSameVehicle(mc.player);
    }

    private static Vec3 aimPoint(Entity entity) {
        AABB box = entity.getBoundingBox();
        // upper chest: easier to hit than the head, still a good hit
        return new Vec3((box.minX + box.maxX) / 2.0, box.minY + box.getYsize() * 0.72, (box.minZ + box.maxZ) / 2.0);
    }

    private static double bulletSpeed(LocalPlayer player) {
        IGun iGun = IGun.getIGunOrNull(player.getMainHandItem());
        if (iGun == null) {
            return 300.0;
        }
        return TimelessAPI.getCommonGunIndex(iGun.getGunId(player.getMainHandItem()))
                .map(CommonGunIndex::getGunData).map(data -> (double) data.getBulletData().getSpeed()).orElse(300.0);
    }

    /**
     * Glowing targets: outlines living things around you, through walls too.
     */
    public static boolean glows(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        return allowed && glow && mc.player != null && isTarget(entity)
                && entity.distanceToSqr(mc.player) < GLOW_RANGE * GLOW_RANGE;
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (allowed && event.getScreen() instanceof PauseScreen pause) {
            event.addListener(Buttons.builder(Component.translatable("taczvr.assist.button"),
                    button -> Minecraft.getInstance().setScreen(new AssistScreen(pause))).bounds(8, 8, 110, 20).build());
        }
    }
}
