package com.taczvr.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Quaternion;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Minecraft 1.19.2 has its own math classes, JOML only came with 1.19.3. The mod does its math in JOML (Vivecraft's
 * API uses it too), this converts where it meets Minecraft's PoseStack.
 */
public final class Joml {
    // both sides store matrices column by column. JOML writes through the buffer's native address, so it has to be
    // a direct buffer in native byte order
    private static final ThreadLocal<FloatBuffer> BUFFER = ThreadLocal.withInitial(
            () -> ByteBuffer.allocateDirect(16 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer());

    private Joml() {
    }

    public static Quaternion mc(Quaternionfc q) {
        return new Quaternion(q.x(), q.y(), q.z(), q.w());
    }

    public static Quaternionf joml(Quaternion q) {
        return new Quaternionf(q.i(), q.j(), q.k(), q.r());
    }

    public static com.mojang.math.Matrix4f mc(Matrix4fc m) {
        FloatBuffer buffer = BUFFER.get();
        m.get(buffer);
        com.mojang.math.Matrix4f result = new com.mojang.math.Matrix4f();
        result.load(buffer);
        return result;
    }

    public static Matrix4f joml(com.mojang.math.Matrix4f m) {
        FloatBuffer buffer = BUFFER.get();
        m.store(buffer);
        return new Matrix4f(buffer);
    }

    public static com.mojang.math.Vector3f mc(org.joml.Vector3fc v) {
        return new com.mojang.math.Vector3f(v.x(), v.y(), v.z());
    }

    public static Vector3f joml(com.mojang.math.Vector3f v) {
        return new Vector3f(v.x(), v.y(), v.z());
    }

    public static void mulPose(PoseStack poseStack, Quaternionfc q) {
        poseStack.mulPose(mc(q));
    }

    public static void mulPoseMatrix(PoseStack poseStack, Matrix4fc m) {
        poseStack.mulPoseMatrix(mc(m));
    }

    /**
     * A copy of the current pose matrix; changing it doesn't change the stack.
     */
    public static Matrix4f pose(PoseStack poseStack) {
        return joml(poseStack.last().pose());
    }

    public static void setPose(PoseStack poseStack, Matrix4fc m) {
        copyInto(m, poseStack.last().pose());
    }

    public static void copyInto(Matrix4fc from, com.mojang.math.Matrix4f to) {
        FloatBuffer buffer = BUFFER.get();
        from.get(buffer);
        to.load(buffer);
    }

    public static Vector3f translation(PoseStack poseStack) {
        return pose(poseStack).getTranslation(new Vector3f());
    }
}
