package com.taczvr.network;

import com.taczvr.server.OffhandGunServer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * The off-hand gun's trigger was pulled: where its muzzle is (relative to the player) and where it points.
 */
public record OffhandShootPacket(Vec3 originOffset, Vec3 direction) {
    public static void encode(OffhandShootPacket msg, FriendlyByteBuf buf) {
        buf.writeDouble(msg.originOffset.x);
        buf.writeDouble(msg.originOffset.y);
        buf.writeDouble(msg.originOffset.z);
        buf.writeDouble(msg.direction.x);
        buf.writeDouble(msg.direction.y);
        buf.writeDouble(msg.direction.z);
    }

    public static OffhandShootPacket decode(FriendlyByteBuf buf) {
        return new OffhandShootPacket(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public static void handle(OffhandShootPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                OffhandGunServer.shoot(player, msg.originOffset, msg.direction);
            }
        });
        ctx.setPacketHandled(true);
    }
}
