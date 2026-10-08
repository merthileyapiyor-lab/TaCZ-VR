package com.taczvr.client;

import com.taczvr.compat.Joml;
import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.compat.Axis;
import com.taczvr.TaczVR;
import com.taczvr.TaczVRConfig;
import com.tacz.guns.compat.oculus.OculusCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import com.taczvr.vr.VrPass;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

/**
 * LesRaisins Tactical Equipments draws its knives, grenades, medkits and shield in first person only with its own
 * animated hand rendering, which doesn't run in VR: in your own VR view they were invisible. Draw them at the
 * controller the way they sit in a hand in third person (as others see them). Nothing of LesRaisins is changed, its
 * items are only recognised by their "lrtactical" id.
 */
public final class LrTacticalVr {
    private static final String NAMESPACE = "lrtactical";
    private static boolean loggedError = false;
    static int drawn = 0;

    private LrTacticalVr() {
    }

    public static boolean isLrItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && NAMESPACE.equals(id.getNamespace());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !TaczVRConfig.CLIENT.enabled.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !VrClient.isVRActive() || OculusCompat.isRenderShadow()) {
            return;
        }
        // in third person views the player model holds it the normal way
        VrPass pass = VrClient.currentPass();
        if (pass != null && !pass.isFirstPerson()) {
            return;
        }
        VrPose pose = VrClient.renderPose(player);
        if (pose == null) {
            return;
        }
        try {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                VrPart part = pose.getHand(hand);
                if (part != null && isLrItem(stack)) {
                    draw(mc, player, hand, stack, part, event.getPoseStack(), event.getCamera().getPosition());
                }
            }
        } catch (Throwable t) {
            if (!loggedError) {
                loggedError = true;
                TaczVR.LOGGER.error("Failed to draw a LesRaisins item in VR", t);
            }
        }
    }

    private static void draw(Minecraft mc, LocalPlayer player, InteractionHand hand, ItemStack stack, VrPart part,
                             PoseStack poseStack, Vec3 cam) {
        boolean left = (hand == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.LEFT);
        Vec3 at = part.getPos();
        poseStack.pushPose();
        poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        Joml.mulPose(poseStack, part.getRotation());
        // controller space (-Z forward, +Y up) to the frame an item has in a third person hand (+Y forward, +Z up)
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.translate((left ? -1.0F : 1.0F) / 16.0F, 0.0F, 0.0F);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int light = LevelRenderer.getLightColor(mc.level, new BlockPos(at));
        mc.getItemRenderer().renderStatic(player, stack, left ? ItemTransforms.TransformType.THIRD_PERSON_LEFT_HAND : ItemTransforms.TransformType.THIRD_PERSON_RIGHT_HAND,
                left, poseStack, buffers, mc.level, light, OverlayTexture.NO_OVERLAY, player.getId() + hand.ordinal());
        buffers.endBatch();
        poseStack.popPose();
        drawn++;
    }
}
