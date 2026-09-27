package com.taczvr.network;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * A high five between the sender and another player: everyone around hears it, the other player feels it.
 */
public record HighFivePacket(int otherId, Vec3 at) {
    public static int received = 0;

    public static void encode(HighFivePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.otherId);
        buf.writeDouble(msg.at.x);
        buf.writeDouble(msg.at.y);
        buf.writeDouble(msg.at.z);
    }

    public static HighFivePacket decode(FriendlyByteBuf buf) {
        return new HighFivePacket(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
    }

    public static void handle(HighFivePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !Double.isFinite(msg.at.x + msg.at.y + msg.at.z) || msg.at.distanceTo(player.getEyePosition()) > 3.0) {
                return;
            }
            received++;
            player.serverLevel().playSound(null, msg.at.x, msg.at.y, msg.at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 1.7F);
            player.serverLevel().sendParticles(ParticleTypes.CRIT, msg.at.x, msg.at.y, msg.at.z, 8, 0.05, 0.05, 0.05, 0.2);
            Entity other = player.level().getEntity(msg.otherId);
            if (other instanceof ServerPlayer target && target != player && Net.CHANNEL.isRemotePresent(target.connection.connection)) {
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target), new HighFiveFeltPacket(msg.at));
            }
        });
        ctx.setPacketHandled(true);
    }
}
