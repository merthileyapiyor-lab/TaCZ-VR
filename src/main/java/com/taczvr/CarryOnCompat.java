package com.taczvr;

import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Carry On (picking up blocks and creatures), looked up by name: this mod neither needs it nor ships any of it.
 */
public final class CarryOnCompat {
    private static volatile boolean looked = false;
    @Nullable
    private static Method getCarryData;
    @Nullable
    private static Method isCarrying;

    private CarryOnCompat() {
    }

    /**
     * Whether the player carries a block or creature with Carry On. While they do, both their hands are empty.
     */
    public static boolean isCarrying(Player player) {
        if (!looked) {
            try {
                Class<?> manager = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
                Method data = manager.getMethod("getCarryData", Player.class);
                isCarrying = data.getReturnType().getMethod("isCarrying");
                getCarryData = data;
            } catch (ReflectiveOperationException | LinkageError e) {
                getCarryData = null;
            }
            looked = true;
        }
        if (getCarryData == null || isCarrying == null) {
            return false;
        }
        try {
            Object data = getCarryData.invoke(null, player);
            return data != null && Boolean.TRUE.equals(isCarrying.invoke(data));
        } catch (ReflectiveOperationException | RuntimeException e) {
            VrCommon.logOnce(e);
            return false;
        }
    }
}
