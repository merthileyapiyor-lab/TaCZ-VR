package com.taczvr.network;

import com.taczvr.server.GameManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Start a round (see {@link GameManager} for the modes) or stop it, from the op game menu.
 * Ignored unless the sender is an op or hosts the world.
 */
public record GameCommandPacket(int action) {
    public static void encode(GameCommandPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
    }

    public static GameCommandPacket decode(FriendlyByteBuf buf) {
        return new GameCommandPacket(buf.readVarInt());
    }

    public static void handle(GameCommandPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null && GameManager.canControl(player)) {
                GameManager.command(player, msg.action);
            }
        });
        ctx.setPacketHandled(true);
    }
}
