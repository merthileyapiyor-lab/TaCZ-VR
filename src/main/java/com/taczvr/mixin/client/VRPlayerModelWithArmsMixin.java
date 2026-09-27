package com.taczvr.mixin.client;

import com.taczvr.client.GunArmFitter;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionfc;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client.render.VRPlayerModel;
import org.vivecraft.client.render.VRPlayerModel_WithArms;

/**
 * Pulls back the forearm holding a gun on Vivecraft's player models with elbows, see {@link GunArmFitter}.
 */
@Mixin(value = VRPlayerModel_WithArms.class, remap = false)
public abstract class VRPlayerModelWithArmsMixin {

    @Inject(method = "positionConnectedLimb", at = @At("RETURN"))
    private void taczvr$fitConnectedForearm(LivingEntity player, ModelPart upper, ModelPart lower, Vector3fc lowerPos,
                                            Quaternionfc lowerRot, float lowerXOffset, Vector3fc jointPos,
                                            boolean jointDown, HumanoidArm arm, boolean useWorldScale, CallbackInfo ci) {
        GunArmFitter.fitForearm((VRPlayerModel<?>) (Object) this, player, lower, lowerPos, arm);
    }

    @Inject(method = "positionSplitLimb", at = @At("RETURN"))
    private void taczvr$fitSplitForearm(LivingEntity player, ModelPart upper, ModelPart lower, Vector3fc lowerPos,
                                        Quaternionfc lowerRot, float lowerXRot, float lowerXOffset, Vector3fc jointPos,
                                        boolean jointDown, HumanoidArm arm, boolean useWorldScale, CallbackInfo ci) {
        GunArmFitter.fitForearm((VRPlayerModel<?>) (Object) this, player, lower, lowerPos, arm);
    }
}
