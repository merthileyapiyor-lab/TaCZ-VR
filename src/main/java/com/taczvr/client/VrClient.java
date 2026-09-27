package com.taczvr.client;

import com.taczvr.VrCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.client.VRRenderingAPI;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRPose;

/**
 * Client side access to Vivecraft. Everything is wrapped so a Vivecraft API change degrades instead of crashing.
 */
public final class VrClient {
    /**
     * Development self-test only: a fake world pose for the local player, which then counts as being in VR.
     */
    @Nullable
    static volatile VRPose testPose = null;
    /**
     * Development self-test only: the fake room pose that goes with {@link #testPose}.
     */
    @Nullable
    static volatile VRPose testRoomPose = null;
    static volatile int testHaptics = 0;

    private VrClient() {
    }

    public static boolean isVRActive() {
        if (testPose != null) {
            return true;
        }
        try {
            return VRClientAPI.instance().isVRActive();
        } catch (Throwable t) {
            VrCommon.logOnce(t);
            return false;
        }
    }

    /**
     * Pose of the local player sampled before the tick, this is what the server receives for this tick.
     */
    @Nullable
    public static VRPose localTickPose() {
        if (testPose != null) {
            return testPose;
        }
        try {
            return VRClientAPI.instance().getPreTickWorldPose();
        } catch (Throwable t) {
            VrCommon.logOnce(t);
            return null;
        }
    }

    /**
     * Local player's pose in the physical room, unaffected by walking or turning in the game.
     */
    @Nullable
    public static VRPose latestRoomPose() {
        if (testPose != null) {
            return testRoomPose;
        }
        try {
            return VRClientAPI.instance().getLatestRoomPose();
        } catch (Throwable t) {
            VrCommon.logOnce(t);
            return null;
        }
    }

    /**
     * Pose of any player interpolated for the frame being rendered.
     */
    @Nullable
    public static VRPose renderPose(Player player) {
        try {
            if (player == Minecraft.getInstance().player) {
                return testPose != null ? testPose : VRClientAPI.instance().getWorldRenderPose();
            }
            return VRRenderingAPI.instance().getWorldRenderPose(player);
        } catch (Throwable t) {
            // remote players can briefly be flagged as VR before their first pose arrives
            return null;
        }
    }

    /**
     * Vivecraft builds remote poses from the un-interpolated entity position, this is the offset to fix that.
     */
    public static Vec3 remoteInterpolationOffset(Player player, float partialTick) {
        if (player == Minecraft.getInstance().player) {
            return Vec3.ZERO;
        }
        return player.getPosition(partialTick).subtract(player.position());
    }

    public static float localWorldScale() {
        if (testPose != null) {
            return 1.0F;
        }
        try {
            return VRClientAPI.instance().getWorldScale();
        } catch (Throwable t) {
            return 1.0F;
        }
    }

    public static void haptic(VRBodyPart part, float seconds, float amplitude) {
        if (testPose != null) {
            testHaptics++;
            return;
        }
        try {
            VRClientAPI.instance().triggerHapticPulse(part, seconds, 160.0F, amplitude, 0.0F);
        } catch (Throwable t) {
            VrCommon.logOnce(t);
        }
    }

    @Nullable
    public static RenderPass currentPass() {
        if (testPose != null) {
            return null;
        }
        try {
            return VRRenderingAPI.instance().getCurrentRenderPass();
        } catch (Throwable t) {
            return null;
        }
    }
}
