package com.taczvr.client;

import com.taczvr.VrCommon;
import com.taczvr.vr.VrBackends;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPass;
import com.taczvr.vr.VrPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Client side access to the VR mod (Vivecraft or Visor). Everything is wrapped so a VR mod API change degrades
 * instead of crashing.
 */
public final class VrClient {
    /**
     * Development self-test only: a fake world pose for the local player, which then counts as being in VR.
     */
    @Nullable
    static volatile VrPose testPose = null;
    /**
     * Development self-test only: the fake room pose that goes with {@link #testPose}.
     */
    @Nullable
    static volatile VrPose testRoomPose = null;
    static volatile int testHaptics = 0;

    private VrClient() {
    }

    public static boolean isVRActive() {
        if (testPose != null) {
            return true;
        }
        try {
            return VrBackends.client().isVRActive();
        } catch (Throwable t) {
            VrCommon.logOnce(t);
            return false;
        }
    }

    /**
     * Pose of the local player sampled before the tick, this is what the server receives for this tick.
     */
    @Nullable
    public static VrPose localTickPose() {
        if (testPose != null) {
            return testPose;
        }
        try {
            return VrBackends.client().localTickPose();
        } catch (Throwable t) {
            VrCommon.logOnce(t);
            return null;
        }
    }

    /**
     * Local player's pose in the physical room, unaffected by walking or turning in the game.
     */
    @Nullable
    public static VrPose latestRoomPose() {
        if (testPose != null) {
            return testRoomPose;
        }
        try {
            return VrBackends.client().latestRoomPose();
        } catch (Throwable t) {
            VrCommon.logOnce(t);
            return null;
        }
    }

    /**
     * Pose of any player interpolated for the frame being rendered.
     */
    @Nullable
    public static VrPose renderPose(Player player) {
        if (testPose != null && player == Minecraft.getInstance().player) {
            return testPose;
        }
        try {
            return VrBackends.client().renderPose(player);
        } catch (Throwable t) {
            // remote players can briefly be flagged as VR before their first pose arrives
            return null;
        }
    }

    /**
     * What to add to a remote player's render pose so it lines up with the interpolated player model.
     */
    public static Vec3 remoteInterpolationOffset(Player player, float partialTick) {
        try {
            return VrBackends.client().remoteInterpolationOffset(player, partialTick);
        } catch (Throwable t) {
            return Vec3.ZERO;
        }
    }

    public static float localWorldScale() {
        if (testPose != null) {
            return 1.0F;
        }
        try {
            return VrBackends.client().localWorldScale();
        } catch (Throwable t) {
            return 1.0F;
        }
    }

    public static void haptic(VrHand hand, float seconds, float amplitude) {
        if (testPose != null) {
            testHaptics++;
            return;
        }
        try {
            VrBackends.client().haptic(hand, seconds, amplitude);
        } catch (Throwable t) {
            VrCommon.logOnce(t);
        }
    }

    @Nullable
    public static VrPass currentPass() {
        if (ScopeCamera.isRendering()) {
            return VrPass.SCOPE;
        }
        if (testPose != null) {
            return null;
        }
        try {
            return VrBackends.client().currentPass();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The off-hand trigger, which fires a gun held in the off-hand.
     */
    public static boolean offHandTriggerDown() {
        try {
            return VrBackends.client().offHandTriggerDown();
        } catch (Throwable t) {
            VrCommon.logOnce(t);
            return false;
        }
    }

    /**
     * Whether the VR mod drew this player model, with arms posed to the controllers.
     */
    public static boolean isVrPlayerModel(PlayerModel<?> model) {
        try {
            return VrBackends.client().isVrPlayerModel(model);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * The texture with the magnified scope picture, -1 if the VR mod has none.
     */
    public static int scopeTexture() {
        try {
            return VrBackends.client().scopeTexture();
        } catch (Throwable t) {
            return -1;
        }
    }
}
