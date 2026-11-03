package com.taczvr.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.taczvr.content.NightVisionItem;
import com.taczvr.network.Net;
import com.taczvr.network.NvgTogglePacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Keys of this mod. In VR they can be put on a controller button in Vivecraft's binding settings.
 */
public final class ClientKeys {
    public static final KeyMapping NIGHT_VISION = new KeyMapping("key.taczvr.night_vision", InputConstants.Type.KEYSYM,
            // N is Simple Voice Chat's
            GLFW.GLFW_KEY_B, "key.categories.taczvr");
    static int presses = 0;

    private ClientKeys() {
    }

    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(NIGHT_VISION);
        event.register(BloodVision.KEY);
        event.register(ShopScreen.KEY);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null) {
            return;
        }
        while (NIGHT_VISION.consumeClick()) {
            if (mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof NightVisionItem) {
                Net.CHANNEL.sendToServer(new NvgTogglePacket());
                presses++;
            }
        }
    }
}
