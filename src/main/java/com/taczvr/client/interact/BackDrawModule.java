package com.taczvr.client.interact;

import com.taczvr.BackGun;
import com.taczvr.TaczVR;
import com.taczvr.TaczVRConfig;
import com.taczvr.client.VrClient;
import com.taczvr.network.BackDrawPacket;
import com.taczvr.network.Net;
import com.taczvr.vr.GripModule;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPose;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

/**
 * With no gun in the gun hand, reach over your shoulder with it and press grip: the gun on your back comes into the
 * hand ({@link BackGun}) with a buzz. The gun hand is the main hand, so with left-handed play it's the left one.
 */
public final class BackDrawModule implements GripModule {
    private static final ResourceLocation ID = new ResourceLocation(TaczVR.MOD_ID, "back_draw");
    // the hand behind the eyes, from below the chin to above the head, not far out to the side
    private static final double BEHIND = 0.10;
    private static final double BELOW = 0.45;
    private static final double ABOVE = 0.30;
    private static final double SIDE = 0.50;
    public static int draws = 0;
    // the slot of the gun held last, drawn first
    private static int lastGunSlot = -1;

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public int getPriority() {
        return 960;
    }

    @Override
    public boolean isActive(LocalPlayer player, InteractionHand hand, Vec3 handPosition) {
        if (hand != InteractionHand.MAIN_HAND) {
            return false;
        }
        if (IGun.mainhandHoldGun(player)) {
            lastGunSlot = player.getInventory().selected;
            return false;
        }
        return canDraw(player) && behindHead(handPosition);
    }

    @Override
    public boolean onPress(LocalPlayer player, InteractionHand hand) {
        Net.CHANNEL.sendToServer(new BackDrawPacket(lastGunSlot));
        VrClient.haptic(VrHand.MAIN_HAND, 0.12F, 1.0F);
        draws++;
        return true;
    }

    @Override
    public boolean swingsArm() {
        return false;
    }

    /**
     * Whether reaching back would draw a gun: it's on, the gun hand has none, and there's one in the inventory.
     */
    public static boolean canDraw(LocalPlayer player) {
        return TaczVRConfig.CLIENT.backDraw.get() && !IGun.mainhandHoldGun(player) && BackGun.find(player, lastGunSlot) >= 0;
    }

    public static boolean behindHead(Vec3 handPosition) {
        VrPose pose = VrClient.localTickPose();
        if (pose == null || pose.getHead() == null) {
            return false;
        }
        Vec3 look = pose.getHead().getDir();
        Vec3 forward = new Vec3(look.x, 0.0, look.z);
        if (forward.lengthSqr() < 1.0E-4) {
            return false;
        }
        forward = forward.normalize();
        Vec3 offset = handPosition.subtract(pose.getHead().getPos());
        double behind = -offset.dot(forward);
        double side = Math.abs(offset.x * -forward.z + offset.z * forward.x);
        return behind > BEHIND && offset.y > -BELOW && offset.y < ABOVE && side < SIDE;
    }
}
