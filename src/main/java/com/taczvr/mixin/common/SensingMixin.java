package com.taczvr.mixin.common;

import com.taczvr.server.Tactical;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mobs can't see through smoke, and see nothing while a flashbang has them stunned.
 */
@Mixin(Sensing.class)
public abstract class SensingMixin {
    @Shadow
    @Final
    private Mob mob;

    @Inject(method = "hasLineOfSight", at = @At("HEAD"), cancellable = true)
    private void taczvr$smokeAndFlash(Entity target, CallbackInfoReturnable<Boolean> cir) {
        // and nobody goes after a player down on the ground in the zombie waves
        if (Tactical.blocksSight(this.mob, target) || com.taczvr.server.GameManager.isDowned(target)) {
            cir.setReturnValue(false);
        }
    }
}
