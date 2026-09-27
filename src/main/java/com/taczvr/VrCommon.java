package com.taczvr;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;
import org.vivecraft.api.data.VRPoseHistory;

/**
 * Side-independent access to Vivecraft's public API.
 */
public final class VrCommon {
    private static boolean loggedError = false;
    /**
     * Development self-test only: treat every player as a VR player without a tracked pose.
     */
    public static volatile boolean testForceVr = false;
    /**
     * Development self-test only: the tracked pose every player has while {@link #testForceVr} is on.
     */
    @Nullable
    public static volatile VRPose testPose = null;
    /**
     * Development self-test only: hand speed while {@link #testForceVr} is on, blocks per tick.
     */
    @Nullable
    public static volatile Vec3 testHandVelocity = null;

    public record Hand(Vec3 pos, Vec3 dir) {
    }

    private VrCommon() {
    }

    public static boolean isVRPlayer(Player player) {
        if (testForceVr) {
            return true;
        }
        try {
            return VRAPI.instance().isVRPlayer(player);
        } catch (Throwable t) {
            logOnce(t);
            return false;
        }
    }

    @Nullable
    public static VRPose getPose(Player player) {
        if (testForceVr) {
            return testPose;
        }
        try {
            return VRAPI.instance().getVRPose(player);
        } catch (Throwable t) {
            logOnce(t);
            return null;
        }
    }

    /**
     * How fast a VR player's hand moved over the last few ticks, in world space, blocks per tick.
     */
    @Nullable
    public static Vec3 handVelocity(Player player, VRBodyPart part) {
        if (testForceVr) {
            return testHandVelocity;
        }
        try {
            VRPoseHistory history = VRAPI.instance().getHistoricalVRPoses(player);
            return history == null || history.ticksOfHistory() < 2 ? null
                    : history.averageVelocity(part, Math.min(3, history.ticksOfHistory() - 1));
        } catch (Throwable t) {
            logOnce(t);
            return null;
        }
    }

    @Nullable
    public static Hand getMainHand(Player player) {
        VRPose pose = getPose(player);
        if (pose == null) {
            return null;
        }
        VRBodyPartData hand = pose.getMainHand();
        return hand == null ? null : new Hand(hand.getPos(), hand.getDir());
    }

    @Nullable
    public static Vec3 getHeadPos(Player player) {
        VRPose pose = getPose(player);
        if (pose == null || pose.getHead() == null) {
            return null;
        }
        return pose.getHead().getPos();
    }

    public static void logOnce(Throwable t) {
        if (!loggedError) {
            loggedError = true;
            TaczVR.LOGGER.error("Vivecraft API call failed, VR gun handling may not work", t);
        }
    }
}
