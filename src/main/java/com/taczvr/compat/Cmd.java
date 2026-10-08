package com.taczvr.compat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Sends a command the way 1.19.3's {@code ClientPacketListener.sendCommand} does, for 1.19.2's signed chat.
 */
@OnlyIn(Dist.CLIENT)
public final class Cmd {
    private Cmd() {
    }

    public static void send(LocalPlayer player, String command) {
        if (!player.commandUnsigned(command)) {
            player.commandSigned(command, null);
        }
    }
}
