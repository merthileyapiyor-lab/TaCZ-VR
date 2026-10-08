package com.taczvr.client.interact;

import com.taczvr.TaczVR;
import com.taczvr.TaczVRConfig;
import com.taczvr.VrCommon;
import com.taczvr.network.HandoffPacket;
import com.taczvr.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import com.taczvr.vr.GripModule;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

/**
 * Hold your gun, ammo or attachment against another player's hand and press grip: they get it.
 * Vivecraft gives this module the grip button only while the hand is at someone's hand.
 */
public final class HandoffModule implements GripModule {
    private static final ResourceLocation ID = new ResourceLocation(TaczVR.MOD_ID, "handoff");
    private static final double VR_HAND_REACH = 0.3;
    // players without VR don't have tracked hands, their hands are guessed and so get more slack
    private static final double FLAT_HAND_REACH = 0.45;

    @Nullable
    private Player target;

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public int getPriority() {
        return 900;
    }

    @Override
    public void reset(@Nullable LocalPlayer player, InteractionHand hand) {
        this.target = null;
    }

    @Override
    public boolean isActive(LocalPlayer player, InteractionHand hand, Vec3 handPosition) {
        this.target = null;
        if (hand != InteractionHand.MAIN_HAND || !TaczVRConfig.CLIENT.enabled.get() || !TaczVRConfig.CLIENT.handoff.get()
                || !HandoffPacket.canHandOff(player.getMainHandItem()) || !serverHasMod()) {
            return false;
        }
        double best = Double.MAX_VALUE;
        for (Player other : player.getLevel().players()) {
            if (other == player || other.isSpectator() || other.distanceToSqr(player) > 16.0) {
                continue;
            }
            double distance = distanceToHands(other, handPosition);
            if (distance < best) {
                best = distance;
                this.target = other;
            }
        }
        return this.target != null;
    }

    /**
     * @return how far {@code pos} is from the closest hand of {@code other}, beyond reach counts as infinite
     */
    private static double distanceToHands(Player other, Vec3 pos) {
        VrPose pose = VrCommon.isVRPlayer(other) ? VrCommon.getPose(other) : null;
        double best = Double.MAX_VALUE;
        if (pose != null) {
            for (VrPart hand : new VrPart[]{pose.getMainHand(), pose.getOffHand()}) {
                if (hand != null) {
                    double d = hand.getPos().distanceTo(pos);
                    if (d < VR_HAND_REACH && d < best) {
                        best = d;
                    }
                }
            }
            return best;
        }
        // roughly where a flat screen player's hands hang in front of their body
        float yaw = Mth.DEG_TO_RAD * other.yBodyRot;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 chest = other.position().add(0.0, other.getBbHeight() * 0.55, 0.0).add(forward.scale(0.3));
        for (double side : new double[]{-0.3, 0.3}) {
            double d = chest.add(right.scale(side)).distanceTo(pos);
            if (d < FLAT_HAND_REACH && d < best) {
                best = d;
            }
        }
        return best;
    }

    @Override
    public boolean onPress(LocalPlayer player, InteractionHand hand) {
        if (this.target == null || !HandoffPacket.canHandOff(player.getMainHandItem())) {
            return false;
        }
        Net.CHANNEL.sendToServer(new HandoffPacket(this.target.getId()));
        return true;
    }

    @Override
    public boolean swingsArm() {
        return false;
    }

    private static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && Net.CHANNEL.isRemotePresent(connection.getConnection());
    }
}
