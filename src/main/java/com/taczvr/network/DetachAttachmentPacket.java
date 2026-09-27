package com.taczvr.network;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ServerMessageRefreshRefitScreen;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Takes an attachment off the gun in the main hand and puts it in the off-hand. Same checks as TACZ's own unload
 * message, which would put it into the inventory instead.
 */
public record DetachAttachmentPacket(AttachmentType type) {

    public static void encode(DetachAttachmentPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.type);
    }

    public static DetachAttachmentPacket decode(FriendlyByteBuf buf) {
        return new DetachAttachmentPacket(buf.readEnum(AttachmentType.class));
    }

    public static void handle(DetachAttachmentPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || msg.type == AttachmentType.NONE) {
                return;
            }
            ItemStack gun = player.getMainHandItem();
            IGun iGun = IGun.getIGunOrNull(gun);
            if (iGun == null || iGun.hasAttachmentLock(gun)) {
                return;
            }
            ItemStack attachment = iGun.getAttachment(gun, msg.type);
            if (attachment.isEmpty()) {
                return;
            }
            boolean placed;
            if (player.getOffhandItem().isEmpty()) {
                player.setItemInHand(InteractionHand.OFF_HAND, attachment);
                placed = true;
            } else {
                placed = player.getInventory().add(attachment);
            }
            if (!placed) {
                return;
            }
            iGun.unloadAttachment(gun, msg.type);
            AttachmentPropertyManager.postChangeEvent(player, gun);
            // an extended magazine takes its rounds with it
            if (msg.type == AttachmentType.EXTENDED_MAG) {
                iGun.dropAllAmmo(player, gun);
            }
            player.inventoryMenu.broadcastChanges();
            NetworkHandler.sendToClientPlayer(new ServerMessageRefreshRefitScreen(), player);
        });
        ctx.setPacketHandled(true);
    }
}
