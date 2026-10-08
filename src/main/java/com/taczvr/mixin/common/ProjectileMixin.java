package com.taczvr.mixin.common;

import com.taczvr.VrCommon;
import com.taczvr.server.ServerAssist;
import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Aim assist from the assist menu: the client already points the shot at the target, fire it without spread.
 * TACZ 1.1.4 passes the spread straight to the bullet's shootFromRotation.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {
    @ModifyVariable(method = "shootFromRotation", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private float taczvr$noSpreadWithAimAssist(float inaccuracy) {
        Projectile self = (Projectile) (Object) this;
        if (self instanceof EntityKineticBullet && self.getOwner() instanceof ServerPlayer player && (ServerAssist.aimAssist(player) || VrCommon.testNoSpread)) {
            return 0.0F;
        }
        return inaccuracy;
    }
}
