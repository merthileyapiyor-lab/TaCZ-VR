package com.taczvr.network;

import com.taczvr.content.GrappleEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The client's render distance in chunks, sent when it changes: the grappling hook reaches as far as the player can
 * see, and a 1.20.1 server isn't told that otherwise.
 */
public record ViewDistancePacket(int chunks) {

    public static void encode(ViewDistancePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.chunks);
    }

    public static ViewDistancePacket decode(FriendlyByteBuf buf) {
        return new ViewDistancePacket(buf.readVarInt());
    }

    public static void handle(ViewDistancePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                GrappleEntity.setViewDistance(player, msg.chunks);
            }
        });
        ctx.setPacketHandled(true);
    }
}
