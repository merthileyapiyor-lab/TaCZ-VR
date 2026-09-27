package com.taczvr.network;

import com.taczvr.content.NightVisionItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Switch the night vision goggles you wear on or off.
 */
public record NvgTogglePacket() {
    public static void encode(NvgTogglePacket msg, FriendlyByteBuf buf) {
    }

    public static NvgTogglePacket decode(FriendlyByteBuf buf) {
        return new NvgTogglePacket();
    }

    public static void handle(NvgTogglePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                NightVisionItem.toggle(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
