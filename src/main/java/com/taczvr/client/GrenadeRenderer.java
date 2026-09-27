package com.taczvr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.taczvr.content.GrenadeEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A thrown grenade as its 3D model: tumbling end over end while it flies, lying on its side once it stops.
 */
public final class GrenadeRenderer extends EntityRenderer<GrenadeEntity> {
    private static final float SCALE = 0.5F;
    private final ItemRenderer items;
    static int drawn = 0;

    public GrenadeRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(GrenadeEntity grenade, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.pushPose();
        Vec3 motion = grenade.getDeltaMovement();
        // each one lies turned its own way
        float spin = (grenade.getId() * 47) % 360;
        // at rest it still gets a little gravity every tick
        boolean flying = motion.horizontalDistanceSqr() > 1.0E-4 || motion.y > 0.05 || motion.y < -0.08;
        if (flying) {
            poseStack.translate(0.0F, 0.12F, 0.0F);
            Vector3f axis = new Vector3f((float) motion.z, 0.0F, (float) -motion.x);
            if (axis.lengthSquared() < 1.0E-6F) {
                axis.set(1.0F, 0.0F, 0.0F);
            }
            axis.normalize();
            poseStack.mulPose(new Quaternionf().rotateAxis((grenade.tickCount + partialTick) * 0.45F, axis));
        } else {
            poseStack.translate(0.0F, 0.08F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(spin));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }
        poseStack.scale(SCALE, SCALE, SCALE);
        this.items.renderStatic(grenade.getItem(), ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, poseStack, buffers,
                grenade.level(), grenade.getId());
        poseStack.popPose();
        drawn++;
        super.render(grenade, yaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(GrenadeEntity grenade) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
