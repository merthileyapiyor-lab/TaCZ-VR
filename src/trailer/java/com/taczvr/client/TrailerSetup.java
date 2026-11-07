package com.taczvr.client;

import com.taczvr.TaczVR;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Only in the dev client with {@code -Ptrailer=<out.mp4>}: records {@link Trailer}.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = TaczVR.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TrailerSetup {
    private TrailerSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (System.getProperty("taczvr.trailer") != null) {
            MinecraftForge.EVENT_BUS.register(Trailer.class);
        }
    }
}
