package com.taczvr.vr;

import net.minecraft.world.InteractionHand;

/**
 * A VR controller, by the hand it holds items for.
 */
public enum VrHand {
    MAIN_HAND,
    OFF_HAND;

    public static VrHand of(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? MAIN_HAND : OFF_HAND;
    }

    public InteractionHand interactionHand() {
        return this == MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }
}
