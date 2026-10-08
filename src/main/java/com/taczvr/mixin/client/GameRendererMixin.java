package com.taczvr.mixin.client;

import com.taczvr.client.ScopeView;
import com.taczvr.vr.vivecraft.VivecraftClientBackend;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.math.Matrix4f;
import com.taczvr.compat.Joml;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.api.client.data.RenderPass;

/**
 * Vivecraft renders its spyglass pass with a fixed 8x zoom. Use the TACZ scope's zoom instead.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getProjectionMatrix(D)Lcom/mojang/math/Matrix4f;", at = @At("RETURN"))
    private void taczvr$scopeZoom(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (!ScopeView.isViewing() || VivecraftClientBackend.rawPass() != RenderPass.SCOPER) {
            return;
        }
        Matrix4f returned = cir.getReturnValue();
        org.joml.Matrix4f projection = Joml.joml(returned);
        // a perspective matrix has m11 = 1 / tan(fov / 2) and m00 = m11 / aspect; keep the aspect and clip planes
        float aspect = projection.m11() / projection.m00();
        float m11 = (float) (1.0 / Math.tan(Math.toRadians(ScopeView.fovDegrees()) / 2.0));
        projection.m11(m11);
        projection.m00(m11 / aspect);
        Joml.copyInto(projection, returned);
    }
}
