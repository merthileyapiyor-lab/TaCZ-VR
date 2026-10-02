package com.taczvr.client;

import com.taczvr.TaczVRConfig;
import com.taczvr.VrCommon;
import com.taczvr.network.HighFivePacket;
import com.taczvr.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

import java.util.ArrayList;
import java.util.List;

/**
 * High five: slap your hand against another player's hand and everyone hears it, both of you feel it.
 * Players without VR have no tracked hands, their hand is guessed in front of them.
 */
public final class HighFive {
    private static final double REACH_VR = 0.2;
    private static final double REACH_FLAT = 0.3;
    // meters per second the hand has to move, a slap and not a touch
    private static final double MIN_SPEED = 1.2;
    private static final int COOLDOWN_TICKS = 12;

    @Nullable
    private static Vec3 lastMain;
    @Nullable
    private static Vec3 lastOff;
    private static int cooldown;
    static int slaps = 0;

    private HighFive() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.phase != TickEvent.Phase.END || player == null || mc.level == null) {
            return;
        }
        VrPose pose = VrClient.isVRActive() && TaczVRConfig.CLIENT.highFive.get() ? VrClient.localTickPose() : null;
        VrPart main = pose == null ? null : pose.getMainHand();
        VrPart off = pose == null ? null : pose.getOffHand();
        Vec3 mainPos = main == null ? null : main.getPos();
        Vec3 offPos = off == null ? null : off.getPos();
        if (cooldown > 0) {
            cooldown--;
        } else if (serverHasMod()) {
            // hand speed relative to the body, so walking into someone isn't a slap
            Vec3 bodyMove = player.position().subtract(player.xo, player.yo, player.zo);
            if (!check(mc, player, mainPos, lastMain, bodyMove, VrHand.MAIN_HAND)) {
                check(mc, player, offPos, lastOff, bodyMove, VrHand.OFF_HAND);
            }
        }
        lastMain = mainPos;
        lastOff = offPos;
    }

    private static boolean check(Minecraft mc, LocalPlayer player, @Nullable Vec3 hand, @Nullable Vec3 last, Vec3 bodyMove,
                                 VrHand part) {
        if (hand == null || last == null) {
            return false;
        }
        double speed = hand.subtract(last).subtract(bodyMove).length() * 20.0;
        if (speed < MIN_SPEED) {
            return false;
        }
        for (Player other : mc.level.players()) {
            if (other == player || other.isSpectator() || other.distanceToSqr(player) > 9.0) {
                continue;
            }
            boolean vr = VrCommon.isVRPlayer(other) && VrCommon.getPose(other) != null;
            for (Vec3 theirs : hands(other)) {
                if (theirs.distanceTo(hand) < (vr ? REACH_VR : REACH_FLAT)) {
                    Vec3 at = hand.add(theirs).scale(0.5);
                    Net.CHANNEL.sendToServer(new HighFivePacket(other.getId(), at));
                    VrClient.haptic(part, 0.08F, 1.0F);
                    cooldown = COOLDOWN_TICKS;
                    slaps++;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Where their hands are: tracked for VR players, in front of the chest otherwise.
     */
    static List<Vec3> hands(Player other) {
        List<Vec3> hands = new ArrayList<>();
        VrPose pose = VrCommon.isVRPlayer(other) ? VrCommon.getPose(other) : null;
        if (pose != null) {
            for (VrPart hand : new VrPart[]{pose.getMainHand(), pose.getOffHand()}) {
                if (hand != null) {
                    hands.add(hand.getPos());
                }
            }
            return hands;
        }
        float yaw = Mth.DEG_TO_RAD * other.yBodyRot;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        Vec3 chest = other.position().add(0.0, other.getBbHeight() * 0.6, 0.0).add(forward.scale(0.35));
        hands.add(chest.add(right.scale(0.3)));
        hands.add(chest.add(right.scale(-0.3)));
        return hands;
    }

    /**
     * The other player slapped your hand: feel it in the hand that was hit.
     */
    public static void felt(Vec3 at) {
        VrPose pose = VrClient.isVRActive() ? VrClient.localTickPose() : null;
        if (pose == null) {
            return;
        }
        VrPart main = pose.getMainHand();
        VrPart off = pose.getOffHand();
        boolean mainCloser = off == null || main != null && main.getPos().distanceTo(at) <= off.getPos().distanceTo(at);
        VrClient.haptic(mainCloser ? VrHand.MAIN_HAND : VrHand.OFF_HAND, 0.08F, 1.0F);
    }

    private static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && Net.CHANNEL.isRemotePresent(connection.getConnection());
    }
}
