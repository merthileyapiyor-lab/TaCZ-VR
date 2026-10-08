package com.taczvr.server;

import com.taczvr.compat.TaczCompat;
import com.taczvr.TaczVRConfig;
import com.taczvr.VrCommon;
import com.taczvr.network.Net;
import com.taczvr.network.OffhandFiredPacket;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.sound.SoundManager;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dual wielding: the gun in a VR player's off-hand. TACZ only knows the main hand, so shots and reloads of the
 * off-hand gun are done here, with TACZ's own bullets, ammo and sounds.
 */
public final class OffhandGunServer {
    private static final int RELOAD_TICKS = 30;
    private static final double SPREAD = 1.2;
    private static final Map<UUID, Long> LAST_SHOT = new HashMap<>();
    private static final Map<UUID, Integer> RELOADING = new HashMap<>();
    static int shots = 0;

    private OffhandGunServer() {
    }

    /**
     * Both hands hold a TACZ gun, the off-hand one is fired by this mod. The main hand's gun is needed too,
     * TACZ builds bullets from the shooter's gun data.
     */
    public static boolean dualWielding(ServerPlayer player) {
        // like the aim: our client's VR aim counts when the server's VR mod doesn't know the player
        return TaczVRConfig.COMMON.dualWield.get() && (VrCommon.isVRPlayer(player) || ServerAimStore.hasFreshAim(player))
                && IGun.getIGunOrNull(player.getMainHandItem()) != null && IGun.getIGunOrNull(player.getOffhandItem()) != null;
    }

    @Nullable
    private static GunData data(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        return iGun == null ? null : TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).map(CommonGunIndex::getGunData).orElse(null);
    }

    public static void shoot(ServerPlayer player, Vec3 originOffset, Vec3 direction) {
        if (!dualWielding(player) || !player.isAlive() || player.isSpectator() || direction.lengthSqr() < 1.0E-6
                || !Double.isFinite(originOffset.x + originOffset.y + originOffset.z + direction.x + direction.y + direction.z)) {
            return;
        }
        ItemStack gun = player.getOffhandItem();
        AbstractGunItem gunItem = (AbstractGunItem) gun.getItem();
        GunData data = data(gun);
        IGunOperator operator = IGunOperator.fromLivingEntity(player);
        if (data == null || RELOADING.containsKey(player.getUUID()) || operator.getCacheProperty() == null) {
            return;
        }
        // no faster than the gun's rate of fire
        long now = System.currentTimeMillis();
        long interval = 60_000L / Math.max(1, TaczCompat.rpm(gunItem, gun));
        Long last = LAST_SHOT.get(player.getUUID());
        if (last != null && now - last < interval * 0.8) {
            return;
        }
        boolean free = player.isCreative() || ServerAssist.infiniteAmmo(player);
        if (!free && !takeRound(gunItem, gun, data)) {
            return;
        }
        LAST_SHOT.put(player.getUUID(), now);
        Vec3 eye = player.getEyePosition();
        Vec3 origin = player.position().add(originOffset);
        double maxDistance = TaczVRConfig.COMMON.maxAimOriginDistance.get();
        if (origin.distanceToSqr(eye) > maxDistance * maxDistance) {
            origin = eye;
        }
        origin = ServerAimStore.clipToWalls(player, eye, origin);
        Vec3 dir = direction.normalize();
        BulletData bullet = data.getBulletData();
        float speed = bullet.getSpeed() / 20.0F;
        float spread = ServerAssist.aimAssist(player) ? 0.0F : (float) SPREAD;
        for (int i = 0; i < Math.max(1, bullet.getBulletAmount()); i++) {
            EntityKineticBullet shot = new EntityKineticBullet(player.getLevel(), player, gun, data.getAmmoId(),
                    gunItem.getGunId(gun), bullet.hasTracerAmmo(), data, bullet);
            shot.setPos(origin);
            shot.shoot(dir.x, dir.y, dir.z, speed, spread);
            player.getLevel().addFreshEntity(shot);
        }
        shots++;
        SoundManager.sendSoundToNearby(player, 64, gunItem.getGunId(gun), gunItem.getGunDisplayId(gun), SoundManager.SHOOT_3P_SOUND,
                0.8F, 0.9F + player.getRandom().nextFloat() * 0.125F);
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), new OffhandFiredPacket(player.getId()));
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Uses up the round being fired. Closed bolt guns fire the chambered round and chamber the next one.
     */
    private static boolean takeRound(AbstractGunItem gunItem, ItemStack gun, GunData data) {
        int ammo = gunItem.getCurrentAmmoCount(gun);
        if (data.getBolt() == Bolt.CLOSED_BOLT) {
            if (!gunItem.hasBulletInBarrel(gun)) {
                return false;
            }
            if (ammo > 0) {
                gunItem.setCurrentAmmoCount(gun, ammo - 1);
            } else {
                gunItem.setBulletInBarrel(gun, false);
            }
            return true;
        }
        if (ammo <= 0) {
            return false;
        }
        gunItem.setCurrentAmmoCount(gun, ammo - 1);
        return true;
    }

    public static void reload(ServerPlayer player) {
        if (!dualWielding(player) || RELOADING.containsKey(player.getUUID())) {
            return;
        }
        ItemStack gun = player.getOffhandItem();
        GunData data = data(gun);
        AbstractGunItem gunItem = (AbstractGunItem) gun.getItem();
        if (data == null || TaczCompat.useInventoryAmmo(gunItem, gun)
                || gunItem.getCurrentAmmoCount(gun) >= AttachmentDataUtils.getAmmoCountWithAttachment(gun, data) && gunItem.hasBulletInBarrel(gun)) {
            return;
        }
        RELOADING.put(player.getUUID(), RELOAD_TICKS);
        SoundManager.sendSoundToNearby(player, 16, gunItem.getGunId(gun), gunItem.getGunDisplayId(gun), SoundManager.RELOAD_TACTICAL_SOUND, 1.0F, 1.0F);
    }

    public static boolean reloading(ServerPlayer player) {
        return RELOADING.containsKey(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || RELOADING.isEmpty() || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        Integer left = RELOADING.get(player.getUUID());
        if (left == null) {
            return;
        }
        if (left > 1) {
            RELOADING.put(player.getUUID(), left - 1);
            return;
        }
        RELOADING.remove(player.getUUID());
        ItemStack gun = player.getOffhandItem();
        GunData data = data(gun);
        if (data == null || !(gun.getItem() instanceof AbstractGunItem gunItem)) {
            return;
        }
        int max = AttachmentDataUtils.getAmmoCountWithAttachment(gun, data);
        int current = gunItem.getCurrentAmmoCount(gun);
        int needed = max - current;
        boolean free = player.isCreative() || ServerAssist.infiniteAmmo(player);
        int found = needed <= 0 ? 0 : free ? needed : gunItem.useDummyAmmo(gun) ? gunItem.findAndExtractDummyAmmo(gun, needed)
                : player.getCapability(ForgeCapabilities.ITEM_HANDLER, null).map(cap -> gunItem.findAndExtractInventoryAmmos(cap, gun, needed)).orElse(0);
        gunItem.setCurrentAmmoCount(gun, current + found);
        // chamber the first round
        if (data.getBolt() == Bolt.CLOSED_BOLT && !gunItem.hasBulletInBarrel(gun) && gunItem.getCurrentAmmoCount(gun) > 0) {
            gunItem.setCurrentAmmoCount(gun, gunItem.getCurrentAmmoCount(gun) - 1);
            gunItem.setBulletInBarrel(gun, true);
        }
        player.inventoryMenu.broadcastChanges();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SHOT.remove(event.getEntity().getUUID());
        RELOADING.remove(event.getEntity().getUUID());
    }

    public static int shotCount() {
        return shots;
    }
}
