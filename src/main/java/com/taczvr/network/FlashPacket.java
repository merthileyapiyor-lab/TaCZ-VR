package com.taczvr.network;

import com.taczvr.client.ScreenEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: a flashbang went off in front of you, this bright (0 to 1).
 */
public record FlashPacket(float strength) {
    public static void encode(FlashPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.strength);
    }

    public static FlashPacket decode(FriendlyByteBuf buf) {
        return new FlashPacket(buf.readFloat());
    }

    public static void handle(FlashPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ScreenEffects.flash(msg.strength)));
        ctx.setPacketHandled(true);
    }
}
