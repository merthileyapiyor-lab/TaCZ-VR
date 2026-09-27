package com.taczvr.network;

import com.taczvr.TaczVRConfig;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A VR player hands the item in their main hand to another player.
 */
public record HandoffPacket(int targetId) {
    private static final double MAX_DISTANCE = 4.0;

    public static void encode(HandoffPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.targetId);
    }

    public static HandoffPacket decode(FriendlyByteBuf buf) {
        return new HandoffPacket(buf.readVarInt());
    }

    public static boolean canHandOff(ItemStack stack) {
        return !stack.isEmpty() && (IGun.getIGunOrNull(stack) != null || IAmmo.getIAmmoOrNull(stack) != null
                || IAttachment.getIAttachmentOrNull(stack) != null);
    }

    public static void handle(HandoffPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer giver = ctx.getSender();
            if (giver == null || !TaczVRConfig.COMMON.allowHandoff.get() || giver.isSpectator() || !giver.isAlive()) {
                return;
            }
            Entity entity = giver.level().getEntity(msg.targetId);
            if (!(entity instanceof ServerPlayer receiver) || receiver == giver || receiver.isSpectator()
                    || !receiver.isAlive() || receiver.distanceTo(giver) > MAX_DISTANCE || !giver.hasLineOfSight(receiver)) {
                return;
            }
            transfer(giver, receiver);
        });
        ctx.setPacketHandled(true);
    }

    /**
     * Moves the giver's main hand item to the receiver: into their empty hand, else their inventory, else at their feet.
     *
     * @return false if there was nothing that can be handed over
     */
    public static boolean transfer(ServerPlayer giver, ServerPlayer receiver) {
        ItemStack stack = giver.getMainHandItem();
        if (!canHandOff(stack)) {
            return false;
        }
        ItemStack given = stack.copy();
        giver.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (receiver.getMainHandItem().isEmpty()) {
            receiver.setItemInHand(InteractionHand.MAIN_HAND, given);
        } else if (!receiver.getInventory().add(given)) {
            receiver.drop(given, false);
        }
        giver.inventoryMenu.broadcastChanges();
        receiver.inventoryMenu.broadcastChanges();
        receiver.level().playSound(null, receiver.getX(), receiver.getY(), receiver.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
        // with a fallback, the receiver doesn't need this mod
        receiver.displayClientMessage(Component.translatableWithFallback("taczvr.handoff.received",
                "%s gave you %s", giver.getDisplayName(), given.getHoverName()), true);
        return true;
    }
}
