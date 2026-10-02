package com.taczvr.client;

import com.taczvr.TaczVRConfig;
import com.taczvr.network.Net;
import com.taczvr.network.OffhandReloadPacket;
import com.taczvr.network.OffhandShootPacket;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPose;

import java.util.UUID;

/**
 * Dual wielding for VR players: with a TACZ gun in each hand, the off-hand one sits in the left controller and fires
 * with the left trigger (Vivecraft's teleport button, teleporting is off meanwhile). The server fires it,
 * see {@link com.taczvr.server.OffhandGunServer}.
 */
public final class OffhandGun {
    private static boolean triggerWasDown = false;
    private static long lastShot = 0;
    private static long firedMillis = -1;
    @Nullable
    private static GunPoseSolver.Pose lastPose;
    static int shotsSent = 0;

    private OffhandGun() {
    }

    /**
     * A TACZ gun in each hand of a VR player.
     */
    public static boolean holds(Player player) {
        return TaczVRConfig.CLIENT.enabled.get() && IGun.getIGunOrNull(player.getMainHandItem()) != null
                && IGun.getIGunOrNull(player.getOffhandItem()) != null;
    }

    /**
     * Key for the pose solver's per player state, separate from the main gun's.
     */
    static UUID key(UUID player) {
        return new UUID(player.getMostSignificantBits() ^ 0x6f666668616e64L, player.getLeastSignificantBits());
    }

    /**
     * The pose with the off-hand as the hand holding the gun, and no second hand to hold it with.
     */
    static VrPose offHandAsMain(VrPose pose) {
        return VrPose.of(pose.getHead(), pose.getOffHand(), null, !pose.isLeftHanded(), pose.isSeated());
    }

    @Nullable
    static GunPoseSolver.Pose solve(Player player, VrPose pose, Vec3 offset, float worldScale) {
        if (pose.getOffHand() == null) {
            return null;
        }
        return GunPoseSolver.solve(key(player.getUUID()), player.getOffhandItem(), offHandAsMain(pose), offset, worldScale,
                player == Minecraft.getInstance().player ? kick() : 0.0F, 0.0F, false);
    }

    @Nullable
    static GunPoseSolver.Pose lastPose() {
        return lastPose;
    }

    static long firedMillis() {
        return firedMillis;
    }

    private static float kick() {
        long t = System.currentTimeMillis() - firedMillis;
        if (firedMillis < 0 || t > 150 || !TaczVRConfig.CLIENT.visualRecoil.get()) {
            return 0.0F;
        }
        float f = 1.0F - t / 150.0F;
        return (float) Math.toRadians(TaczVRConfig.CLIENT.recoilKickDegrees.get()) * f * f;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        VrPose pose = player != null && VrClient.isVRActive() && holds(player) && !player.isSpectator() ? VrClient.localTickPose() : null;
        lastPose = pose == null ? null : solve(player, pose, Vec3.ZERO, VrClient.localWorldScale());
        boolean down = lastPose != null && mc.screen == null && VrClient.offHandTriggerDown();
        boolean pressed = down && !triggerWasDown;
        triggerWasDown = down;
        if (!down || !serverHasMod()) {
            return;
        }
        ItemStack gun = player.getOffhandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        FireMode mode = iGun.getFireMode(gun);
        long interval = 60_000L / Math.max(1, iGun.getRPM(gun));
        long now = System.currentTimeMillis();
        if (mode != FireMode.AUTO && !pressed || now - lastShot < interval) {
            return;
        }
        lastShot = now;
        GunDisplayInstance display = TimelessAPI.getGunDisplay(gun).orElse(null);
        GunData data = TimelessAPI.getClientGunIndex(iGun.getGunId(gun)).map(ClientGunIndex::getGunData).orElse(null);
        boolean loaded = data != null && (data.getBolt() == Bolt.CLOSED_BOLT ? iGun.hasBulletInBarrel(gun) : iGun.getCurrentAmmoCount(gun) > 0);
        if (!loaded && !player.isCreative()) {
            if (display != null && pressed) {
                SoundPlayManager.playDryFireSound(player, display);
            }
            return;
        }
        Vec3 muzzle = new Vec3(lastPose.muzzle.x, lastPose.muzzle.y, lastPose.muzzle.z);
        Vec3 dir = ClientAssist.assistAim(mc, player, muzzle, lastPose.bulletDirection(TaczVRConfig.CLIENT.zeroDistance.get()));
        Net.CHANNEL.sendToServer(new OffhandShootPacket(muzzle.subtract(player.position()), dir));
        shotsSent++;
        firedMillis = now;
        if (display != null && data != null) {
            SoundPlayManager.playShootSound(player, display, data);
        }
        if (TaczVRConfig.CLIENT.haptics.get()) {
            VrClient.haptic(VrHand.OFF_HAND, 0.06F, 1.0F);
        }
    }

    /**
     * Pressing reload (A) also reloads the off-hand gun.
     */
    static void reload(LocalPlayer player) {
        if (!holds(player) || !serverHasMod()) {
            return;
        }
        Net.CHANNEL.sendToServer(new OffhandReloadPacket());
        ItemStack gun = player.getOffhandItem();
        GunDisplayInstance display = TimelessAPI.getGunDisplay(gun).orElse(null);
        IGun iGun = IGun.getIGunOrNull(gun);
        if (display != null && iGun != null) {
            SoundPlayManager.playReloadSound(player, display, iGun.getCurrentAmmoCount(gun) == 0);
        }
    }

    private static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && Net.CHANNEL.isRemotePresent(connection.getConnection());
    }
}
