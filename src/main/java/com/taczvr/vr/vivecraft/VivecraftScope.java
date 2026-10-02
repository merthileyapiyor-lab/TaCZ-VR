package com.taczvr.vr.vivecraft;

import com.taczvr.client.GunPoseSolver;
import com.taczvr.client.ScopeView;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.vivecraft.client_vr.VRData;

/**
 * Points Vivecraft's spyglass render pass down the TACZ scope you look through, see {@link ScopeView}.
 */
public final class VivecraftScope {
    @Nullable
    private static VRData cachedData = null;
    @Nullable
    private static VRData.VRDevicePose cachedEye = null;

    private VivecraftScope() {
    }

    /**
     * The eye pose Vivecraft renders the spyglass pass from: the scope's eyepiece, looking down the scope.
     * Called by Vivecraft for its render data, cached per data instance (one per frame).
     */
    @Nullable
    public static VRData.VRDevicePose eyePose(VRData data) {
        if (data == cachedData) {
            return cachedEye;
        }
        cachedData = data;
        cachedEye = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        ItemStack stack = mc.player.getMainHandItem();
        GunPoseSolver.Pose gun = GunPoseSolver.solve(mc.player, stack, VivecraftPoses.wrap(data.asVRPose()), Vec3.ZERO,
                data.worldScale, 0.0F, 0.0F);
        if (gun == null) {
            return null;
        }
        Vector3d lens = gun.toWorld(new Vector3f(0.0F, 0.0F, -ScopeView.lensDistance()));
        // world -> room space, the inverse of VRDevicePose#getPosition / #getMatrix
        Vector3f roomPos = new Vector3f(
                (float) (lens.x - data.origin.x),
                (float) (lens.y - data.origin.y),
                (float) (lens.z - data.origin.z))
                .rotateY(-data.rotation_radians)
                .div(data.worldScale);
        Matrix4f roomRot = new Matrix4f().rotationY(-data.rotation_radians).rotate(gun.rotation);
        Vector3f roomDir = roomRot.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F));
        cachedEye = data.new VRDevicePose(data, roomRot, roomPos, roomDir);
        return cachedEye;
    }
}
