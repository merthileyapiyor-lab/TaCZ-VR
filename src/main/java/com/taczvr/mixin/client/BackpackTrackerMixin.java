package com.taczvr.mixin.client;

import com.taczvr.client.interact.BackDrawModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.gameplay.trackers.BackpackTracker;

/**
 * Vivecraft's backpack switch presses a key the moment a hand reaches behind the head. For the gun hand, while that
 * reach draws a gun from the back ({@link BackDrawModule}), it would switch the hotbar slot first.
 */
@Mixin(value = BackpackTracker.class, remap = false)
public abstract class BackpackTrackerMixin {

    @Inject(method = "pressKeybind", at = @At("HEAD"), cancellable = true)
    private void taczvr$gunOnTheBack(int controller, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (controller == 0 && mc.player != null && BackDrawModule.canDraw(mc.player)) {
            ci.cancel();
        }
    }
}
