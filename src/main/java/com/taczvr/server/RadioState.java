package com.taczvr.server;

import com.taczvr.VrCommon;
import com.taczvr.content.RadioItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Who is talking on a radio and who hears it, worked out on the server thread every tick. The voice chat thread
 * (RadioVoice) only reads the snapshot.
 */
public final class RadioState {
    // closer than this they hear you anyway, no second voice from the radio
    public static final double NEAR = 6.0;
    private static final double MOUTH_REACH = 0.2;

    public record Listener(UUID id, ResourceKey<Level> level, Vec3 pos, @Nullable String team) {
    }

    private static volatile Set<UUID> transmitting = Set.of();
    private static volatile Map<UUID, Listener> listeners = Map.of();
    private static Set<UUID> transmittingBefore = new HashSet<>();
    /**
     * Development self-test only: players that count like online ones (fake players aren't in the player list).
     */
    public static final List<ServerPlayer> TEST_PLAYERS = new ArrayList<>();
    public static volatile boolean voiceChatLoaded = false;
    public static volatile int framesSent = 0;
    /**
     * Set by the voice chat side to hear when a transmission ends, so this class never touches voice chat classes.
     */
    @Nullable
    public static volatile Consumer<UUID> onRelease = null;

    private RadioState() {
    }

    public static boolean isTransmitting(UUID id) {
        return transmitting.contains(id);
    }

    /**
     * The players who hear this one on the radio: they carry a radio, aren't close by anyway, and in a team match
     * they're on the same team.
     */
    public static List<UUID> receivers(UUID sender) {
        Map<UUID, Listener> all = listeners;
        Listener from = all.get(sender);
        List<UUID> list = new ArrayList<>();
        if (from == null) {
            return list;
        }
        for (Listener to : all.values()) {
            if (to.id.equals(sender)) {
                continue;
            }
            if (to.level == from.level && to.pos.distanceToSqr(from.pos) < NEAR * NEAR) {
                continue;
            }
            if (from.team != null && !from.team.equals(to.team)) {
                continue;
            }
            list.add(to.id);
        }
        return list;
    }

    public static boolean hasRadio(ServerPlayer player) {
        return player.getInventory().hasAnyMatching(stack -> stack.getItem() instanceof RadioItem);
    }

    /**
     * Talking into it: using the radio (held up to the mouth), or in VR the hand with the radio at the mouth.
     */
    public static boolean talksIntoRadio(ServerPlayer player) {
        if (player.isUsingItem() && player.getUseItem().getItem() instanceof RadioItem) {
            return true;
        }
        VRPose pose = VrCommon.isVRPlayer(player) ? VrCommon.getPose(player) : null;
        if (pose == null || pose.getHead() == null) {
            return false;
        }
        Vec3 mouth = pose.getHead().getPos().add(pose.getHead().getDir().scale(0.06)).subtract(0.0, 0.09, 0.0);
        for (InteractionHand hand : InteractionHand.values()) {
            VRBodyPartData part = pose.getHand(hand);
            if (part != null && player.getItemInHand(hand).getItem() instanceof RadioItem && part.getPos().distanceTo(mouth) < MOUTH_REACH) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>(server.getPlayerList().getPlayers());
        players.addAll(TEST_PLAYERS);
        Map<UUID, Listener> nextListeners = new HashMap<>();
        Set<UUID> nextTransmitting = new HashSet<>();
        Map<UUID, ServerPlayer> byId = new HashMap<>();
        for (ServerPlayer player : players) {
            if (player.isSpectator() || !hasRadio(player)) {
                continue;
            }
            byId.put(player.getUUID(), player);
            nextListeners.put(player.getUUID(), new Listener(player.getUUID(), player.level().dimension(), player.position(),
                    GameManager.teamOf(player)));
            if (talksIntoRadio(player)) {
                nextTransmitting.add(player.getUUID());
            }
        }
        listeners = nextListeners;
        transmitting = nextTransmitting;
        // a click when you press and let go, for you and whoever listens
        for (UUID id : nextTransmitting) {
            if (!transmittingBefore.contains(id)) {
                click(byId, id, 1.9F);
            }
            ServerPlayer player = byId.get(id);
            if (player != null && player.tickCount % 10 == 0) {
                player.displayClientMessage(voiceChatLoaded
                        ? Component.translatableWithFallback("taczvr.radio.on_air", "Radio: on air (%s listening)", receivers(id).size())
                                .withStyle(ChatFormatting.GREEN)
                        : Component.translatableWithFallback("taczvr.radio.no_voice", "The radio needs Simple Voice Chat")
                                .withStyle(ChatFormatting.RED), true);
            }
        }
        for (UUID id : transmittingBefore) {
            if (!nextTransmitting.contains(id)) {
                click(byId, id, 1.4F);
                Consumer<UUID> hook = onRelease;
                if (hook != null) {
                    hook.accept(id);
                }
            }
        }
        transmittingBefore = nextTransmitting;
    }

    private static void click(Map<UUID, ServerPlayer> byId, UUID sender, float pitch) {
        List<UUID> hear = new ArrayList<>(receivers(sender));
        hear.add(sender);
        for (UUID id : hear) {
            ServerPlayer player = byId.get(id);
            if (player != null) {
                player.playNotifySound(SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.25F, pitch);
            }
        }
    }

}
