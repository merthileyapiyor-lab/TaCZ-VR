package com.taczvr.network;

import com.taczvr.client.RemoteGunEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: this player fired their off-hand gun, show its muzzle flash.
 */
public record OffhandFiredPacket(int entityId) {
    public static void encode(OffhandFiredPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
    }

    public static OffhandFiredPacket decode(FriendlyByteBuf buf) {
        return new OffhandFiredPacket(buf.readVarInt());
    }

    public static void handle(OffhandFiredPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> RemoteGunEffects.offhandFired(msg.entityId)));
        ctx.setPacketHandled(true);
    }
}
