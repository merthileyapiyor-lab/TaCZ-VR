package com.taczvr.client;

import com.taczvr.content.KnifeItem;
import com.taczvr.content.MedkitItem;
import com.taczvr.network.KnifeStabPacket;
import com.taczvr.network.MedkitPacket;
import com.taczvr.network.Net;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

/**
 * VR hand moves with tools: stab forward with the knife, push the syringe into your other forearm or a friend.
 */
public final class HandTools {
    // meters per second forward along the blade
    private static final double STAB_SPEED = 1.8;
    private static final double BLADE = 0.28;
    private static final double NEEDLE = 0.12;
    // the forearm runs from the controller back towards the elbow
    private static final double FOREARM = 0.28;
    private static final double FOREARM_REACH = 0.08;

    @Nullable
    private static Vec3 lastMain;
    private static int cooldown;
    static int stabsSent = 0;
    static int injectionsSent = 0;

    private HandTools() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.phase != TickEvent.Phase.END || player == null || mc.level == null) {
            return;
        }
        VRPose pose = VrClient.isVRActive() ? VrClient.localTickPose() : null;
        VRBodyPartData main = pose == null ? null : pose.getMainHand();
        if (main == null || player.isSpectator()) {
            lastMain = null;
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        Vec3 pos = main.getPos();
        Vec3 dir = main.getDir();
        ItemStack held = player.getMainHandItem();
        if (cooldown == 0 && serverHasMod()) {
            if (held.getItem() instanceof KnifeItem && lastMain != null) {
                stab(mc, player, pos, dir);
            } else if (held.getItem() instanceof MedkitItem) {
                inject(mc, player, pose, pos.add(dir.scale(NEEDLE)));
            }
        }
        lastMain = pos;
    }

    private static void stab(Minecraft mc, LocalPlayer player, Vec3 pos, Vec3 dir) {
        Vec3 bodyMove = player.position().subtract(player.xo, player.yo, player.zo);
        double forward = pos.subtract(lastMain).subtract(bodyMove).dot(dir) * 20.0;
        if (forward < STAB_SPEED) {
            return;
        }
        Vec3 tip = pos.add(dir.scale(BLADE));
        for (Entity entity : mc.level.getEntities(player, new AABB(pos, tip).inflate(0.5))) {
            if (!(entity instanceof LivingEntity living) || !living.isAlive() || entity.isSpectator()) {
                continue;
            }
            AABB box = entity.getBoundingBox().inflate(0.05);
            if (box.contains(tip) || box.clip(pos, tip).isPresent()) {
                Net.CHANNEL.sendToServer(new KnifeStabPacket(entity.getId()));
                VrClient.haptic(VRBodyPart.MAIN_HAND, 0.1F, 1.0F);
                cooldown = 8;
                stabsSent++;
                return;
            }
        }
    }

    private static void inject(Minecraft mc, LocalPlayer player, VRPose pose, Vec3 needle) {
        VRBodyPartData off = pose.getOffHand();
        if (off != null && player.getHealth() < player.getMaxHealth()) {
            Vec3 wrist = off.getPos();
            Vec3 elbow = wrist.subtract(off.getDir().scale(FOREARM));
            if (distanceToSegment(needle, wrist, elbow) < FOREARM_REACH) {
                sendInjection(player);
                VrClient.haptic(VRBodyPart.OFF_HAND, 0.15F, 0.6F);
                return;
            }
        }
        for (Player other : mc.level.players()) {
            if (other != player && !other.isSpectator() && other.getHealth() < other.getMaxHealth()
                    && other.getBoundingBox().inflate(0.03).contains(needle)) {
                sendInjection(other);
                return;
            }
        }
    }

    private static void sendInjection(LivingEntity target) {
        Net.CHANNEL.sendToServer(new MedkitPacket(target.getId()));
        VrClient.haptic(VRBodyPart.MAIN_HAND, 0.15F, 0.6F);
        cooldown = 20;
        injectionsSent++;
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / Math.max(1.0E-6, ab.lengthSqr())));
        return a.add(ab.scale(t)).distanceTo(p);
    }

    private static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && Net.CHANNEL.isRemotePresent(connection.getConnection());
    }
}
