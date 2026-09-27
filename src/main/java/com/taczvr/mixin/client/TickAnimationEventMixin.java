package com.taczvr.mixin.client;

import com.taczvr.TaczVRConfig;
import com.taczvr.client.VrClient;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.animation.statemachine.GunAnimationConstant;
import com.tacz.guns.client.event.TickAnimationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ bobs and swings the gun while walking or sprinting to fake a flat screen weapon sway. In VR the gun is in
 * your hand, so that sway makes it wobble around the hand. Keep the movement track idle; reload, shoot and inspect
 * animations still play.
 */
@Mixin(value = TickAnimationEvent.class, remap = false)
public abstract class TickAnimationEventMixin {

    @Inject(method = "tickAnimation(Lnet/minecraftforge/event/TickEvent$ClientTickEvent;)V", at = @At("HEAD"), cancellable = true)
    private static void taczvr$noMovementSway(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (!TaczVRConfig.CLIENT.enabled.get() || !VrClient.isVRActive()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            TimelessAPI.getGunDisplay(player.getMainHandItem()).ifPresent(display -> {
                var stateMachine = display.getAnimationStateMachine();
                if (stateMachine != null) {
                    stateMachine.trigger(GunAnimationConstant.INPUT_IDLE);
                }
            });
        }
        ci.cancel();
    }
}
