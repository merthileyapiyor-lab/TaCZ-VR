package com.taczvr.network;

import com.taczvr.server.ServerAimStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Where the VR player's gun muzzle is and where it points, sent every tick while holding a gun.
 * The origin is relative to the player's position so movement between the tick and the shot doesn't matter.
 */
public record VrAimPacket(Vec3 originOffset, Vec3 direction) {

    public static void encode(VrAimPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat((float) msg.originOffset.x);
        buf.writeFloat((float) msg.originOffset.y);
        buf.writeFloat((float) msg.originOffset.z);
        buf.writeFloat((float) msg.direction.x);
        buf.writeFloat((float) msg.direction.y);
        buf.writeFloat((float) msg.direction.z);
    }

    public static VrAimPacket decode(FriendlyByteBuf buf) {
        Vec3 origin = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        Vec3 dir = new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat());
        return new VrAimPacket(origin, dir);
    }

    public static void handle(VrAimPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                ServerAimStore.update(player, msg.originOffset, msg.direction);
            }
        });
        ctx.setPacketHandled(true);
    }
}
