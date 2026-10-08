package com.taczvr.vr.vivecraft;

import com.taczvr.compat.Joml;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionfc;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;

/**
 * Vivecraft's poses seen as ours.
 */
public final class VivecraftPoses {
    private VivecraftPoses() {
    }

    @Nullable
    public static VrPose wrap(@Nullable org.vivecraft.api.data.VRPose pose) {
        return pose == null ? null : new Pose(pose);
    }

    @Nullable
    public static VrPart wrap(@Nullable VRBodyPartData part) {
        return part == null ? null : new Part(part);
    }

    public static VRBodyPart part(VrHand hand) {
        return hand == VrHand.MAIN_HAND ? VRBodyPart.MAIN_HAND : VRBodyPart.OFF_HAND;
    }

    private record Pose(org.vivecraft.api.data.VRPose pose) implements VrPose {
        @Override
        public @Nullable VrPart getHead() {
            return wrap(this.pose.getHead());
        }

        @Override
        public @Nullable VrPart getMainHand() {
            return wrap(this.pose.getMainHand());
        }

        @Override
        public @Nullable VrPart getOffHand() {
            return wrap(this.pose.getOffHand());
        }

        @Override
        public boolean isLeftHanded() {
            return this.pose.isLeftHanded();
        }

        @Override
        public boolean isSeated() {
            return this.pose.isSeated();
        }
    }

    private record Part(VRBodyPartData part) implements VrPart {
        @Override
        public Vec3 getPos() {
            return this.part.getPos();
        }

        @Override
        public Vec3 getDir() {
            return this.part.getDir();
        }

        @Override
        public Quaternionfc getRotation() {
            // Vivecraft's 1.19.2 API hands out Minecraft's own quaternion
            return Joml.joml(this.part.getRotation());
        }
    }
}
