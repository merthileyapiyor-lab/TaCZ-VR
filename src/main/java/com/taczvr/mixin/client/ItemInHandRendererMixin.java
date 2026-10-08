package com.taczvr.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.client.VrGunRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Both Vivecraft's VR hands and the player model's item layer draw held items through here.
 * Skip the gun when {@link VrGunRenderer} already draws it in the VR player's hand.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemTransforms$TransformType;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void taczvr$skipVrHeldGun(LivingEntity entity, ItemStack stack, ItemTransforms.TransformType displayContext,
                                      boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int light,
                                      CallbackInfo ci) {
        if (entity instanceof Player player && VrGunRenderer.rendersHeldGun(player, stack)) {
            ci.cancel();
        }
    }
}
