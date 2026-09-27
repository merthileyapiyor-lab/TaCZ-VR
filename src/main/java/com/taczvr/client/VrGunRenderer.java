package com.taczvr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.taczvr.TaczVR;
import com.taczvr.TaczVRConfig;
import com.taczvr.VrCommon;
import com.taczvr.client.interact.AttachmentModule;
import com.taczvr.mixin.client.MuzzleFlashRenderAccessor;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.functional.MuzzleFlashRender;
import com.tacz.guns.client.model.functional.ShellRender;
import com.tacz.guns.compat.oculus.OculusCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.client.render.VRPlayerModel;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Draws the TACZ gun of every VR player (including yourself) in world space, in the hand holding it.
 * <p>
 * Vivecraft skips the Forge hand render event in VR, so TACZ never draws its first person gun there, and for other
 * players TACZ glues the gun to the arm model which doesn't follow the controller rotation. Both are replaced by this.
 */
public final class VrGunRenderer {
    private static final long MUZZLE_FLASH_MILLIS = 50;
    private static boolean loggedError = false;

    private VrGunRenderer() {
    }

    /**
     * Whether this mod draws the given player's held gun, in which case the vanilla held item rendering must skip it.
     */
    public static boolean rendersHeldGun(Player player, ItemStack stack) {
        if (!TaczVRConfig.CLIENT.enabled.get() || IGun.getIGunOrNull(stack) == null) {
            return false;
        }
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        boolean held = stack == mainHand || ItemStack.matches(stack, mainHand);
        // dual wielding: the off-hand gun is drawn in the left controller too
        boolean offHeld = OffhandGun.holds(player) && (stack == offHand || ItemStack.matches(stack, offHand));
        return (held || offHeld) && handlesPlayer(player);
    }

    /**
     * Whether the player's arm gets replaced by a hand drawn on the gun's grip, in every view including your own.
     */
    static boolean handsOnGun(Player player) {
        return TaczVRConfig.CLIENT.handsOnGun.get();
    }

    /**
     * The off-hand sits on the gun while holding the handguard, and during your own reload animation, where TACZ
     * animates the left hand swapping the magazine.
     */
    static boolean leftHandOnGun(Player player) {
        if (GunPoseSolver.isTwoHanded(player.getUUID())) {
            return true;
        }
        return player == Minecraft.getInstance().player
                && IGunOperator.fromLivingEntity(player).getSynReloadState().getStateType().isReloading();
    }

    /**
     * Whether Vivecraft's own first person VR hand should be skipped because a hand is drawn on the gun instead.
     */
    public static boolean hidesFirstPersonHand(InteractionHand hand) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !handsOnGun(player) || !rendersHeldGun(player, player.getMainHandItem())) {
            return false;
        }
        boolean hide = hand == InteractionHand.MAIN_HAND || leftHandOnGun(player) || OffhandGun.holds(player);
        if (hide) {
            handsSkipped++;
        }
        return hide;
    }

    // counts skipped hand draws, lets the self-test see Vivecraft's hand rendering was really cut short
    static int handsSkipped = 0;
    // players drawn with Vivecraft's model this frame: their chunky model arm holds the gun (see GunArmFitter)
    private static final Set<UUID> FITTED_ARMS = new HashSet<>();

    /**
     * Whether TACZ's life-size hands are drawn on this player's gun. In your own first person view they replace
     * Vivecraft's hand. A player model's arm holds the gun itself: a life-size hand would stick out of its fist.
     */
    static boolean drawsGunHands(Player player) {
        if (!handsOnGun(player)) {
            return false;
        }
        if (player == Minecraft.getInstance().player) {
            RenderPass pass = VrClient.currentPass();
            return pass == null || RenderPass.isFirstPerson(pass);
        }
        return !FITTED_ARMS.contains(player.getUUID());
    }

    /**
     * A model arm that isn't posed by Vivecraft would swallow the gun, it's hidden. Vivecraft's VR model arms stay:
     * {@link GunArmFitter} shortens them to end at the wrist. Visibility is reset by the renderer every frame.
     */
    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Player player = event.getEntity();
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        boolean vivecraftArms = model instanceof VRPlayerModel<?>;
        if (vivecraftArms) {
            FITTED_ARMS.add(player.getUUID());
        } else {
            FITTED_ARMS.remove(player.getUUID());
        }
        if (vivecraftArms || !rendersHeldGun(player, player.getMainHandItem()) || !handsOnGun(player)) {
            return;
        }
        VRPose vrPose = VrClient.renderPose(player);
        boolean leftHanded = vrPose != null && vrPose.isLeftHanded();
        hideArm(model, leftHanded ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
        if (leftHandOnGun(player)) {
            hideArm(model, leftHanded ? HumanoidArm.RIGHT : HumanoidArm.LEFT);
        }
    }

    private static void hideArm(PlayerModel<AbstractClientPlayer> model, HumanoidArm arm) {
        if (arm == HumanoidArm.LEFT) {
            model.leftArm.visible = false;
            model.leftSleeve.visible = false;
        } else {
            model.rightArm.visible = false;
            model.rightSleeve.visible = false;
        }
    }

    private static boolean handlesPlayer(Player player) {
        if (player.isSpectator()) {
            return false;
        }
        if (player == Minecraft.getInstance().player) {
            return VrClient.isVRActive();
        }
        return VrCommon.isVRPlayer(player) && VrClient.renderPose(player) != null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !TaczVRConfig.CLIENT.enabled.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || OculusCompat.isRenderShadow()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        double maxDist = TaczVRConfig.CLIENT.remoteRenderDistance.get();
        for (Player player : mc.level.players()) {
            ItemStack stack = player.getMainHandItem();
            if (IGun.getIGunOrNull(stack) == null || !handlesPlayer(player)) {
                continue;
            }
            boolean local = player == mc.player;
            if (!local && (player.isInvisible() || player.distanceToSqr(cam) > maxDist * maxDist)) {
                continue;
            }
            // the scope camera sits inside your own scope, the gun would block its view
            RenderPass pass = VrClient.currentPass();
            if (local && (pass == RenderPass.SCOPER || pass == RenderPass.SCOPEL)) {
                continue;
            }
            try {
                renderGun(mc, player, stack, event.getPoseStack(), cam, partialTick, local);
                if (OffhandGun.holds(player)) {
                    renderOffhandGun(mc, player, event.getPoseStack(), cam, partialTick, local);
                }
            } catch (Throwable t) {
                if (!loggedError) {
                    loggedError = true;
                    TaczVR.LOGGER.error("Failed to render VR gun", t);
                }
            }
        }
    }

    private static void renderGun(Minecraft mc, Player player, ItemStack stack, PoseStack poseStack, Vec3 cam,
                                  float partialTick, boolean local) {
        VRPose vrPose = VrClient.renderPose(player);
        if (vrPose == null) {
            return;
        }
        GunPoseSolver.Pose pose = GunPoseSolver.solve(player, stack, vrPose,
                VrClient.remoteInterpolationOffset(player, partialTick),
                local ? VrClient.localWorldScale() : 1.0F,
                local ? VrGunController.kickRadians() : 0.0F,
                local ? VrGunController.kickBack() : 0.0F);
        if (pose == null) {
            return;
        }
        AbstractClientPlayer handsOf = player instanceof AbstractClientPlayer clientPlayer && drawsGunHands(player) ? clientPlayer : null;
        drawPose(mc, pose, stack, poseStack, cam, partialTick, local, player, handsOf);
        drawLaser(mc, poseStack, cam, pose, stack, player);
        if (!local) {
            // the new magazine in their off-hand during a manual magazine change
            VRBodyPartData off = vrPose.getOffHand();
            if (off != null && RemoteGunEffects.holdingMagazine(player)) {
                MagazineHandler.drawMagazine(mc, pose, poseStack, cam, off.getPos(), off.getRotation(), pose.scale);
            }
            return;
        }
        RenderPass pass = VrClient.currentPass();
        if (ScopeView.isViewing() && pass != null && RenderPass.isFirstPerson(pass)) {
            ScopeView.drawEyepiece(poseStack, cam, pose);
        }
        if (MagazineHandler.isHoldingMagazine()) {
            VRBodyPartData off = vrPose.getOffHand();
            if (off != null) {
                MagazineHandler.drawMagazine(mc, pose, poseStack, cam, off.getPos(), off.getRotation(), pose.scale);
            }
        }
        MagazineHandler.drawDropped(mc, pose, poseStack, cam);
        Vector3d hover = AttachmentModule.hoverPoint();
        if (hover != null) {
            drawMarker(mc, poseStack, cam, hover, AttachmentModule.hoverIsMount());
        } else if (mc.player != null) {
            // holding an attachment: show where it goes, red if this gun won't take it
            AttachmentModule.Guide guide = AttachmentModule.guide(mc.player, pose);
            if (guide != null) {
                drawMarker(mc, poseStack, cam, guide.point(), 0.03,
                        guide.fits() ? 40 : 255, guide.fits() ? 150 : 40, 40);
            }
        }
        if (TaczVRConfig.CLIENT.showAimLine.get()) {
            drawAimLine(mc, poseStack, cam, pose);
        }
    }

    /**
     * Small 3D cross where an attachment will go (green) or come off (yellow).
     */
    static void drawMarker(Minecraft mc, PoseStack poseStack, Vec3 cam, Vector3d at, boolean mount) {
        drawMarker(mc, poseStack, cam, at, 0.035, mount ? 60 : 255, mount ? 255 : 210, 60);
    }

    /**
     * @param s half the size of the cross
     */
    static void drawMarker(Minecraft mc, PoseStack poseStack, Vec3 cam, Vector3d at, double s, int r, int g, int b) {
        Vec3 c = new Vec3(at.x, at.y, at.z);
        drawLine(mc, poseStack, cam, c.add(-s, 0, 0), new Vec3(1, 0, 0), 2 * s, r, g, b);
        drawLine(mc, poseStack, cam, c.add(0, -s, 0), new Vec3(0, 1, 0), 2 * s, r, g, b);
        drawLine(mc, poseStack, cam, c.add(0, 0, -s), new Vec3(0, 0, 1), 2 * s, r, g, b);
    }

    /**
     * @param local   plays the local player's animations and muzzle flash
     * @param owner   whose gun it is, another player's shots and reloads are shown from {@link RemoteGunEffects}
     * @param handsOf draws this player's hands on the grip (and the handguard when two-handed), null for none
     */
    static void drawPose(Minecraft mc, GunPoseSolver.Pose pose, ItemStack stack, PoseStack poseStack, Vec3 cam,
                         float partialTick, boolean local, @Nullable Player owner, @Nullable AbstractClientPlayer handsOf) {
        BedrockGunModel model = pose.model;
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pose.grip.x, pose.grip.y, pose.grip.z));

        poseStack.pushPose();
        poseStack.translate(pose.origin.x - cam.x, pose.origin.y - cam.y, pose.origin.z - cam.z);
        poseStack.mulPose(pose.rotation);
        poseStack.scale(pose.scale, pose.scale, pose.scale);
        GunPoseSolver.applyAimFrameChain(poseStack, pose.viewPath);
        BedrockPart magazine = null;
        boolean magazineVisible = true;
        long savedFlashStamp = Long.MIN_VALUE;
        try {
            if (local) {
                // reload/shoot/bolt animations, same as TACZ's first person
                LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = pose.display.getAnimationStateMachine();
                if (stateMachine != null) {
                    stateMachine.processContextIfExist(context -> {
                        context.setPartialTicks(partialTick);
                        context.setCurrentGunItem(stack);
                    });
                    stateMachine.update();
                }
                MuzzleFlashRender.isSelf = true;
                ShellRender.isSelf = true;
                // TACZ caches the flash pose relative to the first camera that sees it, re-capture for every eye
                if (System.currentTimeMillis() - MuzzleFlashRenderAccessor.taczvr$getShootTimeStamp() <= MUZZLE_FLASH_MILLIS) {
                    MuzzleFlashRenderAccessor.taczvr$setMuzzleFlashStartMark(true);
                }
            } else if (owner != null) {
                // TACZ runs no animations for other players: the rest pose (hands on the gun) plus shots and reloads
                RemoteGunEffects.apply(owner, pose.display, model);
                long flash = RemoteGunEffects.muzzleFlashStart(owner);
                if (flash >= 0) {
                    // TACZ's flash only knows your own last shot, lend it theirs for this draw
                    savedFlashStamp = MuzzleFlashRenderAccessor.taczvr$getShootTimeStamp();
                    MuzzleFlashRenderAccessor.taczvr$setShootTimeStamp(flash);
                    MuzzleFlashRenderAccessor.taczvr$setMuzzleFlashStartMark(true);
                    MuzzleFlashRender.isSelf = true;
                }
            } else if (handsOf != null) {
                // the hands only sit on the gun in the idle pose
                RestPose.apply(pose.display, model);
            }
            RenderType renderType = pose.display.enablesTransparency()
                    ? RenderType.entityTranslucent(pose.display.getModelTexture())
                    : RenderType.entityCutout(pose.display.getModelTexture());
            // magazine taken out for a manual reload
            boolean magazineOut = local ? MagazineHandler.isMagazineOut() : owner != null && RemoteGunEffects.magazineOut(owner);
            magazine = magazineOut ? MagazineHandler.magazinePart(model) : null;
            if (magazine != null) {
                magazineVisible = magazine.visible;
                magazine.visible = false;
            }
            model.render(poseStack, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, renderType, light, OverlayTexture.NO_OVERLAY);
            if (handsOf != null) {
                // before cleaning up, so the hands follow the reload animation
                GunHandRenderer.render(poseStack, model, handsOf, true, pose.twoHanded || local && leftHandOnGun(handsOf), light,
                        pose.leftHanded);
            }
        } finally {
            if (magazine != null) {
                magazine.visible = magazineVisible;
            }
            if (savedFlashStamp != Long.MIN_VALUE) {
                MuzzleFlashRenderAccessor.taczvr$setShootTimeStamp(savedFlashStamp);
            }
            model.cleanAnimationTransform();
            MuzzleFlashRender.isSelf = false;
            ShellRender.isSelf = false;
            poseStack.popPose();
        }
    }

    /**
     * The gun in the off-hand when dual wielding: in the left controller, in its rest pose, with its own muzzle flash.
     */
    private static void renderOffhandGun(Minecraft mc, Player player, PoseStack poseStack, Vec3 cam, float partialTick, boolean local) {
        VRPose vrPose = VrClient.renderPose(player);
        if (vrPose == null) {
            return;
        }
        ItemStack stack = player.getOffhandItem();
        GunPoseSolver.Pose pose = OffhandGun.solve(player, vrPose, VrClient.remoteInterpolationOffset(player, partialTick),
                local ? VrClient.localWorldScale() : 1.0F);
        if (pose == null) {
            return;
        }
        offhandGunsDrawn++;
        BedrockGunModel model = pose.model;
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(pose.grip.x, pose.grip.y, pose.grip.z));
        long flash = local ? OffhandGun.firedMillis() : RemoteGunEffects.offhandFlashStart(player);
        boolean flashing = flash >= 0 && System.currentTimeMillis() - flash <= MUZZLE_FLASH_MILLIS;
        long savedStamp = MuzzleFlashRenderAccessor.taczvr$getShootTimeStamp();
        poseStack.pushPose();
        poseStack.translate(pose.origin.x - cam.x, pose.origin.y - cam.y, pose.origin.z - cam.z);
        poseStack.mulPose(pose.rotation);
        poseStack.scale(pose.scale, pose.scale, pose.scale);
        GunPoseSolver.applyAimFrameChain(poseStack, pose.viewPath);
        try {
            RestPose.apply(pose.display, model);
            if (flashing) {
                MuzzleFlashRenderAccessor.taczvr$setShootTimeStamp(flash);
                MuzzleFlashRenderAccessor.taczvr$setMuzzleFlashStartMark(true);
                MuzzleFlashRender.isSelf = true;
            }
            RenderType renderType = pose.display.enablesTransparency()
                    ? RenderType.entityTranslucent(pose.display.getModelTexture())
                    : RenderType.entityCutout(pose.display.getModelTexture());
            model.render(poseStack, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, renderType, light, OverlayTexture.NO_OVERLAY);
            if (player instanceof AbstractClientPlayer clientPlayer && drawsGunHands(player)) {
                GunHandRenderer.render(poseStack, model, clientPlayer, true, false, light, pose.leftHanded);
            }
        } finally {
            if (flashing) {
                MuzzleFlashRenderAccessor.taczvr$setShootTimeStamp(savedStamp);
            }
            model.cleanAnimationTransform();
            MuzzleFlashRender.isSelf = false;
            poseStack.popPose();
        }
        drawLaser(mc, poseStack, cam, pose, stack, player);
    }

    // for the self-test
    static int offhandGunsDrawn = 0;

    /**
     * A laser attachment's beam and dot, for everyone to see. TACZ only draws your own laser on a flat screen.
     */
    static void drawLaser(Minecraft mc, PoseStack poseStack, Vec3 cam, GunPoseSolver.Pose pose, ItemStack stack, Player player) {
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null || DefaultAssets.isEmptyAttachmentId(iGun.getAttachmentId(stack, AttachmentType.LASER))) {
            return;
        }
        lasersDrawn++;
        Vector3d mount = AttachmentModule.slotPosition(pose, AttachmentType.LASER);
        Vector3d start = mount != null ? mount : pose.muzzle;
        Vec3 from = new Vec3(start.x, start.y, start.z);
        Vec3 dir = new Vec3(pose.forward.x, pose.forward.y, pose.forward.z);
        Vec3 far = from.add(dir.scale(LASER_RANGE));
        HitResult hit = mc.level.clip(new ClipContext(from, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? far : hit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(mc.level, player, from, end,
                new AABB(from, end).inflate(1.0), entity -> entity.isPickable() && !entity.isSpectator() && entity != player);
        if (entityHit != null) {
            end = entityHit.getLocation();
        }
        int color = iGun.getLaserColor(stack);
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        if (r == 0 && g == 0 && b == 0) {
            r = 255;
        }
        drawLine(mc, poseStack, cam, from, dir, from.distanceTo(end), r, g, b);
        drawMarker(mc, poseStack, cam, new Vector3d(end.x, end.y, end.z), 0.03, r, g, b);
    }

    private static final double LASER_RANGE = 64.0;
    // for the self-test
    static int lasersDrawn = 0;

    private static void drawAimLine(Minecraft mc, PoseStack poseStack, Vec3 cam, GunPoseSolver.Pose pose) {
        Vec3 dir = pose.bulletDirection(TaczVRConfig.CLIENT.zeroDistance.get());
        drawLine(mc, poseStack, cam, new Vec3(pose.muzzle.x, pose.muzzle.y, pose.muzzle.z), dir, 64.0, 255, 40, 40);
    }

    static void drawLine(Minecraft mc, PoseStack poseStack, Vec3 cam, Vec3 start, Vec3 dir, double length,
                         int r, int g, int b) {
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack.Pose last = poseStack.last();
        float sx = (float) (start.x - cam.x);
        float sy = (float) (start.y - cam.y);
        float sz = (float) (start.z - cam.z);
        lines.vertex(last.pose(), sx, sy, sz).color(r, g, b, 255)
                .normal(last.normal(), (float) dir.x, (float) dir.y, (float) dir.z).endVertex();
        lines.vertex(last.pose(), sx + (float) (dir.x * length), sy + (float) (dir.y * length), sz + (float) (dir.z * length))
                .color(r, g, b, 255)
                .normal(last.normal(), (float) dir.x, (float) dir.y, (float) dir.z).endVertex();
        buffers.endBatch(RenderType.lines());
    }
}
