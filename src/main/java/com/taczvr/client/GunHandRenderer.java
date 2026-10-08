package com.taczvr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.compat.Axis;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.GunModelConstant;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.List;

/**
 * Draws a player's arms on a TACZ gun, where TACZ puts them in first person: the right hand on the pistol grip and
 * the left hand on the handguard. Uses its own copy of the player arm geometry, the player's real model is posed by
 * Vivecraft and can't be borrowed.
 */
final class GunHandRenderer {
    private static Arms wide;
    private static Arms slim;

    private record Arms(ModelPart rightArm, ModelPart rightSleeve, ModelPart leftArm, ModelPart leftSleeve) {
    }

    private GunHandRenderer() {
    }

    /**
     * @param poseStack positioned in TACZ model space (after the aim frame chain), with the model's animation applied
     */
    /**
     * @param grip       the hand on the pistol grip
     * @param handguard  the other hand on the handguard
     * @param leftHanded the gun is in the left hand: the left arm holds the grip and the right arm the handguard
     */
    static void render(PoseStack poseStack, BedrockGunModel model, AbstractClientPlayer player, boolean grip,
                       boolean handguard, int light, boolean leftHanded) {
        Arms arms = arms("slim".equals(player.getModelName()));
        ResourceLocation skin = player.getSkinTextureLocation();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean rightSleeve = player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        boolean leftSleeve = player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
        if (leftHanded) {
            // each arm sits where the other one would
            place(arms.leftArm, -5.0F);
            place(arms.leftSleeve, -5.0F);
            place(arms.rightArm, 5.0F);
            place(arms.rightSleeve, 5.0F);
        }
        try {
            if (grip) {
                renderArm(poseStack, model, GunModelConstant.RIGHTHAND_POS_NODE, leftHanded ? arms.leftArm : arms.rightArm,
                        leftHanded ? arms.leftSleeve : arms.rightSleeve, leftHanded ? leftSleeve : rightSleeve, skin, buffers, light);
            }
            if (handguard) {
                renderArm(poseStack, model, GunModelConstant.LEFTHAND_POS_NODE, leftHanded ? arms.rightArm : arms.leftArm,
                        leftHanded ? arms.rightSleeve : arms.leftSleeve, leftHanded ? rightSleeve : leftSleeve, skin, buffers, light);
            }
        } finally {
            if (leftHanded) {
                place(arms.leftArm, 5.0F);
                place(arms.leftSleeve, 5.0F);
                place(arms.rightArm, -5.0F);
                place(arms.rightSleeve, -5.0F);
            }
        }
        if (leftHanded) {
            leftHandedDraws++;
        }
        buffers.endBatch();
    }

    // for the self-test
    static int leftHandedDraws = 0;

    private static void renderArm(PoseStack poseStack, BedrockGunModel model, String bone, ModelPart arm,
                                  ModelPart sleeve, boolean showSleeve, ResourceLocation skin,
                                  MultiBufferSource.BufferSource buffers, int light) {
        List<BedrockPart> path = GunPoseSolver.bonePath(model, bone);
        if (path == null) {
            return;
        }
        poseStack.pushPose();
        for (BedrockPart part : path) {
            part.translateAndRotateAndScale(poseStack);
        }
        // same as TACZ's LeftHandRender/RightHandRender before they draw the vanilla first person arm
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        arm.render(poseStack, buffers.getBuffer(RenderType.entitySolid(skin)), light, OverlayTexture.NO_OVERLAY);
        if (showSleeve) {
            sleeve.render(poseStack, buffers.getBuffer(RenderType.entityTranslucent(skin)), light, OverlayTexture.NO_OVERLAY);
        }
        poseStack.popPose();
    }

    private static Arms arms(boolean slimModel) {
        if (slimModel) {
            if (slim == null) {
                slim = bake(true);
            }
            return slim;
        }
        if (wide == null) {
            wide = bake(false);
        }
        return wide;
    }

    private static Arms bake(boolean slimModel) {
        ModelPart root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slimModel), 64, 64).bakeRoot();
        Arms arms = new Arms(root.getChild("right_arm"), root.getChild("right_sleeve"),
                root.getChild("left_arm"), root.getChild("left_sleeve"));
        // the pose vanilla's first person hand render ends up with (HumanoidModel#setupAnim puts both arms at y = 2)
        place(arms.rightArm, -5.0F);
        place(arms.rightSleeve, -5.0F);
        place(arms.leftArm, 5.0F);
        place(arms.leftSleeve, 5.0F);
        return arms;
    }

    private static void place(ModelPart part, float x) {
        part.setPos(x, 2.0F, 0.0F);
        part.setRotation(0.0F, 0.0F, 0.0F);
    }
}
