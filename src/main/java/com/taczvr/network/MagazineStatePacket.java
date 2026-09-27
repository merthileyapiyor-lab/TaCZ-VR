package com.taczvr.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * A VR player's manual magazine change: whether the magazine is out of the gun and whether the off-hand holds a new
 * one. Passed on to everyone who can see that player, so they see the magazine come out and go in.
 */
public record MagazineStatePacket(boolean out, boolean holding) {
    public static void encode(MagazineStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.out);
        buf.writeBoolean(msg.holding);
    }

    public static MagazineStatePacket decode(FriendlyByteBuf buf) {
        return new MagazineStatePacket(buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(MagazineStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player),
                        new RemoteMagazinePacket(player.getId(), msg.out, msg.holding));
            }
        });
        ctx.setPacketHandled(true);
    }
}
