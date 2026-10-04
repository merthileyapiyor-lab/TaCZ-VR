package com.taczvr.vr;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The client half of {@link VrBackend}.
 */
public interface VrClientBackend {
    VrClientBackend NONE = new VrClientBackend() {
    };

    default boolean isVRActive() {
        return false;
    }

    /**
     * The local player's pose sampled before the tick, what the server receives for this tick.
     */
    @Nullable
    default VrPose localTickPose() {
        return null;
    }

    /**
     * The local player's pose in the physical room, unaffected by walking or turning in the game.
     */
    @Nullable
    default VrPose latestRoomPose() {
        return null;
    }

    /**
     * Any player's pose for the frame being rendered.
     */
    @Nullable
    default VrPose renderPose(Player player) {
        return null;
    }

    /**
     * What to add to a remote player's render pose so it lines up with the interpolated player model.
     */
    default Vec3 remoteInterpolationOffset(Player player, float partialTick) {
        return Vec3.ZERO;
    }

    default float localWorldScale() {
        return 1.0F;
    }

    default void haptic(VrHand hand, float seconds, float amplitude) {
    }

    @Nullable
    default VrPass currentPass() {
        return null;
    }

    /**
     * The off-hand trigger, which fires a gun held in the off-hand.
     */
    default boolean offHandTriggerDown() {
        return false;
    }

    /**
     * Whether the VR mod drew this player model, with arms posed to the controllers.
     */
    default boolean isVrPlayerModel(PlayerModel<?> model) {
        return false;
    }

    /**
     * The texture with the magnified picture of a scope you look through, -1 if the VR mod has none.
     */
    default int scopeTexture() {
        return -1;
    }

    default void registerGripModules(List<GripModule> modules) {
    }

    /**
     * Whether the grip modules run through our own {@code GripDriver}, for a VR mod without grip modules of its own.
     */
    default boolean usesGripDriver() {
        return false;
    }

    /**
     * Whether the zoomed scope picture comes from our own {@code ScopeCamera}, for a VR mod without a spyglass pass.
     */
    default boolean usesScopeCamera() {
        return false;
    }
}
