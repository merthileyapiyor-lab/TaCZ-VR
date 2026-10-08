package com.taczvr.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.client.LeftHanded;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.RenderHandEvent;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Left-handed first person: TACZ's gun mirrored across the middle of the screen, so it sits on the left and the
 * sights stay in the center. Mirrored faces wind the other way round, they're drawn with the front face flipped.
 * TACZ 1.1.4 draws its first person gun from this hand event.
 */
@Mixin(value = FirstPersonRenderGunEvent.class, remap = false)
public abstract class FirstPersonRenderGunEventMixin {
    @Unique
    private static boolean taczvr$mirrored;

    @Inject(method = "onRenderHand", at = @At("HEAD"))
    private static void taczvr$mirrorForLefties(RenderHandEvent event, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        taczvr$mirrored = player != null && event.getHand() == InteractionHand.MAIN_HAND
                && IGun.getIGunOrNull(event.getItemStack()) != null && LeftHanded.mirrorsFirstPerson(player);
        if (!taczvr$mirrored) {
            return;
        }
        // whatever is queued is drawn the normal way first
        if (event.getMultiBufferSource() instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.scale(-1.0F, 1.0F, 1.0F);
        GL11.glFrontFace(GL11.GL_CW);
    }

    @Inject(method = "onRenderHand", at = @At("RETURN"))
    private static void taczvr$endMirror(RenderHandEvent event, CallbackInfo ci) {
        if (!taczvr$mirrored) {
            return;
        }
        taczvr$mirrored = false;
        if (event.getMultiBufferSource() instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
        GL11.glFrontFace(GL11.GL_CCW);
        event.getPoseStack().popPose();
        LeftHanded.countMirrored();
    }
}
