package com.taczvr.network;

import com.taczvr.BackGun;
import com.tacz.guns.api.item.IGun;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Draw the gun from your back: a hotbar gun gets selected, one further in the inventory swaps with what's in the hand.
 */
public record BackDrawPacket(int lastSlot) {
    public static void encode(BackDrawPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.lastSlot);
    }

    public static BackDrawPacket decode(FriendlyByteBuf buf) {
        return new BackDrawPacket(buf.readVarInt());
    }

    public static void handle(BackDrawPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.isSpectator() || !player.isAlive() || IGun.mainHandHoldGun(player)) {
                return;
            }
            int slot = BackGun.find(player, msg.lastSlot);
            if (slot < 0) {
                return;
            }
            Inventory inventory = player.getInventory();
            if (Inventory.isHotbarSlot(slot)) {
                inventory.selected = slot;
                player.connection.send(new ClientboundSetCarriedItemPacket(slot));
            } else {
                ItemStack inHand = inventory.getItem(inventory.selected);
                inventory.setItem(inventory.selected, inventory.getItem(slot));
                inventory.setItem(slot, inHand);
            }
        });
        ctx.setPacketHandled(true);
    }
}
