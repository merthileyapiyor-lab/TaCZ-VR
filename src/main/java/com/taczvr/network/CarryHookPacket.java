package com.taczvr.network;

import com.taczvr.content.GrapplingHookItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Use pressed while carrying something with Carry On: fire the grappling hook from the inventory, or let go of it.
 */
public record CarryHookPacket() {
    public static void encode(CarryHookPacket msg, FriendlyByteBuf buf) {
    }

    public static CarryHookPacket decode(FriendlyByteBuf buf) {
        return new CarryHookPacket();
    }

    public static void handle(CarryHookPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                GrapplingHookItem.useWhileCarrying(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
