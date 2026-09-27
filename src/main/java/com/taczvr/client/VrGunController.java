package com.taczvr.client;

import com.taczvr.TaczVRConfig;
import com.taczvr.client.interact.AttachmentModule;
import com.taczvr.network.Net;
import com.taczvr.network.VrAimPacket;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.input.ShootKey;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

/**
 * Per tick VR gun handling for the local player: trigger, aiming down sights, reloading and telling the server
 * where the gun points.
 */
public final class VrGunController {
    private static final long KICK_MILLIS = 150;
    private static final double ADS_MIN_HEAD_ALIGNMENT = Math.cos(Math.toRadians(40.0));
    private static final int MAG_TAP_TICKS = 3;

    private static final int MELEE_COOLDOWN_TICKS = 10;

    private static boolean shootHeld = false;
    private static boolean aimOwned = false;
    private static boolean twoHandedLastTick = false;
    private static boolean useWasDown = false;
    private static int magTapTicks = 0;
    private static int reloadCooldown = 0;
    private static int meleeCooldown = 0;
    private static long lastShotMillis = -1;
    @Nullable
    private static Vec3 lastRoomHandPos = null;
    @Nullable
    private static GunPoseSolver.Pose lastPose = null;
    @Nullable
    private static VRPose lastVrPose = null;

    private VrGunController() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // START so TACZ's own shoot handler at END sees the trigger state of this tick
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean active = player != null && TaczVRConfig.CLIENT.enabled.get() && !player.isSpectator() && VrClient.isVRActive();
        ItemStack stack = active ? player.getMainHandItem() : ItemStack.EMPTY;
        IGun iGun = IGun.getIGunOrNull(stack);
        if (!active || iGun == null) {
            release(player);
            return;
        }
        if (reloadCooldown > 0) {
            reloadCooldown--;
        }
        if (meleeCooldown > 0) {
            meleeCooldown--;
        }
        // TACZ shifts your own tracers by the flat screen muzzle offset, our bullets already start at the real muzzle
        GunItemRendererWrapper.muzzleRenderOffset.set(0.0F, 0.0F, 0.0F);
        boolean inGame = mc.screen == null && mc.getOverlay() == null;

        // Vivecraft maps the trigger to the vanilla attack key, TACZ listens to its own shoot key
        boolean trigger = inGame && mc.options.keyAttack.isDown();
        ShootKey.shootControllerTick(trigger);
        shootHeld = trigger;

        VRPose vrPose = VrClient.localTickPose();
        if (vrPose == null) {
            return;
        }
        GunPoseSolver.Pose gun = GunPoseSolver.solve(player, stack, vrPose, Vec3.ZERO, VrClient.localWorldScale(), 0.0F, 0.0F);
        if (gun == null) {
            lastPose = null;
            return;
        }
        lastPose = gun;
        lastVrPose = vrPose;
        twoHandedLastTick = gun.twoHanded;
        sendAim(mc, player, gun);

        IClientPlayerGunOperator operator = IClientPlayerGunOperator.fromLocalPlayer(player);
        updateAim(operator, vrPose, gun, inGame);
        ScopeView.update(stack, iGun, vrPose, gun, operator.isAim() && inGame);
        if (inGame) {
            AttachmentModule.tickAutoMount(player, gun, vrPose);
        }
        boolean manualMagazine = MagazineHandler.appliesTo(stack);
        if (manualMagazine) {
            MagazineHandler.tick(player, stack, vrPose, gun);
        } else {
            updateReload(player, operator, iGun, stack, vrPose, gun);
        }
        updateButtonReload(mc, player, operator, inGame);
        updateMeleeThrust(operator, inGame);
        MagazineHandler.syncState();
    }

    /**
     * Guns can't be "used", so Vivecraft's use button (A on Quest) is free to reload with: TACZ's normal animated
     * reload, which also refills a magazine that was taken out by hand.
     */
    private static void updateButtonReload(Minecraft mc, LocalPlayer player, IClientPlayerGunOperator operator, boolean inGame) {
        if (!TaczVRConfig.CLIENT.buttonReload.get()) {
            return;
        }
        // a quick tap can be pressed and released between two ticks, count the clicks instead of polling isDown
        boolean pressed = false;
        while (mc.options.keyUse.consumeClick()) {
            pressed = true;
        }
        boolean down = mc.options.keyUse.isDown();
        pressed |= down && !useWasDown;
        useWasDown = down;
        if (!pressed || !inGame) {
            return;
        }
        // a gun in the other hand reloads along with this one
        OffhandGun.reload(player);
        if (IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading()) {
            return;
        }
        MagazineHandler.cancelManualReload();
        operator.reload();
    }

    /**
     * A fast jab along the barrel is a stock/bayonet hit. Measured in room space, so walking or turning doesn't count.
     */
    private static void updateMeleeThrust(IClientPlayerGunOperator operator, boolean inGame) {
        VRPose room = VrClient.latestRoomPose();
        VRBodyPartData hand = room == null ? null : room.getMainHand();
        if (hand == null) {
            lastRoomHandPos = null;
            return;
        }
        Vec3 pos = hand.getPos();
        Vec3 previous = lastRoomHandPos;
        lastRoomHandPos = pos;
        if (previous == null || !inGame || !TaczVRConfig.CLIENT.meleeThrust.get() || meleeCooldown > 0) {
            return;
        }
        // blocks per tick to meters per second
        Vec3 velocity = pos.subtract(previous).scale(20.0);
        double forward = velocity.dot(hand.getDir());
        if (forward > TaczVRConfig.CLIENT.meleeThrustSpeed.get() && forward > velocity.length() * 0.75) {
            operator.melee();
            meleeCooldown = MELEE_COOLDOWN_TICKS;
            if (TaczVRConfig.CLIENT.haptics.get()) {
                VrClient.haptic(VRBodyPart.MAIN_HAND, 0.08F, 1.0F);
            }
        }
    }

    /**
     * Gun pose from the last tick, null when the local player isn't holding a gun in VR.
     */
    @Nullable
    public static GunPoseSolver.Pose lastPose() {
        return lastPose;
    }

    /**
     * VR pose of the local player from the last tick, null when not holding a gun in VR.
     */
    @Nullable
    public static VRPose lastVrPose() {
        return lastVrPose;
    }

    /**
     * Development self-test only: pretend a tick produced these poses.
     */
    static void setTestPoses(@Nullable GunPoseSolver.Pose pose, @Nullable VRPose vrPose) {
        lastPose = pose;
        lastVrPose = vrPose;
    }

    private static void sendAim(Minecraft mc, LocalPlayer player, GunPoseSolver.Pose gun) {
        ClientPacketListener connection = mc.getConnection();
        if (connection == null || !Net.CHANNEL.isRemotePresent(connection.getConnection())) {
            return;
        }
        Vec3 muzzle = new Vec3(gun.muzzle.x, gun.muzzle.y, gun.muzzle.z);
        Vec3 dir = gun.bulletDirection(TaczVRConfig.CLIENT.zeroDistance.get());
        dir = ClientAssist.assistAim(mc, player, muzzle, dir);
        Net.CHANNEL.sendToServer(new VrAimPacket(muzzle.subtract(player.position()), dir));
    }

    /**
     * Aiming down sights: the head is close to the sight line, behind the sights, looking along the barrel.
     */
    private static void updateAim(IClientPlayerGunOperator operator, VRPose vrPose, GunPoseSolver.Pose gun, boolean inGame) {
        TaczVRConfig.Client cfg = TaczVRConfig.CLIENT;
        if (!cfg.aimDownSights.get()) {
            if (aimOwned) {
                operator.aim(false);
                aimOwned = false;
            }
            return;
        }
        VRBodyPartData head = vrPose.getHead();
        boolean aiming = false;
        if (inGame && head != null) {
            Vec3 headPos = head.getPos();
            Vector3d toHead = new Vector3d(headPos.x, headPos.y, headPos.z).sub(gun.origin);
            double along = toHead.dot(gun.forward);
            double offLine = new Vector3d(toHead).sub(new Vector3d(gun.forward).mul(along)).length();
            Vec3 headDir = head.getDir();
            double alignment = headDir.x * gun.forward.x + headDir.y * gun.forward.y + headDir.z * gun.forward.z;
            aiming = offLine < cfg.adsMaxEyeOffset.get()
                    && along < 0.15
                    && along > -cfg.adsMaxEyeDistance.get()
                    && alignment > ADS_MIN_HEAD_ALIGNMENT;
        }
        if (aiming != operator.isAim()) {
            operator.aim(aiming);
        }
        aimOwned = true;
    }

    private static void updateReload(LocalPlayer player, IClientPlayerGunOperator operator, IGun iGun, ItemStack stack,
                                     VRPose vrPose, GunPoseSolver.Pose gun) {
        TaczVRConfig.Client cfg = TaczVRConfig.CLIENT;
        // guns that feed from the inventory have no magazine to reload
        if (iGun.useInventoryAmmo(stack)) {
            return;
        }
        boolean reloading = IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading();

        VRBodyPartData offHand = vrPose.getOffHand();
        if (cfg.magazineTapReload.get() && gun.magazine != null && offHand != null && !gun.twoHanded) {
            Vec3 off = offHand.getPos();
            double dist = gun.magazine.distance(off.x, off.y, off.z);
            magTapTicks = dist < cfg.magazineTapRadius.get() ? magTapTicks + 1 : 0;
            if (magTapTicks == MAG_TAP_TICKS && !reloading && reloadCooldown == 0) {
                operator.reload();
                reloadCooldown = 20;
                if (cfg.haptics.get()) {
                    VrClient.haptic(VRBodyPart.OFF_HAND, 0.05F, 0.6F);
                }
            }
        } else {
            magTapTicks = 0;
        }
    }

    private static void release(@Nullable LocalPlayer player) {
        if (shootHeld) {
            ShootKey.shootControllerTick(false);
            shootHeld = false;
        }
        if (aimOwned) {
            if (player != null) {
                IClientPlayerGunOperator.fromLocalPlayer(player).aim(false);
            }
            aimOwned = false;
        }
        magTapTicks = 0;
        twoHandedLastTick = false;
        useWasDown = false;
        lastRoomHandPos = null;
        lastPose = null;
        lastVrPose = null;
        ScopeView.stop();
        MagazineHandler.reset();
    }

    @SubscribeEvent
    public static void onGunFire(GunFireEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.getLogicalSide().isClient() || event.getShooter() != mc.player || !VrClient.isVRActive()) {
            return;
        }
        lastShotMillis = System.currentTimeMillis();
        if (TaczVRConfig.CLIENT.haptics.get()) {
            VrClient.haptic(VRBodyPart.MAIN_HAND, 0.06F, 1.0F);
            if (twoHandedLastTick) {
                VrClient.haptic(VRBodyPart.OFF_HAND, 0.04F, 0.6F);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        release(null);
        lastShotMillis = -1;
        MagazineHandler.forgetSentState();
        ClientAssist.reset();
    }

    private static float kickProgress() {
        if (lastShotMillis < 0 || !TaczVRConfig.CLIENT.visualRecoil.get()) {
            return 0.0F;
        }
        long t = System.currentTimeMillis() - lastShotMillis;
        if (t >= KICK_MILLIS) {
            return 0.0F;
        }
        float f = 1.0F - (float) t / KICK_MILLIS;
        return f * f;
    }

    /**
     * Visual recoil: how far the muzzle is currently rotated up, in radians.
     */
    public static float kickRadians() {
        return (float) Math.toRadians(TaczVRConfig.CLIENT.recoilKickDegrees.get()) * kickProgress();
    }

    /**
     * Visual recoil: how far the gun is currently pushed back, in meters.
     */
    public static float kickBack() {
        return 0.015F * kickProgress();
    }
}
