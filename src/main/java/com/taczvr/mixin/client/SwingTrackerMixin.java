package com.taczvr.mixin.client;

import com.taczvr.TaczVRConfig;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.gameplay.trackers.SwingTracker;

/**
 * Waving a gun around shouldn't break blocks or punch mobs through Vivecraft's roomscale swing.
 */
@Mixin(value = SwingTracker.class, remap = false)
public abstract class SwingTrackerMixin {

    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void taczvr$noSwingWithGun(LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (player != null && TaczVRConfig.CLIENT.disableSwingWithGun.get() && IGun.mainhandHoldGun(player)) {
            cir.setReturnValue(false);
        }
    }
}
