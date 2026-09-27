package com.taczvr.network;

import com.taczvr.client.HighFive;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: someone high fived you here.
 */
public record HighFiveFeltPacket(Vec3 at) {
    public static void encode(HighFiveFeltPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.at.x);
        buf.writeDouble(msg.at.y);
        buf.writeDouble(msg.at.z);
    }

    public static HighFiveFeltPacket decode(FriendlyByteBuf buf) {
        return new HighFiveFeltPacket(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public static void handle(HighFiveFeltPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HighFive.felt(msg.at)));
        ctx.setPacketHandled(true);
    }
}
