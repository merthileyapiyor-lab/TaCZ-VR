package com.taczvr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
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
import net.minecraft.world.item.ItemDisplayContext;
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
    // Vivecraft's VR hand is a player arm drawn at this size
    private static final float HAND_SCALE = 0.4F;
    // a bit bigger than that hand, as Vivecraft's own third person items in VR
    private static final float ITEM_SCALE = 0.525F;
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
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !TaczVRConfig.CLIENT.enabled.get()) {
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
        float worldScale = VrClient.localWorldScale();
        poseStack.pushPose();
        poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
        poseStack.mulPose(new org.joml.Quaternionf(part.getRotation()));
        poseStack.scale(worldScale, worldScale, worldScale);
        // Vivecraft's VR hand is a player arm at 0.4 size ending at the controller: the middle of its fist is 2 px
        // of that arm behind it
        poseStack.translate(0.0F, 0.0F, 2.0F / 16.0F * HAND_SCALE);
        // controller space (-Z forward, +Y up) to the frame an item has in a third person hand (+Y forward, +Z up)
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        // that frame starts at the front of the fist's end, 2 px forward and 2 px down from the fist's middle,
        // which is where the item's handle sits
        poseStack.translate(0.0F, 2.0F / 16.0F, -2.0F / 16.0F);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(at));
        mc.getItemRenderer().renderStatic(player, stack, left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                left, poseStack, buffers, mc.level, light, OverlayTexture.NO_OVERLAY, player.getId() + hand.ordinal());
        buffers.endBatch();
        poseStack.popPose();
        drawn++;
    }
}
