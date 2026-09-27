package com.taczvr.mixin.client;

import com.taczvr.TaczVRConfig;
import com.taczvr.client.VrClient;
import com.tacz.guns.client.input.AimKey;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * In hold-to-aim mode TACZ resets the aim every tick the aim key isn't held. In VR aiming comes from holding the
 * sights to the eye (VrGunController#updateAim), so that reset would cancel it straight away.
 */
@Mixin(value = AimKey.class, remap = false)
public abstract class AimKeyMixin {

    @Inject(method = "onAimHoldingPreInput", at = @At("HEAD"), cancellable = true)
    private static void taczvr$vrOwnsAim(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (TaczVRConfig.CLIENT.enabled.get() && TaczVRConfig.CLIENT.aimDownSights.get() && VrClient.isVRActive()) {
            ci.cancel();
        }
    }
}
