package com.taczvr;

import com.taczvr.vr.VrBackends;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Side-independent access to the VR mod (Vivecraft).
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
    public static volatile VrPose testPose = null;
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
            return VrBackends.common().isVRPlayer(player);
        } catch (Throwable t) {
            logOnce(t);
            return false;
        }
    }

    @Nullable
    public static VrPose getPose(Player player) {
        if (testForceVr) {
            return testPose;
        }
        try {
            return VrBackends.common().getPose(player);
        } catch (Throwable t) {
            logOnce(t);
            return null;
        }
    }

    /**
     * How fast a VR player's hand moved over the last few ticks, in world space, blocks per tick.
     */
    @Nullable
    public static Vec3 handVelocity(Player player, VrHand hand) {
        if (testForceVr) {
            return testHandVelocity;
        }
        try {
            return VrBackends.common().handVelocity(player, hand);
        } catch (Throwable t) {
            logOnce(t);
            return null;
        }
    }

    @Nullable
    public static Hand getMainHand(Player player) {
        VrPose pose = getPose(player);
        if (pose == null) {
            return null;
        }
        VrPart hand = pose.getMainHand();
        return hand == null ? null : new Hand(hand.getPos(), hand.getDir());
    }

    @Nullable
    public static Vec3 getHeadPos(Player player) {
        VrPose pose = getPose(player);
        if (pose == null || pose.getHead() == null) {
            return null;
        }
        return pose.getHead().getPos();
    }

    public static void logOnce(Throwable t) {
        if (!loggedError) {
            loggedError = true;
            TaczVR.LOGGER.error("VR mod ({}) API call failed, VR gun handling may not work", VrBackends.common().name(), t);
        }
    }
}
