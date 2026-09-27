package com.taczvr.server;

import com.taczvr.TaczVRConfig;
import com.taczvr.network.AssistAllowedPacket;
import com.taczvr.network.Net;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The server half of the assist menu, for the players listed in the config: infinite ammo keeps the gun full, aim
 * assist takes away the spread and steers the bullets onto the locked target. Glowing targets are client side, the
 * server only tells the client it may use them.
 */
public final class ServerAssist {
    private static final double LOCK_RANGE = 160.0;
    private static final double PLAYER_LOCK_COS = Math.cos(Math.toRadians(75.0));
    private static final double MOB_LOCK_COS = Math.cos(Math.toRadians(12.0));

    private static final Set<UUID> INFINITE_AMMO = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> AIM_ASSIST = ConcurrentHashMap.newKeySet();
    // bullets of aim assisted shots and what they're locked onto, server thread only
    private static final List<Homing> HOMING = new ArrayList<>();

    private record Homing(EntityKineticBullet bullet, LivingEntity target) {
    }

    private ServerAssist() {
    }

    public static boolean allowed(ServerPlayer player) {
        String name = player.getGameProfile().getName().toLowerCase(Locale.ROOT);
        for (String entry : TaczVRConfig.COMMON.assistPlayers.get()) {
            if (entry.trim().toLowerCase(Locale.ROOT).equals(name)) {
                return true;
            }
        }
        return false;
    }

    public static void set(ServerPlayer player, boolean infiniteAmmo, boolean aimAssist) {
        boolean allowed = allowed(player);
        toggle(INFINITE_AMMO, player, infiniteAmmo && allowed);
        toggle(AIM_ASSIST, player, aimAssist && allowed);
    }

    private static void toggle(Set<UUID> set, ServerPlayer player, boolean on) {
        if (on) {
            set.add(player.getUUID());
        } else {
            set.remove(player.getUUID());
        }
    }

    public static boolean infiniteAmmo(ServerPlayer player) {
        return INFINITE_AMMO.contains(player.getUUID());
    }

    /**
     * Aim assist also takes away the random spread, the client already points the shot at the target.
     */
    public static boolean aimAssist(ServerPlayer player) {
        return AIM_ASSIST.contains(player.getUUID());
    }

    /**
     * Tells the client whether it gets the menu, again whenever the list may have changed.
     */
    public static void sendAllowed(ServerPlayer player) {
        if (!allowed(player)) {
            INFINITE_AMMO.remove(player.getUUID());
            AIM_ASSIST.remove(player.getUUID());
        }
        if (Net.CHANNEL.isRemotePresent(player.connection.connection)) {
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new AssistAllowedPacket(allowed(player)));
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sendAllowed(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        INFINITE_AMMO.remove(event.getEntity().getUUID());
        AIM_ASSIST.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onBulletSpawn(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || AIM_ASSIST.isEmpty() || !(event.getEntity() instanceof EntityKineticBullet bullet)
                || !(bullet.getOwner() instanceof ServerPlayer shooter) || !aimAssist(shooter)) {
            return;
        }
        ServerLevel level = shooter.serverLevel();
        List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class, bullet.getBoundingBox().inflate(LOCK_RANGE),
                entity -> !(entity instanceof Player) && !(entity instanceof ArmorStand));
        LivingEntity target = lockTarget(shooter, bullet.position(), bullet.getDeltaMovement(), level.players(), mobs);
        if (target != null) {
            HOMING.add(new Homing(bullet, target));
        }
    }

    /**
     * The nearest player roughly in front of the shot, else the mob closest to where it goes. Must be in sight.
     */
    @Nullable
    public static LivingEntity lockTarget(ServerPlayer shooter, Vec3 from, Vec3 direction,
                                          Iterable<? extends LivingEntity> players, Iterable<? extends LivingEntity> mobs) {
        Vec3 dir = direction.normalize();
        LivingEntity best = null;
        double bestDistance = LOCK_RANGE;
        for (LivingEntity player : players) {
            if (player == shooter || !player.isAlive() || player.isSpectator() || player instanceof Player p && p.isCreative()) {
                continue;
            }
            Vec3 to = chest(player).subtract(from);
            double distance = to.length();
            if (distance < bestDistance && to.dot(dir) / distance > PLAYER_LOCK_COS && visible(shooter, from, chest(player))) {
                best = player;
                bestDistance = distance;
            }
        }
        if (best != null) {
            return best;
        }
        double bestCos = MOB_LOCK_COS;
        for (LivingEntity mob : mobs) {
            if (mob == shooter || !mob.isAlive()) {
                continue;
            }
            Vec3 to = chest(mob).subtract(from);
            double distance = to.length();
            double cos = to.dot(dir) / distance;
            if (distance < LOCK_RANGE && cos > bestCos && visible(shooter, from, chest(mob))) {
                best = mob;
                bestCos = cos;
            }
        }
        return best;
    }

    private static Vec3 chest(Entity entity) {
        AABB box = entity.getBoundingBox();
        return new Vec3((box.minX + box.maxX) / 2.0, box.minY + box.getYsize() * 0.72, (box.minZ + box.maxZ) / 2.0);
    }

    private static boolean visible(Entity shooter, Vec3 from, Vec3 to) {
        BlockHitResult wall = shooter.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        return wall.getType() == HitResult.Type.MISS || wall.getLocation().distanceTo(from) >= from.distanceTo(to) - 0.5;
    }

    /**
     * Before the bullets move: turn every locked bullet towards where its target is going to be, keeping its speed.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.side != LogicalSide.SERVER || HOMING.isEmpty()) {
            return;
        }
        Iterator<Homing> it = HOMING.iterator();
        while (it.hasNext()) {
            Homing homing = it.next();
            EntityKineticBullet bullet = homing.bullet;
            LivingEntity target = homing.target;
            if (bullet.isRemoved() || target.isRemoved() || !target.isAlive() || bullet.level() != target.level()) {
                it.remove();
                continue;
            }
            if (bullet.level() != event.level) {
                continue;
            }
            Vec3 motion = target.position().subtract(target.xo, target.yo, target.zo);
            Vec3 to = chest(target).add(motion).subtract(bullet.position());
            double speed = bullet.getDeltaMovement().length();
            if (to.lengthSqr() > 1.0E-6 && speed > 1.0E-6) {
                bullet.setDeltaMovement(to.normalize().scale(speed));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || INFINITE_AMMO.isEmpty() || !INFINITE_AMMO.contains(player.getUUID())) {
            return;
        }
        topUp(player.getMainHandItem());
        topUp(player.getOffhandItem());
    }

    private static void topUp(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || iGun.useInventoryAmmo(gun)) {
            return;
        }
        GunData data = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).map(CommonGunIndex::getGunData).orElse(null);
        if (data == null) {
            return;
        }
        int max = AttachmentDataUtils.getAmmoCountWithAttachment(gun, data);
        if (iGun.getCurrentAmmoCount(gun) < max) {
            iGun.setCurrentAmmoCount(gun, max);
        }
        if (data.getBolt() == Bolt.CLOSED_BOLT && !iGun.hasBulletInBarrel(gun)) {
            iGun.setBulletInBarrel(gun, true);
        }
    }
}
