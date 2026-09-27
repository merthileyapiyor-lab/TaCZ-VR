package com.taczvr.network;

import com.taczvr.server.OffhandGunServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Reload the off-hand gun too (sent along with the normal reload).
 */
public record OffhandReloadPacket() {
    public static void encode(OffhandReloadPacket msg, FriendlyByteBuf buf) {
    }

    public static OffhandReloadPacket decode(FriendlyByteBuf buf) {
        return new OffhandReloadPacket();
    }

    public static void handle(OffhandReloadPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                OffhandGunServer.reload(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
