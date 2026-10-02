package com.taczvr.vr;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Something a VR hand grabs with the grip button while it's at the right spot: a magazine, an attachment, a friend's
 * hand. The VR mod asks {@link #isActive} each tick and hands the grip button over while it says yes.
 * The defaults are Vivecraft's.
 */
public interface GripModule {
    ResourceLocation getId();

    /**
     * Lower goes first.
     */
    default int getPriority() {
        return 1000;
    }

    default void reset(@Nullable LocalPlayer player, InteractionHand hand) {
    }

    boolean isActive(LocalPlayer player, InteractionHand hand, Vec3 handPosition);

    boolean onPress(LocalPlayer player, InteractionHand hand);

    /**
     * Whether the grip can be held down: then {@link #onHoldTick} runs every tick and {@link #onRelease} at the end.
     */
    default boolean isHeld() {
        return false;
    }

    /**
     * @return false to stop holding
     */
    default boolean onHoldTick(LocalPlayer player, InteractionHand hand) {
        return true;
    }

    default void onRelease(@Nullable LocalPlayer player, InteractionHand hand) {
    }

    default boolean swingsArm() {
        return true;
    }
}
