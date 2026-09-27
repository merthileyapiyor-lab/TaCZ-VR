package com.taczvr.mixin.client;

import com.taczvr.TaczVRConfig;
import com.taczvr.client.VrClient;
import com.tacz.guns.client.event.CameraSetupEvent;
import net.minecraftforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ rotates the camera for recoil, reload shake and scope zoom. In VR the head is the camera, so turning it
 * makes people sick. The recoil is shown on the gun model instead (see VrGunController#kickRadians).
 */
@Mixin(value = CameraSetupEvent.class, remap = false)
public abstract class CameraSetupEventMixin {

    @Inject(method = "applyCameraRecoil", at = @At("HEAD"), cancellable = true)
    private static void taczvr$noCameraRecoil(ViewportEvent.ComputeCameraAngles event, CallbackInfo ci) {
        if (TaczVRConfig.CLIENT.disableCameraRecoil.get() && VrClient.isVRActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "applyLevelCameraAnimation", at = @At("HEAD"), cancellable = true)
    private static void taczvr$noCameraShake(ViewportEvent.ComputeCameraAngles event, CallbackInfo ci) {
        if (TaczVRConfig.CLIENT.disableCameraRecoil.get() && VrClient.isVRActive()) {
            ci.cancel();
        }
    }

    @Inject(method = "applyScopeMagnification", at = @At("HEAD"), cancellable = true)
    private static void taczvr$noScopeZoom(ViewportEvent.ComputeFov event, CallbackInfo ci) {
        if (VrClient.isVRActive()) {
            ci.cancel();
        }
    }
}
