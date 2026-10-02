package com.taczvr.vr.visor;

import com.taczvr.vr.VrBackend;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.player.VRPlayer;
import org.vmstudio.visor.api.common.player.VRPoseHistory;
import org.vmstudio.visor.api.server.player.VRServerPlayer;

public final class VisorBackend implements VrBackend {
    private static final int VELOCITY_TICKS = 3;

    public VisorBackend() {
        // Visor takes addons until it has loaded, at the end of mod loading
        VisorAPI.registerAddon(new TaczVrVisorAddon());
    }

    @Override
    public String name() {
        return "visor";
    }

    @Nullable
    private static VRPlayer vrPlayer(Player player) {
        VRPlayer vr = VisorAPI.getVRPlayer(player);
        if (vr instanceof VRServerPlayer server && !server.hasPoseData()) {
            return null;
        }
        return vr;
    }

    @Override
    public boolean isVRPlayer(Player player) {
        return vrPlayer(player) != null;
    }

    @Override
    public @Nullable VrPose getPose(Player player) {
        VRPlayer vr = vrPlayer(player);
        return vr == null ? null : VisorPoses.wrap(vr, vr.getPoseData());
    }

    @Override
    public @Nullable Vec3 handVelocity(Player player, VrHand hand) {
        VRPlayer vr = vrPlayer(player);
        if (vr == null) {
            return null;
        }
        VRPoseHistory history = vr.getPoseHistoryTick();
        int ticks = Math.min(VELOCITY_TICKS, history.getHistorySize() - 1);
        if (ticks < 1) {
            return null;
        }
        Vector3f moved = history.netMovement(VisorPoses.part(hand), ticks);
        return new Vec3(moved.x / ticks, moved.y / ticks, moved.z / ticks);
    }
}
