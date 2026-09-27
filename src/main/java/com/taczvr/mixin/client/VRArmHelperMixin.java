package com.taczvr.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.client.VrGunRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.render.helpers.VRArmHelper;

/**
 * Vivecraft draws your VR hands as a fist and forearm block at the controller, which ends up inside the gun.
 * While a gun is held that block is skipped and {@link VrGunRenderer} draws the hands on the gun's grip (and on the
 * handguard when the off-hand holds it) instead.
 */
@Mixin(value = VRArmHelper.class, remap = false)
public abstract class VRArmHelperMixin {

    @Inject(method = "renderVRHand_Main", at = @At("HEAD"), cancellable = true)
    private static void taczvr$handOnGun(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (VrGunRenderer.hidesFirstPersonHand(InteractionHand.MAIN_HAND)) {
            ci.cancel();
        }
    }

    // only the hand, the rest of the method draws the teleport arc which has to stay
    @Redirect(method = "renderVRHand_Offhand",
            at = @At(value = "INVOKE", remap = true,
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private static void taczvr$offHandOnGun(ItemInHandRenderer renderer, AbstractClientPlayer player, float partialTick,
                                            float pitch, InteractionHand hand, float swingProgress, ItemStack stack,
                                            float equippedProgress, PoseStack poseStack, MultiBufferSource buffer,
                                            int light) {
        if (!VrGunRenderer.hidesFirstPersonHand(InteractionHand.OFF_HAND)) {
            ((ItemInHandRendererInvoker) renderer).taczvr$renderArmWithItem(player, partialTick, pitch, hand,
                    swingProgress, stack, equippedProgress, poseStack, buffer, light);
        }
    }
}
