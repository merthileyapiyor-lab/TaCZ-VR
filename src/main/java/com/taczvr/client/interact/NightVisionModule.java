package com.taczvr.client.interact;

import com.taczvr.TaczVR;
import com.taczvr.client.VrClient;
import com.taczvr.content.DualVisionItem;
import com.taczvr.content.NightVisionItem;
import com.taczvr.network.Net;
import com.taczvr.network.NvgTogglePacket;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import com.taczvr.vr.GripModule;
import com.taczvr.vr.VrPose;

/**
 * Touch the night vision goggles on your head with either hand and press grip to flip them on or off. On dual vision
 * goggles the spot is the right side of the head.
 */
public final class NightVisionModule implements GripModule {
    private static final ResourceLocation ID = new ResourceLocation(TaczVR.MOD_ID, "night_vision");
    private static final double REACH = 0.22;
    private static final double SIDE = 0.12;
    // smaller, the blood vision spot at the other side of the head is only 0.24 away
    private static final double SIDE_REACH = 0.16;
    public static int toggles = 0;

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public int getPriority() {
        return 950;
    }

    @Override
    public void reset(@Nullable LocalPlayer player, InteractionHand hand) {
    }

    @Override
    public boolean isActive(LocalPlayer player, InteractionHand hand, Vec3 handPosition) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        VrPose pose = VrClient.localTickPose();
        if (!(helmet.getItem() instanceof NightVisionItem) || pose == null || pose.getHead() == null) {
            return false;
        }
        if (helmet.getItem() instanceof DualVisionItem) {
            // dual vision goggles: night vision at the right side of the head, the left side is for blood vision
            Vector3f right = pose.getHead().getRotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
            Vec3 side = pose.getHead().getPos().add(right.x * SIDE, right.y * SIDE, right.z * SIDE);
            // really out to the right, the forehead is close to that spot too
            double outward = handPosition.subtract(pose.getHead().getPos()).dot(new Vec3(right.x, right.y, right.z));
            return handPosition.distanceTo(side) < SIDE_REACH && outward > 0.06;
        }
        // the goggles sit on the forehead, a bit in front of and above the eyes
        Vec3 goggles = pose.getHead().getPos().add(pose.getHead().getDir().scale(0.08)).add(0.0, 0.05, 0.0);
        return handPosition.distanceTo(goggles) < REACH;
    }

    @Override
    public boolean onPress(LocalPlayer player, InteractionHand hand) {
        Net.CHANNEL.sendToServer(new NvgTogglePacket());
        toggles++;
        return true;
    }

    @Override
    public boolean swingsArm() {
        return false;
    }
}
