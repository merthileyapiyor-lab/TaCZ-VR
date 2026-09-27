package com.taczvr.network;

import com.taczvr.client.GrappleClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to the hook's owner: your hook holds at this spot (you get pulled there), or it let go.
 */
public record GrapplePacket(boolean anchored, Vec3 at, boolean offHand) {
    public static void encode(GrapplePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.anchored);
        buf.writeDouble(msg.at.x);
        buf.writeDouble(msg.at.y);
        buf.writeDouble(msg.at.z);
        buf.writeBoolean(msg.offHand);
    }

    public static GrapplePacket decode(FriendlyByteBuf buf) {
        return new GrapplePacket(buf.readBoolean(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readBoolean());
    }

    public static void handle(GrapplePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> GrappleClient.anchor(msg.anchored ? msg.at : null, msg.offHand)));
        ctx.setPacketHandled(true);
    }
}
