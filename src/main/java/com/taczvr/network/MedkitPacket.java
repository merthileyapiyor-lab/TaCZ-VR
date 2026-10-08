package com.taczvr.network;

import com.taczvr.compat.Mc;
import com.taczvr.content.MedkitItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * A VR player pushed their syringe into their own forearm (target is themselves) or into someone else.
 */
public record MedkitPacket(int targetId) {
    private static final double REACH = 3.0;
    private static final int COOLDOWN_TICKS = 15;
    private static final Map<ServerPlayer, Integer> LAST = new WeakHashMap<>();

    public static void encode(MedkitPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.targetId);
    }

    public static MedkitPacket decode(FriendlyByteBuf buf) {
        return new MedkitPacket(buf.readVarInt());
    }

    public static void handle(MedkitPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.isSpectator()) {
                return;
            }
            ItemStack syringe = player.getItemInHand(InteractionHand.MAIN_HAND);
            if (!(syringe.getItem() instanceof MedkitItem)) {
                syringe = player.getItemInHand(InteractionHand.OFF_HAND);
                if (!(syringe.getItem() instanceof MedkitItem)) {
                    return;
                }
            }
            Entity target = player.getLevel().getEntity(msg.targetId);
            if (!(target instanceof LivingEntity living) || !living.isAlive() || living.getHealth() >= living.getMaxHealth()
                    || target != player && Mc.distanceToSqr(target.getBoundingBox(), player.getEyePosition()) > REACH * REACH) {
                return;
            }
            Integer last = LAST.get(player);
            if (last != null && player.tickCount - last < COOLDOWN_TICKS && player.tickCount >= last) {
                return;
            }
            LAST.put(player, player.tickCount);
            MedkitItem.heal(living, player);
            MedkitItem.consume(player, syringe);
        });
        ctx.setPacketHandled(true);
    }
}
