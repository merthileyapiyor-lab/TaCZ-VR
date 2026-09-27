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
import org.vivecraft.api.client.VRClientAPI;

public final class ClientSetup {
    /**
     * Set once Vivecraft asked for our interact modules.
     */
    static volatile boolean modulesRegistered = false;

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
        MinecraftForge.EVENT_BUS.addListener(TaczVRCommands::register);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(ScreenEffects::onRegisterOverlays);
        modBus.addListener(ClientKeys::onRegisterKeys);
        SelfTest.register();
        try {
            // Vivecraft hands the grip button to these while the hand is at something they can grab
            VRClientAPI.instance().addClientRegistrationHandler(event -> {
                event.registerInteractModules(new HandoffModule(), new MagazineModule(), new AttachmentModule(), new NightVisionModule());
                modulesRegistered = true;
            });
        } catch (Throwable t) {
            TaczVR.LOGGER.error("Could not register VR interactions, hand-off and mounting attachments by hand won't work", t);
        }
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.GRENADE_ENTITY.get(), GrenadeRenderer::new);
        event.registerEntityRenderer(ModContent.GRAPPLE_ENTITY.get(), GrappleClient.Renderer::new);
    }
}
