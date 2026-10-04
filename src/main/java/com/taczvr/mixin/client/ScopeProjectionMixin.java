package com.taczvr.mixin.client;

import com.taczvr.client.ScopeCamera;
import com.taczvr.client.ScopeView;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While {@link ScopeCamera} renders the scope picture: the scope's narrow field of view, on a square picture.
 */
@Mixin(GameRenderer.class)
public abstract class ScopeProjectionMixin {
    @Inject(method = "getProjectionMatrix(D)Lorg/joml/Matrix4f;", at = @At("RETURN"))
    private void taczvr$scopeCameraProjection(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (!ScopeCamera.isRendering()) {
            return;
        }
        // a perspective matrix has m11 = 1 / tan(fov / 2) and m00 = m11 / aspect; keep the clip planes
        Matrix4f projection = cir.getReturnValue();
        float m11 = (float) (1.0 / Math.tan(Math.toRadians(ScopeView.fovDegrees()) / 2.0));
        projection.m11(m11);
        projection.m00(m11);
    }
}
