package com.taczvr.vr.vivecraft;

import com.taczvr.vr.VrBackend;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.VRAPI;
import org.vivecraft.api.data.VRPoseHistory;

public final class VivecraftBackend implements VrBackend {
    @Override
    public String name() {
        return "vivecraft";
    }

    @Override
    public boolean isVRPlayer(Player player) {
        return VRAPI.instance().isVRPlayer(player);
    }

    @Override
    public @Nullable VrPose getPose(Player player) {
        return VivecraftPoses.wrap(VRAPI.instance().getVRPose(player));
    }

    @Override
    public @Nullable Vec3 handVelocity(Player player, VrHand hand) {
        VRPoseHistory history = VRAPI.instance().getHistoricalVRPoses(player);
        return history == null || history.ticksOfHistory() < 2 ? null
                : history.averageVelocity(VivecraftPoses.part(hand), Math.min(3, history.ticksOfHistory() - 1));
    }
}
