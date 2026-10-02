package com.taczvr.vr;

import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;

/**
 * A VR player's head and hands in world space, from whichever VR mod is installed.
 */
public interface VrPose {
    @Nullable
    VrPart getHead();

    /**
     * The controller holding the main hand item, the left one for left-handed players.
     */
    @Nullable
    VrPart getMainHand();

    @Nullable
    VrPart getOffHand();

    boolean isLeftHanded();

    boolean isSeated();

    @Nullable
    default VrPart getHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? this.getMainHand() : this.getOffHand();
    }

    @Nullable
    default VrPart getHand(VrHand hand) {
        return hand == VrHand.MAIN_HAND ? this.getMainHand() : this.getOffHand();
    }

    static VrPose of(@Nullable VrPart head, @Nullable VrPart mainHand, @Nullable VrPart offHand, boolean leftHanded, boolean seated) {
        return new Simple(head, mainHand, offHand, leftHanded, seated);
    }

    record Simple(@Nullable VrPart head, @Nullable VrPart mainHand, @Nullable VrPart offHand, boolean leftHanded,
                  boolean seated) implements VrPose {
        @Override
        public @Nullable VrPart getHead() {
            return this.head;
        }

        @Override
        public @Nullable VrPart getMainHand() {
            return this.mainHand;
        }

        @Override
        public @Nullable VrPart getOffHand() {
            return this.offHand;
        }

        @Override
        public boolean isLeftHanded() {
            return this.leftHanded;
        }

        @Override
        public boolean isSeated() {
            return this.seated;
        }
    }
}
