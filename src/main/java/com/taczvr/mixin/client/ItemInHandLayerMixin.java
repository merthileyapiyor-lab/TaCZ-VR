package com.taczvr.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.taczvr.client.LeftHanded;
import com.taczvr.client.VrGunRenderer;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ skips drawing the gun of a left-handed player (or mob), so they look empty handed to everyone. Draw it in
 * their left hand, the way the right hand one would be.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin<T extends LivingEntity, M extends EntityModel<T> & ArmedModel> extends RenderLayer<T, M> {
    @Shadow
    @Final
    private ItemInHandRenderer itemInHandRenderer;

    private ItemInHandLayerMixin(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("TAIL"))
    private void taczvr$leftHandedGun(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing,
                                      float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw,
                                      float headPitch, CallbackInfo ci) {
        ItemStack gun = entity.getMainHandItem();
        if (entity.getMainArm() != HumanoidArm.LEFT || IGun.getIGunOrNull(gun) == null || !LeftHanded.enabled()) {
            return;
        }
        // VR players' guns are drawn in their controller
        if (entity instanceof Player player && VrGunRenderer.rendersHeldGun(player, gun)) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().translateToHand(HumanoidArm.LEFT, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(-1.0F / 16.0F, 0.125F, -0.625F);
        this.itemInHandRenderer.renderItem(entity, gun, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, poseStack, buffers, light);
        poseStack.popPose();
        LeftHanded.countThirdPerson();
    }
}
