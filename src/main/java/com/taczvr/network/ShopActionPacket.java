package com.taczvr.network;

import com.taczvr.server.GameManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client to server, in the zombie shop: buy offer {@code action}, or {@link #READY} to say you're done.
 */
public record ShopActionPacket(int action) {
    public static final int READY = -1;

    public static void encode(ShopActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
    }

    public static ShopActionPacket decode(FriendlyByteBuf buf) {
        return new ShopActionPacket(buf.readVarInt());
    }

    public static void handle(ShopActionPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                GameManager.shopAction(player, msg.action);
            }
        });
        ctx.setPacketHandled(true);
    }
}
