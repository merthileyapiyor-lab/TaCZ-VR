package com.taczvr.mixin.client;

import com.taczvr.client.ScopeView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client_vr.VRData;

/**
 * Moves the camera of Vivecraft's spyglass pass into the TACZ scope's eyepiece while looking through it.
 */
@Mixin(value = VRData.class, remap = false)
public abstract class VRDataMixin {

    @Inject(method = "getEye", at = @At("HEAD"), cancellable = true)
    private void taczvr$scopeEye(RenderPass pass, CallbackInfoReturnable<VRData.VRDevicePose> cir) {
        if (pass == RenderPass.SCOPER && ScopeView.isViewing()) {
            VRData.VRDevicePose eye = ScopeView.eyePose((VRData) (Object) this);
            if (eye != null) {
                cir.setReturnValue(eye);
            }
        }
    }
}
