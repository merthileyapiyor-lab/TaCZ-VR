package com.taczvr.vr.visor;

import com.taczvr.TaczVR;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.addon.VisorAddon;

/**
 * TaCZ VR as a Visor addon, so it can listen to Visor's events.
 */
public final class TaczVrVisorAddon implements VisorAddon {
    public static volatile boolean loaded = false;

    @Override
    public void onAddonLoad() {
        if (!ModLoader.get().isDedicatedServer()) {
            VisorAPI.eventBus().registerListener(this, new VisorClientEvents());
        }
        loaded = true;
        TaczVR.LOGGER.info("Loaded as a Visor addon");
    }

    @Override
    public @NotNull String getAddonId() {
        return TaczVR.MOD_ID;
    }

    @Override
    public @NotNull Component getAddonName() {
        return Component.literal("TaCZ VR");
    }

    @Override
    public String getModId() {
        return TaczVR.MOD_ID;
    }
}
