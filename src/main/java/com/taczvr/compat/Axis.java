package com.taczvr.compat;

import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;

/**
 * Minecraft 1.19.3's {@code com.mojang.math.Axis}, for 1.19.2: rotations about the X, Y and Z axes for PoseStack.mulPose.
 */
@FunctionalInterface
public interface Axis {
    Axis XN = radians -> Vector3f.XN.rotation(radians);
    Axis XP = radians -> Vector3f.XP.rotation(radians);
    Axis YN = radians -> Vector3f.YN.rotation(radians);
    Axis YP = radians -> Vector3f.YP.rotation(radians);
    Axis ZN = radians -> Vector3f.ZN.rotation(radians);
    Axis ZP = radians -> Vector3f.ZP.rotation(radians);

    Quaternion rotation(float radians);

    default Quaternion rotationDegrees(float degrees) {
        return this.rotation((float) Math.toRadians(degrees));
    }
}
