package com.taczvr.network;

import com.taczvr.TaczVR;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public final class Net {
    private static final String PROTOCOL = "9";

    // acceptMissingOr lets vanilla/unmodded peers join, the aim fix then simply falls back to the Vivecraft pose
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(TaczVR.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
            .serverAcceptedVersions(NetworkRegistry.acceptMissingOr(PROTOCOL))
            .simpleChannel();

    private Net() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, VrAimPacket.class, VrAimPacket::encode, VrAimPacket::decode, VrAimPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, HandoffPacket.class, HandoffPacket::encode, HandoffPacket::decode, HandoffPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, DetachAttachmentPacket.class, DetachAttachmentPacket::encode, DetachAttachmentPacket::decode,
                DetachAttachmentPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(3, VrMagazinePacket.class, VrMagazinePacket::encode, VrMagazinePacket::decode,
                VrMagazinePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, MagazineStatePacket.class, MagazineStatePacket::encode, MagazineStatePacket::decode,
                MagazineStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(5, RemoteMagazinePacket.class, RemoteMagazinePacket::encode, RemoteMagazinePacket::decode,
                RemoteMagazinePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(6, AssistPacket.class, AssistPacket::encode, AssistPacket::decode, AssistPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(7, AssistAllowedPacket.class, AssistAllowedPacket::encode, AssistAllowedPacket::decode,
                AssistAllowedPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(8, GameCommandPacket.class, GameCommandPacket::encode, GameCommandPacket::decode,
                GameCommandPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(9, HighFivePacket.class, HighFivePacket::encode, HighFivePacket::decode,
                HighFivePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(10, HighFiveFeltPacket.class, HighFiveFeltPacket::encode, HighFiveFeltPacket::decode,
                HighFiveFeltPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(11, OffhandShootPacket.class, OffhandShootPacket::encode, OffhandShootPacket::decode,
                OffhandShootPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(12, OffhandReloadPacket.class, OffhandReloadPacket::encode, OffhandReloadPacket::decode,
                OffhandReloadPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(13, OffhandFiredPacket.class, OffhandFiredPacket::encode, OffhandFiredPacket::decode,
                OffhandFiredPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(14, FlashPacket.class, FlashPacket::encode, FlashPacket::decode, FlashPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(15, KnifeStabPacket.class, KnifeStabPacket::encode, KnifeStabPacket::decode,
                KnifeStabPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(16, MedkitPacket.class, MedkitPacket::encode, MedkitPacket::decode, MedkitPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(17, NvgTogglePacket.class, NvgTogglePacket::encode, NvgTogglePacket::decode,
                NvgTogglePacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(18, ShopStatePacket.class, ShopStatePacket::encode, ShopStatePacket::decode,
                ShopStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(19, ShopActionPacket.class, ShopActionPacket::encode, ShopActionPacket::decode,
                ShopActionPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(20, DownedPacket.class, DownedPacket::encode, DownedPacket::decode, DownedPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(21, GrapplePacket.class, GrapplePacket::encode, GrapplePacket::decode, GrapplePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
}
