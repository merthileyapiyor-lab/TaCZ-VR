package com.taczvr.client;

import com.taczvr.TaczVR;
import com.taczvr.client.interact.AttachmentModule;
import com.taczvr.client.interact.HandoffModule;
import com.taczvr.client.interact.MagazineModule;
import com.taczvr.client.interact.NightVisionModule;
import net.minecraftforge.eventbus.api.IEventBus;
import com.taczvr.content.ModContent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.taczvr.vr.VrBackends;

import java.util.List;

public final class ClientSetup {
    /**
     * Set once the VR mod asked for our grip modules.
     */
    public static volatile boolean modulesRegistered = false;

    private ClientSetup() {
    }

    public static void init() {
        MinecraftForge.EVENT_BUS.register(VrGunController.class);
        MinecraftForge.EVENT_BUS.register(VrGunRenderer.class);
        MinecraftForge.EVENT_BUS.register(RemoteGunEffects.class);
        MinecraftForge.EVENT_BUS.register(HitFeedback.class);
        MinecraftForge.EVENT_BUS.register(ClientAssist.class);
        MinecraftForge.EVENT_BUS.register(GameMenu.class);
        MinecraftForge.EVENT_BUS.register(GunEcho.class);
        MinecraftForge.EVENT_BUS.register(HighFive.class);
        MinecraftForge.EVENT_BUS.register(OffhandGun.class);
        MinecraftForge.EVENT_BUS.register(ScreenEffects.class);
        MinecraftForge.EVENT_BUS.register(HandTools.class);
        MinecraftForge.EVENT_BUS.register(GrappleClient.class);
        MinecraftForge.EVENT_BUS.register(ClientKeys.class);
        MinecraftForge.EVENT_BUS.register(ShopScreen.class);
        MinecraftForge.EVENT_BUS.register(BulletWhiz.class);
        MinecraftForge.EVENT_BUS.register(LrTacticalVr.class);
        MinecraftForge.EVENT_BUS.register(AttachmentHint.class);
        MinecraftForge.EVENT_BUS.register(UpdateChecker.class);
        MinecraftForge.EVENT_BUS.register(GripDriver.class);
        MinecraftForge.EVENT_BUS.register(ScopeCamera.class);
        MinecraftForge.EVENT_BUS.addListener(TaczVRCommands::register);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(ScreenEffects::onRegisterOverlays);
        modBus.addListener(ClientKeys::onRegisterKeys);
        VrBackends.initClient();
        // SelfTest drives Vivecraft directly, it can't even load without it
        if (VrBackends.isVisor()) {
            VisorSelfTest.register();
        } else {
            SelfTest.register();
        }
        try {
            VrBackends.client().registerGripModules(List.of(new HandoffModule(), new MagazineModule(), new AttachmentModule(), new NightVisionModule()));
        } catch (Throwable t) {
            TaczVR.LOGGER.error("Could not register VR interactions, hand-off and mounting attachments by hand won't work", t);
        }
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.GRENADE_ENTITY.get(), GrenadeRenderer::new);
        event.registerEntityRenderer(ModContent.GRAPPLE_ENTITY.get(), GrappleClient.Renderer::new);
    }
}
