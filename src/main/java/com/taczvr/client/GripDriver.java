package com.taczvr.client;

import com.taczvr.TaczVRConfig;
import com.taczvr.vr.GripModule;
import com.taczvr.vr.VrBackends;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs the {@link GripModule}s for a VR mod that doesn't have grip modules of its own (Visor), the way Vivecraft
 * runs its interact modules: every tick each hand finds the first module that can grab where it is, the grip button
 * goes to that module, and a held module keeps it until it's let go or says it's done.
 */
public final class GripDriver {
    private static List<GripModule> modules = List.of();
    private static final GripModule[] active = new GripModule[2];
    private static final boolean[] pressed = new boolean[2];
    private static final boolean[] down = new boolean[2];
    // the VR mod's own grip action was skipped for this press, so its release has to be skipped too
    private static final boolean[] swallowed = new boolean[2];

    private GripDriver() {
    }

    public static void setModules(List<GripModule> list) {
        List<GripModule> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingInt(GripModule::getPriority));
        modules = List.copyOf(sorted);
    }

    public static List<GripModule> modules() {
        return modules;
    }

    /**
     * The module that has this hand's grip right now, or null.
     */
    @Nullable
    public static GripModule activeModule(InteractionHand hand) {
        return active[hand.ordinal()];
    }

    /**
     * The VR mod reports a grip press or release.
     *
     * @return true when a module has this grip, so the VR mod must skip its own grip action (for the release too)
     */
    public static boolean onGrip(LocalPlayer player, InteractionHand hand, boolean isDown) {
        int i = hand.ordinal();
        down[i] = isDown;
        if (!isDown) {
            boolean skip = swallowed[i];
            swallowed[i] = false;
            if (active[i] != null) {
                reset(player, i);
            }
            return skip;
        }
        GripModule module = active[i];
        if (module == null) {
            return false;
        }
        pressed[i] = module.onPress(player, hand);
        if (pressed[i] && module.swingsArm()) {
            player.swing(hand);
        }
        swallowed[i] = true;
        return true;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !VrBackends.client().usesGripDriver()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        boolean inGame = mc.screen == null && VrClient.isVRActive() && TaczVRConfig.CLIENT.enabled.get() && !player.isSpectator();
        tick(player, inGame ? VrClient.localTickPose() : null);
    }

    static void tick(LocalPlayer player, @Nullable VrPose pose) {
        for (InteractionHand hand : InteractionHand.values()) {
            int i = hand.ordinal();
            GripModule module = active[i];
            if (down[i] && module != null && module.isHeld() && pressed[i] && pose != null && module.onHoldTick(player, hand)) {
                continue;
            }
            if (module != null) {
                reset(player, i);
            }
            VrPart part = pose == null ? null : pose.getHand(hand);
            if (part == null) {
                continue;
            }
            for (GripModule candidate : modules) {
                if (candidate.isActive(player, hand, part.getPos())) {
                    active[i] = candidate;
                    break;
                }
            }
            // a little buzz when the hand gets to something it can grab
            if (module == null && active[i] != null && TaczVRConfig.CLIENT.haptics.get()) {
                VrClient.haptic(VrHand.of(hand), 0.01F, 0.3F);
            }
        }
    }

    private static void reset(LocalPlayer player, int i) {
        InteractionHand hand = InteractionHand.values()[i];
        GripModule module = active[i];
        if (pressed[i] && module != null && module.isHeld()) {
            module.onRelease(player, hand);
        }
        pressed[i] = false;
        active[i] = null;
        for (GripModule each : modules) {
            each.reset(player, hand);
        }
    }
}
