package com.taczvr.vr.visor;

import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3fc;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.player.VRBodyPartType;
import org.vmstudio.visor.api.common.player.VRPlayer;
import org.vmstudio.visor.api.common.player.VRPlayerPose;

/**
 * Visor's poses seen as ours. Visor's hand pose is the aim pose, pointing forward out of the controller like
 * Vivecraft's controller pose.
 */
public final class VisorPoses {
    private VisorPoses() {
    }

    @Nullable
    public static VrPose wrap(@Nullable VRPlayerPose pose, boolean leftHanded) {
        if (pose == null) {
            return null;
        }
        return VrPose.of(wrap(pose.getHmd()), wrap(pose.getMainHand()), wrap(pose.getOffhand()), leftHanded, false);
    }

    @Nullable
    public static VrPose wrap(@Nullable VRPlayer player, @Nullable VRPlayerPose pose) {
        return player == null ? null : wrap(pose, player.isLeftHanded());
    }

    /**
     * Null for a part Visor has no data for, it hands out an all-zero pose then.
     */
    @Nullable
    public static VrPart wrap(@Nullable org.vmstudio.visor.api.common.player.VRPose pose) {
        if (pose == null || pose == org.vmstudio.visor.api.common.player.VRPose.EMPTY) {
            return null;
        }
        Vector3fc dir = pose.getDirection();
        if (dir.lengthSquared() < 1.0E-8F) {
            return null;
        }
        Vector3fc pos = pose.getPosition();
        Quaternionf rotation = pose.getRotation().getNormalizedRotation(new Quaternionf());
        return VrPart.of(new Vec3(pos.x(), pos.y(), pos.z()), new Vec3(dir.x(), dir.y(), dir.z()).normalize(), rotation);
    }

    public static HandType hand(VrHand hand) {
        return hand == VrHand.MAIN_HAND ? HandType.MAIN : HandType.OFFHAND;
    }

    public static VRBodyPartType part(VrHand hand) {
        return hand == VrHand.MAIN_HAND ? VRBodyPartType.MAIN_HAND : VRBodyPartType.OFFHAND;
    }
}
