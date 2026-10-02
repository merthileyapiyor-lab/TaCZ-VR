package com.taczvr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.taczvr.TaczVRConfig;
import com.taczvr.network.MagazineStatePacket;
import com.taczvr.network.Net;
import com.taczvr.network.VrMagazinePacket;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.GunModelConstant;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Manual magazine changes for the local player: A drops the magazine, the off-hand takes a new one from the belt
 * and pushes it into the magazine well, then racks the charging handle if the chamber is empty.
 */
public final class MagazineHandler {
    private static final double INSERT_REACH = 0.07;
    private static final long DROP_MILLIS = 1500;
    private static final String[] CHARGING_HANDLE_BONES = {"charging_handle", "bolt_handle", "slide", "bolt"};

    private static boolean magazineOut = false;
    private static boolean holdingMagazine = false;
    private static boolean needsRack = false;
    @Nullable
    private static String trackedGun = null;
    // what other players were last told, see syncState
    private static boolean sentOut = false;
    private static boolean sentHolding = false;
    private static final List<DroppedMagazine> DROPPED = new ArrayList<>();

    private record DroppedMagazine(Vec3 start, Quaternionf rotation, float scale, long time) {
    }

    private MagazineHandler() {
    }

    /**
     * True if the gun is reloaded by hand: manual mode is on and the gun has a detachable magazine.
     */
    public static boolean appliesTo(ItemStack gun) {
        return TaczVRConfig.CLIENT.manualMagazine.get() && VrMagazinePacket.magazineGunData(gun) != null && serverHasMod();
    }

    public static boolean isMagazineOut() {
        return magazineOut;
    }

    public static boolean isHoldingMagazine() {
        return holdingMagazine;
    }

    public static boolean needsRack() {
        return needsRack;
    }

    /**
     * TACZ's own reload (A button) puts a full magazine in, whatever state the manual reload was in.
     */
    static void cancelManualReload() {
        magazineOut = false;
        holdingMagazine = false;
        needsRack = false;
    }

    static void reset() {
        magazineOut = false;
        holdingMagazine = false;
        needsRack = false;
        trackedGun = null;
        syncState();
    }

    /**
     * Lets the players around see the magazine come out and the new one in your hand. Sent only on changes.
     */
    static void syncState() {
        if (magazineOut == sentOut && holdingMagazine == sentHolding || !serverHasMod()) {
            return;
        }
        sentOut = magazineOut;
        sentHolding = holdingMagazine;
        Net.CHANNEL.sendToServer(new MagazineStatePacket(magazineOut, holdingMagazine));
    }

    static void forgetSentState() {
        sentOut = false;
        sentHolding = false;
    }

    /**
     * Keeps the state tied to one gun (hotbar slot + gun type, the ammo count keeps changing the item's tags);
     * switching weapons puts the magazine back, like TACZ would.
     */
    static void track(LocalPlayer player, ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        String key = player.getInventory().selected + ":" + (iGun == null ? "" : iGun.getGunId(gun));
        if (!key.equals(trackedGun)) {
            magazineOut = false;
            holdingMagazine = false;
            needsRack = false;
        }
        trackedGun = key;
    }

    /**
     * The off-hand pulled the magazine out of the gun: it drops, the rounds left in it go back to the inventory.
     */
    public static void eject(LocalPlayer player, @Nullable GunPoseSolver.Pose pose) {
        if (magazineOut) {
            return;
        }
        Net.CHANNEL.sendToServer(new VrMagazinePacket(VrMagazinePacket.Action.EJECT));
        magazineOut = true;
        holdingMagazine = false;
        needsRack = false;
        player.playSound(SoundEvents.IRON_TRAPDOOR_OPEN, 0.5F, 1.8F);
        if (pose != null) {
            Vector3d mag = GunPoseSolver.boneWorld(pose, GunModelConstant.MAG_NORMAL_NODE);
            if (mag != null) {
                DROPPED.add(new DroppedMagazine(new Vec3(mag.x, mag.y, mag.z), new Quaternionf(pose.rotation),
                        pose.scale, System.currentTimeMillis()));
            }
        }
        if (TaczVRConfig.CLIENT.haptics.get()) {
            VrClient.haptic(VrHand.MAIN_HAND, 0.05F, 0.5F);
        }
    }

    /**
     * Off-hand grabbed a magazine from the belt.
     */
    public static void grab(LocalPlayer player) {
        holdingMagazine = true;
        player.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 0.6F, 1.2F);
    }

    public static void letGo() {
        holdingMagazine = false;
    }

    /**
     * Pushes the held magazine in once it reaches the magazine well.
     */
    static void tick(LocalPlayer player, ItemStack gun, VrPose vrPose, GunPoseSolver.Pose pose) {
        track(player, gun);
        if (!holdingMagazine) {
            return;
        }
        VrPart off = vrPose.getOffHand();
        Vector3d well = GunPoseSolver.boneWorld(pose, GunModelConstant.MAG_NORMAL_NODE);
        if (off == null || well == null) {
            return;
        }
        Vec3 hand = off.getPos();
        if (well.distance(hand.x, hand.y, hand.z) < INSERT_REACH * pose.scale / 0.3) {
            Net.CHANNEL.sendToServer(new VrMagazinePacket(VrMagazinePacket.Action.INSERT));
            magazineOut = false;
            holdingMagazine = false;
            GunData data = VrMagazinePacket.magazineGunData(gun);
            needsRack = data != null && data.getBolt() == Bolt.CLOSED_BOLT && gun.getItem() instanceof AbstractGunItem g
                    && !g.hasBulletInBarrel(gun);
            player.playSound(SoundEvents.ARMOR_EQUIP_IRON, 0.8F, 1.3F);
            if (TaczVRConfig.CLIENT.haptics.get()) {
                VrClient.haptic(VrHand.OFF_HAND, 0.06F, 0.8F);
                VrClient.haptic(VrHand.MAIN_HAND, 0.04F, 0.5F);
            }
        }
    }

    /**
     * Charging handle / slide was pulled back and let go.
     */
    public static void rack(LocalPlayer player) {
        Net.CHANNEL.sendToServer(new VrMagazinePacket(VrMagazinePacket.Action.CHAMBER));
        needsRack = false;
        player.playSound(SoundEvents.CROSSBOW_LOADING_END, 0.7F, 1.6F);
        if (TaczVRConfig.CLIENT.haptics.get()) {
            VrClient.haptic(VrHand.OFF_HAND, 0.05F, 1.0F);
            VrClient.haptic(VrHand.MAIN_HAND, 0.05F, 0.7F);
        }
    }

    /**
     * Where the off-hand grabs the charging handle or slide.
     */
    public static Vector3d chargingHandle(GunPoseSolver.Pose pose) {
        for (String bone : CHARGING_HANDLE_BONES) {
            Vector3d at = GunPoseSolver.boneWorld(pose, bone);
            if (at != null && at.distance(pose.grip) < 0.4 * pose.scale / 0.3 && GunPoseSolver.isOnGun(pose, at, 0.1)) {
                return at;
            }
        }
        // top of the receiver just above the grip
        return pose.toWorld(new Vector3f(0.0F, 0.2F, -0.05F).add(gripLocal(pose)));
    }

    private static Vector3f gripLocal(GunPoseSolver.Pose pose) {
        Vector3f toGrip = new Vector3f((float) (pose.grip.x - pose.origin.x), (float) (pose.grip.y - pose.origin.y),
                (float) (pose.grip.z - pose.origin.z));
        return new Quaternionf(pose.rotation).conjugate().transform(toGrip).div(pose.scale);
    }

    /**
     * The belt: anywhere around the waist, below the chest and close to the body.
     */
    public static boolean isAtBelt(VrPose vrPose, Vec3 hand, float worldScale) {
        VrPart head = vrPose.getHead();
        if (head == null) {
            return false;
        }
        Vec3 h = head.getPos();
        double below = h.y - hand.y;
        double horizontal = Math.hypot(h.x - hand.x, h.z - hand.z);
        return below > 0.4 * worldScale && below < 1.1 * worldScale && horizontal < 0.45 * worldScale;
    }

    // --- rendering ---

    @Nullable
    static BedrockPart magazinePart(BedrockGunModel model) {
        List<BedrockPart> path = GunPoseSolver.bonePath(model, GunModelConstant.MAG_NORMAL_NODE);
        return path == null || path.isEmpty() ? null : path.get(path.size() - 1);
    }

    /**
     * Draws the gun's magazine on its own, its pivot at {@code at}, turned like {@code rotation}.
     */
    static void drawMagazine(Minecraft mc, GunPoseSolver.Pose pose, PoseStack poseStack, Vec3 cam,
                             Vec3 at, Quaternionfc rotation, float scale) {
        BedrockGunModel model = pose.model;
        List<BedrockPart> path = GunPoseSolver.bonePath(model, GunModelConstant.MAG_NORMAL_NODE);
        if (path == null || path.isEmpty()) {
            return;
        }
        BedrockPart magazine = path.get(path.size() - 1);
        boolean wasVisible = magazine.visible;
        magazine.visible = true;
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at.x, at.y, at.z));
        // position of the magazine pivot in the aim frame, so it can be moved to the hand
        Vector3f magLocal = GunPoseSolver.boneInAimFrame(pose, path);
        poseStack.pushPose();
        poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        poseStack.mulPose(new Quaternionf(rotation));
        poseStack.scale(scale, scale, scale);
        poseStack.translate(-magLocal.x, -magLocal.y, -magLocal.z);
        GunPoseSolver.applyAimFrameChain(poseStack, pose.viewPath);
        for (int i = 0; i < path.size() - 1; i++) {
            path.get(i).translateAndRotateAndScale(poseStack);
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutout(pose.display.getModelTexture());
        VertexConsumer consumer = buffers.getBuffer(type);
        magazine.render(poseStack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, consumer, light, OverlayTexture.NO_OVERLAY);
        buffers.endBatch(type);
        poseStack.popPose();
        magazine.visible = wasVisible;
    }

    /**
     * Magazines that were just dropped, falling to the floor.
     */
    static void drawDropped(Minecraft mc, GunPoseSolver.Pose pose, PoseStack poseStack, Vec3 cam) {
        long now = System.currentTimeMillis();
        Iterator<DroppedMagazine> it = DROPPED.iterator();
        while (it.hasNext()) {
            DroppedMagazine drop = it.next();
            long age = now - drop.time;
            if (age > DROP_MILLIS) {
                it.remove();
                continue;
            }
            double t = age / 1000.0;
            Vec3 at = drop.start.add(0.0, -4.9 * t * t, 0.0);
            drawMagazine(mc, pose, poseStack, cam, at, drop.rotation, drop.scale);
        }
    }

    private static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && Net.CHANNEL.isRemotePresent(connection.getConnection());
    }
}
