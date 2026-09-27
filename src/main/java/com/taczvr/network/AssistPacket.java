package com.taczvr.network;

import com.taczvr.server.ServerAssist;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The assist menu's switches that the server has to know about. Ignored unless the player is on the assist list.
 */
public record AssistPacket(boolean infiniteAmmo, boolean aimAssist) {
    public static void encode(AssistPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.infiniteAmmo);
        buf.writeBoolean(msg.aimAssist);
    }

    public static AssistPacket decode(FriendlyByteBuf buf) {
        return new AssistPacket(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(AssistPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                ServerAssist.set(player, msg.infiniteAmmo, msg.aimAssist);
            }
        });
        ctx.setPacketHandled(true);
    }
}
