package com.taczvr.network;

import com.taczvr.client.ClientAssist;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: whether this player gets the assist menu.
 */
public record AssistAllowedPacket(boolean allowed) {
    public static void encode(AssistAllowedPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.allowed);
    }

    public static AssistAllowedPacket decode(FriendlyByteBuf buf) {
        return new AssistAllowedPacket(buf.readBoolean());
    }

    public static void handle(AssistAllowedPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAssist.setAllowed(msg.allowed)));
        ctx.setPacketHandled(true);
    }
}
