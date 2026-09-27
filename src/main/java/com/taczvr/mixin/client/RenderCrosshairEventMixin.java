package com.taczvr.mixin.client;

import com.taczvr.TaczVRConfig;
import com.taczvr.client.VrClient;
import com.tacz.guns.client.event.RenderCrosshairEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ draws its crosshair in the middle of the HUD. In VR the HUD floats somewhere in front of you, so that
 * crosshair points nowhere; you aim with the gun's sights instead.
 */
@Mixin(value = RenderCrosshairEvent.class, remap = false)
public abstract class RenderCrosshairEventMixin {

    @Inject(method = "onRenderOverlay", at = @At("HEAD"), cancellable = true)
    private static void taczvr$noHudCrosshair(RenderGuiOverlayEvent.Pre event, CallbackInfo ci) {
        if (TaczVRConfig.CLIENT.hideTaczCrosshair.get() && VrClient.isVRActive()) {
            ci.cancel();
        }
    }
}
