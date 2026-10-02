package com.taczvr.vr;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * What TaCZ VR needs from a VR mod, on both sides. There's one per VR mod, and only the one for the installed mod is
 * ever loaded, so the rest of the mod never touches a VR mod's classes.
 */
public interface VrBackend {
    VrBackend NONE = new VrBackend() {
        @Override
        public String name() {
            return "none";
        }

        @Override
        public boolean isVRPlayer(Player player) {
            return false;
        }

        @Override
        public @Nullable VrPose getPose(Player player) {
            return null;
        }

        @Override
        public @Nullable Vec3 handVelocity(Player player, VrHand hand) {
            return null;
        }
    };

    String name();

    boolean isVRPlayer(Player player);

    /**
     * The player's latest pose for this tick: what the server received, or on a client what it was sent.
     */
    @Nullable
    VrPose getPose(Player player);

    /**
     * How fast a hand moved over the last few ticks, in world space, blocks per tick.
     */
    @Nullable
    Vec3 handVelocity(Player player, VrHand hand);
}
