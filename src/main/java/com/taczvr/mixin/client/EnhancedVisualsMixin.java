package com.taczvr.mixin.client;

import com.taczvr.client.VrClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EnhancedVisuals paints its effects (blood, blur, faded colors) over the whole screen after the HUD. In VR the screen
 * it paints is Vivecraft's HUD panel at the hand: its blur and color shaders turn the see-through panel black, with the
 * blood on it. So its picture stays off while in VR, its sounds keep going. Only applied when EnhancedVisuals is there.
 */
@Pseudo
@Mixin(targets = "team.creative.enhancedvisuals.client.EVClient", remap = false)
public abstract class EnhancedVisualsMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, remap = false)
    private static void taczvr$notInVr(CallbackInfoReturnable<Boolean> cir) {
        if (!VrClient.testEnhancedVisualsInVr && VrClient.isVRActive()) {
            cir.setReturnValue(false);
        }
    }
}
