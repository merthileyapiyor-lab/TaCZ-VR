package com.taczvr.mixin.common;

import com.taczvr.server.ServerAimStore;
import com.tacz.guns.entity.shooter.LivingEntityShoot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Supplier;

/**
 * TACZ fires in the direction of the player's head rotation. For VR players use where the gun points instead.
 */
@Mixin(value = LivingEntityShoot.class, remap = false)
public abstract class LivingEntityShootMixin {
    @Shadow
    @Final
    private LivingEntity shooter;

    @ModifyVariable(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;J)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Supplier<Float> taczvr$vrPitch(Supplier<Float> pitch) {
        if (!(this.shooter instanceof ServerPlayer player)) {
            return pitch;
        }
        return () -> taczvr$aimOr(player, true, pitch);
    }

    @ModifyVariable(method = "shoot(Ljava/util/function/Supplier;Ljava/util/function/Supplier;J)Lcom/tacz/guns/api/entity/ShootResult;",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private Supplier<Float> taczvr$vrYaw(Supplier<Float> yaw) {
        if (!(this.shooter instanceof ServerPlayer player)) {
            return yaw;
        }
        return () -> taczvr$aimOr(player, false, yaw);
    }

    @Unique
    private static Float taczvr$aimOr(ServerPlayer player, boolean pitch, Supplier<Float> fallback) {
        ServerAimStore.Aim aim = ServerAimStore.getAim(player);
        if (aim == null) {
            return fallback.get();
        }
        return pitch ? ServerAimStore.pitchOf(aim.direction()) : ServerAimStore.yawOf(aim.direction());
    }
}
