package com.taczvr.mixin.common;

import com.taczvr.VrCommon;
import com.taczvr.server.ServerAimStore;
import com.taczvr.server.ServerAssist;
import com.tacz.guns.entity.shooter.ShooterDataHolder;
import com.tacz.guns.item.ModernKineticGunItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ aims stock/bayonet hits with the head rotation. For VR players, turn them to where the gun points for the
 * duration of the hit, like Vivecraft's own aim fix does for vanilla attacks.
 */
@Mixin(value = ModernKineticGunItem.class, remap = false)
public abstract class ModernKineticGunItemMixin {
    @Unique
    private static ServerPlayer taczvr$aimedPlayer;
    @Unique
    private static float taczvr$savedXRot;
    @Unique
    private static float taczvr$savedYRot;

    @Inject(method = "melee(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"))
    private void taczvr$aimMelee(ShooterDataHolder dataHolder, LivingEntity user, ItemStack gunItem, CallbackInfo ci) {
        taczvr$aimedPlayer = null;
        if (!(user instanceof ServerPlayer player)) {
            return;
        }
        ServerAimStore.Aim aim = ServerAimStore.getAim(player);
        if (aim == null) {
            return;
        }
        taczvr$aimedPlayer = player;
        taczvr$savedXRot = player.getXRot();
        taczvr$savedYRot = player.getYRot();
        player.setXRot(ServerAimStore.pitchOf(aim.direction()));
        player.setYRot(ServerAimStore.yawOf(aim.direction()));
    }

    @Inject(method = "melee(Lcom/tacz/guns/entity/shooter/ShooterDataHolder;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("RETURN"))
    private void taczvr$restoreAim(ShooterDataHolder dataHolder, LivingEntity user, ItemStack gunItem, CallbackInfo ci) {
        if (taczvr$aimedPlayer == user) {
            taczvr$aimedPlayer.setXRot(taczvr$savedXRot);
            taczvr$aimedPlayer.setYRot(taczvr$savedYRot);
        }
        taczvr$aimedPlayer = null;
    }

    /**
     * Aim assist from the assist menu: the client already points the shot at the target, fire it without spread.
     */
    @Inject(method = "doBulletSpread", at = @At("HEAD"), cancellable = true)
    private void taczvr$noSpreadWithAimAssist(ShooterDataHolder dataHolder, ItemStack gunItem, LivingEntity shooter,
                                              Projectile projectile, int bulletCnt, float processedSpeed, float inaccuracy,
                                              float pitch, float yaw, CallbackInfo ci) {
        if (shooter instanceof ServerPlayer player && (ServerAssist.aimAssist(player) || VrCommon.testNoSpread)) {
            projectile.shootFromRotation(shooter, pitch, yaw, 0.0F, processedSpeed, 0.0F);
            ci.cancel();
        }
    }
}
