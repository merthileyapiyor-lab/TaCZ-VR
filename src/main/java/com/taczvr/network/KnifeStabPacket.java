package com.taczvr.network;

import com.taczvr.content.KnifeItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * A VR player stabbed this entity with the knife in their hand: hit it like a normal attack.
 */
public record KnifeStabPacket(int targetId) {
    private static final double REACH = 3.5;
    private static final int COOLDOWN_TICKS = 6;
    private static final Map<ServerPlayer, Integer> LAST_STAB = new WeakHashMap<>();
    public static int stabs = 0;

    public static void encode(KnifeStabPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.targetId);
    }

    public static KnifeStabPacket decode(FriendlyByteBuf buf) {
        return new KnifeStabPacket(buf.readVarInt());
    }

    public static void handle(KnifeStabPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.isSpectator() || !(player.getMainHandItem().getItem() instanceof KnifeItem)) {
                return;
            }
            Entity target = player.level().getEntity(msg.targetId);
            if (!(target instanceof LivingEntity living) || target == player || !living.isAlive()
                    || target.getBoundingBox().distanceToSqr(player.getEyePosition()) > REACH * REACH) {
                return;
            }
            Integer last = LAST_STAB.get(player);
            if (last != null && player.tickCount - last < COOLDOWN_TICKS && player.tickCount >= last) {
                return;
            }
            LAST_STAB.put(player, player.tickCount);
            stabs++;
            player.attack(target);
            player.swing(InteractionHand.MAIN_HAND, true);
        });
        ctx.setPacketHandled(true);
    }
}
