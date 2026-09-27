package com.taczvr.mixin.client;

import com.taczvr.TaczVRConfig;
import com.taczvr.client.OffhandGun;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vivecraft.client_vr.gameplay.trackers.TeleportTracker;

/**
 * With a gun in each hand the left trigger fires the off-hand gun, so it doesn't teleport meanwhile.
 */
@Mixin(value = TeleportTracker.class, remap = false)
public abstract class TeleportTrackerMixin {

    @Inject(method = "isActive", at = @At("RETURN"), cancellable = true)
    private void taczvr$noTeleportWhileDualWielding(LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && player != null && TaczVRConfig.COMMON.dualWield.get() && OffhandGun.holds(player)) {
            cir.setReturnValue(false);
        }
    }
}
