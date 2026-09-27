package com.taczvr.client;

import com.taczvr.TaczVRConfig;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * Left-handed players (Main Hand: Left). TACZ is right-handed only: its first person gun is always on the right, and
 * on a left-handed player it doesn't draw the gun at all. This mod mirrors the first person gun and draws the gun in
 * the left hand for everyone else.
 */
public final class LeftHanded {
    static int mirroredFrames = 0;
    static int thirdPersonDrawn = 0;

    private LeftHanded() {
    }

    public static boolean enabled() {
        return TaczVRConfig.CLIENT.leftHandedGuns.get();
    }

    /**
     * Your own first person gun is mirrored to the left. Not in VR, there the gun is in the controller.
     */
    public static boolean mirrorsFirstPerson(Player player) {
        return enabled() && player.getMainArm() == HumanoidArm.LEFT && !VrClient.isVRActive();
    }

    public static void countMirrored() {
        mirroredFrames++;
    }

    public static void countThirdPerson() {
        thirdPersonDrawn++;
    }
}
