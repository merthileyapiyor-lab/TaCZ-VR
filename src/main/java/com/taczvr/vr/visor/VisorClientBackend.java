package com.taczvr.vr.visor;

import com.taczvr.vr.GripModule;
import com.taczvr.vr.VrClientBackend;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPass;
import com.taczvr.vr.VrPose;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.player.VRClientPlayer;
import org.vmstudio.visor.api.client.player.VRLocalPlayer;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.client.render.VRRenderPass;

import java.util.ArrayList;
import java.util.List;

public final class VisorClientBackend implements VrClientBackend {
    // the grip modules, for Visor's grip button (later)
    public static final List<GripModule> GRIP_MODULES = new ArrayList<>();

    @Override
    public boolean isVRActive() {
        return VisorAPI.clientState().stateMode().isActive();
    }

    private static VRLocalPlayer local() {
        return VisorAPI.client().getVRLocalPlayer();
    }

    @Override
    public @Nullable VrPose localTickPose() {
        return isVRActive() ? VisorPoses.wrap(local(), local().getPoseData(PlayerPoseType.TICK)) : null;
    }

    @Override
    public @Nullable VrPose latestRoomPose() {
        return isVRActive() ? VisorPoses.wrap(local(), local().getPoseData(PlayerPoseType.ROOM)) : null;
    }

    @Override
    public @Nullable VrPose renderPose(Player player) {
        if (player == Minecraft.getInstance().player) {
            return isVRActive() ? VisorPoses.wrap(local(), local().getPoseData(PlayerPoseType.RENDER)) : null;
        }
        VRClientPlayer remote = VisorAPI.client().getVRPlayer(player.getUUID());
        return remote == null ? null : VisorPoses.wrap(remote, remote.getPoseData(PlayerPoseType.RENDER));
    }

    @Override
    public float localWorldScale() {
        return isVRActive() ? local().getPoseData(PlayerPoseType.TICK).getWorldScale() : 1.0F;
    }

    @Override
    public void haptic(VrHand hand, float seconds, float amplitude) {
        VisorAPI.client().getInputManager().triggerHapticPulse(VisorPoses.hand(hand), 160.0F, amplitude, seconds);
    }

    @Override
    public @Nullable VrPass currentPass() {
        if (VisorAPI.clientState().renderPhase().isVanilla()) {
            return null;
        }
        VRRenderPass pass = VisorAPI.clientState().renderPass();
        if (pass == null || pass.isNull()) {
            return null;
        }
        if (pass.isFirstPerson()) {
            return VrPass.FIRST_PERSON;
        }
        return pass.isThirdPerson() ? VrPass.THIRD_PERSON : VrPass.OTHER;
    }

    @Override
    public void registerGripModules(List<GripModule> modules) {
        GRIP_MODULES.clear();
        GRIP_MODULES.addAll(modules);
    }
}
