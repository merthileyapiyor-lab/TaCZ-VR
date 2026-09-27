package com.taczvr.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.client.LeftHanded;
import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Left-handed first person: TACZ's gun mirrored across the middle of the screen, so it sits on the left and the
 * sights stay in the center. Mirrored faces wind the other way round, they're drawn with the front face flipped.
 */
@Mixin(value = GunItemRendererWrapper.class, remap = false)
public abstract class GunItemRendererWrapperMixin {
    @Unique
    private static boolean taczvr$mirrored;

    @Inject(method = "renderFirstPerson", at = @At("HEAD"))
    private void taczvr$mirrorForLefties(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack,
                                         MultiBufferSource buffers, int light, float partialTick, CallbackInfo ci) {
        taczvr$mirrored = LeftHanded.mirrorsFirstPerson(player);
        if (!taczvr$mirrored) {
            return;
        }
        // whatever is queued is drawn the normal way first
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
        poseStack.pushPose();
        poseStack.scale(-1.0F, 1.0F, 1.0F);
        GL11.glFrontFace(GL11.GL_CW);
    }

    @Inject(method = "renderFirstPerson", at = @At("RETURN"))
    private void taczvr$endMirror(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack,
                                  MultiBufferSource buffers, int light, float partialTick, CallbackInfo ci) {
        if (!taczvr$mirrored) {
            return;
        }
        taczvr$mirrored = false;
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
        GL11.glFrontFace(GL11.GL_CCW);
        poseStack.popPose();
        LeftHanded.countMirrored();
    }
}
