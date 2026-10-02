package com.taczvr.client.interact;

import com.taczvr.TaczVR;
import com.taczvr.client.VrClient;
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
import com.taczvr.vr.GripModule;
import com.taczvr.vr.VrPose;

/**
 * Touch the night vision goggles on your head with either hand and press grip to flip them on or off.
 */
public final class NightVisionModule implements GripModule {
    private static final ResourceLocation ID = new ResourceLocation(TaczVR.MOD_ID, "night_vision");
    private static final double REACH = 0.22;
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
