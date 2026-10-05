package com.taczvr.vr;

import com.taczvr.TaczVR;
import net.minecraftforge.fml.ModList;

/**
 * Picks the backend for the installed VR mod (Vivecraft), or none for a flat screen game.
 */
public final class VrBackends {
    private static VrBackend common = VrBackend.NONE;
    private static VrClientBackend client = VrClientBackend.NONE;

    private VrBackends() {
    }

    public static void init() {
        try {
            if (ModList.get().isLoaded("vivecraft")) {
                common = new com.taczvr.vr.vivecraft.VivecraftBackend();
            }
        } catch (Throwable t) {
            TaczVR.LOGGER.error("Could not hook into the VR mod, VR gun handling is off", t);
            common = VrBackend.NONE;
        }
        TaczVR.LOGGER.info("VR mod: {}", common.name());
    }

    public static void initClient() {
        try {
            // by name: an instanceof would load the backend class without the VR mod
            if (isVivecraft()) {
                client = new com.taczvr.vr.vivecraft.VivecraftClientBackend();
            }
        } catch (Throwable t) {
            TaczVR.LOGGER.error("Could not hook into the VR mod on the client, VR gun handling is off", t);
            client = VrClientBackend.NONE;
        }
    }

    public static VrBackend common() {
        return common;
    }

    public static VrClientBackend client() {
        return client;
    }

    public static boolean isVivecraft() {
        return "vivecraft".equals(common.name());
    }
}
