package com.taczvr.network;

import com.taczvr.client.ShopScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: the zombie shop opened, changed (points) or closed.
 *
 * @param seconds time left in the break, 0 when only the points changed
 */
public record ShopStatePacket(boolean open, int points, int seconds, boolean ready) {
    public static void encode(ShopStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.open);
        buf.writeVarInt(msg.points);
        buf.writeVarInt(msg.seconds);
        buf.writeBoolean(msg.ready);
    }

    public static ShopStatePacket decode(FriendlyByteBuf buf) {
        return new ShopStatePacket(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(ShopStatePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ShopScreen.state(msg.open, msg.points, msg.seconds, msg.ready)));
        ctx.setPacketHandled(true);
    }
}
