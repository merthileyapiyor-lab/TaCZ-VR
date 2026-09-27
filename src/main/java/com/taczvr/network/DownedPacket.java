package com.taczvr.network;

import com.taczvr.DownedState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to every client: this player is down on the ground (crawling) or back on their feet.
 */
public record DownedPacket(int entityId, boolean downed) {
    public static void encode(DownedPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.downed);
    }

    public static DownedPacket decode(FriendlyByteBuf buf) {
        return new DownedPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(DownedPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            if (msg.downed) {
                DownedState.CLIENT.add(msg.entityId);
            } else {
                DownedState.CLIENT.remove(msg.entityId);
            }
        });
        ctx.setPacketHandled(true);
    }
}
