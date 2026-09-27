package com.taczvr.mixin.client;

import com.taczvr.client.GunArmFitter;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.render.VRPlayerModel;
import org.vivecraft.client.render.VRPlayerModel_WithArms;

/**
 * Shortens the arm holding a gun on Vivecraft's player model without elbows, see {@link GunArmFitter}.
 */
@Mixin(value = VRPlayerModel.class, remap = false)
public abstract class VRPlayerModelMixin {

    @Inject(method = "animateVRModel", at = @At("RETURN"))
    private static void taczvr$fitArmsToGun(PlayerModel<LivingEntity> model, LivingEntity player, float limbSwing,
                                            float limbSwingAmount, Vector3f tempV, Vector3f tempV2, Matrix3f tempM,
                                            CallbackInfo ci) {
        // models with elbows move their forearm instead
        if (model instanceof VRPlayerModel<?> vrModel && !(model instanceof VRPlayerModel_WithArms<?>)) {
            GunArmFitter.fitWholeArms(vrModel, player);
        }
    }
}
