package com.taczvr;

import com.mojang.logging.LogUtils;
import com.taczvr.client.ClientSetup;
import com.taczvr.network.Net;
import com.taczvr.content.KnifeItem;
import com.taczvr.content.ModContent;
import com.taczvr.content.NightVisionItem;
import com.taczvr.server.RadioState;
import com.taczvr.server.Tactical;
import com.taczvr.server.GameManager;
import com.taczvr.server.MuzzleLight;
import com.taczvr.server.OffhandGunServer;
import com.taczvr.server.ServerAimStore;
import com.taczvr.server.ServerAssist;
import com.taczvr.server.ShieldBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

@Mod(TaczVR.MOD_ID)
public class TaczVR {
    public static final String MOD_ID = "taczvr";
    public static final Logger LOGGER = LogUtils.getLogger();

    public TaczVR() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, TaczVRConfig.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TaczVRConfig.COMMON_SPEC);

        ModContent.register(FMLJavaModLoadingContext.get().getModEventBus());
        Net.register();
        MinecraftForge.EVENT_BUS.register(ServerAimStore.class);
        MinecraftForge.EVENT_BUS.register(ServerAssist.class);
        MinecraftForge.EVENT_BUS.register(ShieldBlock.class);
        MinecraftForge.EVENT_BUS.register(GameManager.class);
        MinecraftForge.EVENT_BUS.register(MuzzleLight.class);
        MinecraftForge.EVENT_BUS.register(OffhandGunServer.class);
        MinecraftForge.EVENT_BUS.register(Tactical.class);
        MinecraftForge.EVENT_BUS.register(KnifeItem.class);
        MinecraftForge.EVENT_BUS.register(NightVisionItem.class);
        MinecraftForge.EVENT_BUS.register(RadioState.class);
        MinecraftForge.EVENT_BUS.register(com.taczvr.content.GrappleEntity.class);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientSetup.init();
        }
    }
}
