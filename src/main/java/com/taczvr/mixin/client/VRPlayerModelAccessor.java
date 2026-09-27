package com.taczvr.mixin.client;

import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.client.render.VRPlayerModel;

/**
 * What Vivecraft's player model was posed with, set during its setupAnim.
 */
@Mixin(value = VRPlayerModel.class, remap = false)
public interface VRPlayerModelAccessor {
    @Accessor("rotInfo")
    ClientVRPlayers.RotInfo taczvr$getRotInfo();

    @Accessor("bodyYaw")
    float taczvr$getBodyYaw();

    @Accessor("isMainPlayer")
    boolean taczvr$isMainPlayer();

    @Accessor("mainArm")
    HumanoidArm taczvr$getMainArm();
}
