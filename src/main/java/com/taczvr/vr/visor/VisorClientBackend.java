package com.taczvr.vr.visor;

import com.taczvr.client.ClientSetup;
import com.taczvr.client.GripDriver;
import com.taczvr.client.ScopeCamera;
import com.taczvr.vr.GripModule;
import com.taczvr.vr.VrClientBackend;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPass;
import com.taczvr.vr.VrPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.input.action.framework.VRActionButton;
import org.vmstudio.visor.api.client.player.VRClientPlayer;
import org.vmstudio.visor.api.client.player.VRLocalPlayer;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.client.render.VRRenderPass;
import org.vmstudio.visor.api.common.HandType;

import java.util.List;

public final class VisorClientBackend implements VrClientBackend {
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
        if (ScopeCamera.isRendering()) {
            return VrPass.SCOPE;
        }
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

    /**
     * Visor's left trigger, the off-hand "left mouse" action. While dual wielding its click is skipped
     * ({@link VisorClientEvents}) and the off-hand gun reads it from here.
     */
    @Override
    public boolean offHandTriggerDown() {
        if (VisorClientEvents.leftTriggerDown) {
            return true;
        }
        VRActionButton trigger = VisorAPI.client().getInputManager().getActionLeftMouse(HandType.OFFHAND);
        return trigger != null && trigger.isPressed();
    }

    /**
     * Visor draws VR players with its own models, arms reaching to the controllers.
     */
    @Override
    public boolean isVrPlayerModel(PlayerModel<?> model) {
        return model.getClass().getName().startsWith("org.vmstudio.visor.");
    }

    /**
     * Visor's grip opens its hotbar, our grip modules get it first through {@link GripDriver}.
     */
    @Override
    public void registerGripModules(List<GripModule> modules) {
        GripDriver.setModules(modules);
        ClientSetup.modulesRegistered = true;
    }

    @Override
    public boolean usesGripDriver() {
        return true;
    }

    /**
     * Visor has no spyglass pass, the scope picture is rendered by {@link ScopeCamera}.
     */
    @Override
    public int scopeTexture() {
        return ScopeCamera.texture();
    }

    @Override
    public boolean usesScopeCamera() {
        return true;
    }
}

