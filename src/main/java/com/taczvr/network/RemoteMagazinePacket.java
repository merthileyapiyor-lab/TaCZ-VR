package com.taczvr.network;

import com.taczvr.client.RemoteGunEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: another player's manual magazine state, see {@link MagazineStatePacket}.
 */
public record RemoteMagazinePacket(int entityId, boolean out, boolean holding) {
    public static void encode(RemoteMagazinePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.out);
        buf.writeBoolean(msg.holding);
    }

    public static RemoteMagazinePacket decode(FriendlyByteBuf buf) {
        return new RemoteMagazinePacket(buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(RemoteMagazinePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> RemoteGunEffects.setMagazine(msg.entityId, msg.out, msg.holding)));
        ctx.setPacketHandled(true);
    }
}
