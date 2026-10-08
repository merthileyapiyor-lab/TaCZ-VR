package com.taczvr.compat;

import com.taczvr.TaczVR;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.ShootResult;
import com.tacz.guns.api.item.gun.FireMode;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.sound.SoundPlayManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

/**
 * What TACZ 1.1.8 (Minecraft 1.20.1) has and TACZ 1.1.4, the newest for 1.19.2, doesn't.
 */
public final class TaczCompat {
    /**
     * Default laser red, 1.1.4 has no laser colors.
     */
    public static final int LASER_COLOR = 0xFF0000;

    private static boolean shootDown;
    @Nullable
    public static ShootResult lastShot;

    private TaczCompat() {
    }

    /**
     * TACZ 1.1.8's controller trigger, held down or not, every tick: semi-auto fires on the press, full-auto every
     * tick it's held. 1.1.4's own controller hooks only fire while the desktop window has the mouse, which it doesn't
     * need to in VR.
     */
    @OnlyIn(Dist.CLIENT)
    public static void shootControllerTick(boolean down) {
        boolean pressed = down && !shootDown;
        if (!down && shootDown) {
            SoundPlayManager.resetDryFireSound();
        }
        shootDown = down;
        LocalPlayer player = Minecraft.getInstance().player;
        if (!down || player == null || player.isSpectator()) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IGun iGun)) {
            return;
        }
        FireMode mode = iGun.getFireMode(stack);
        boolean continuous = mode == FireMode.AUTO || mode == FireMode.BURST && TimelessAPI.getCommonGunIndex(iGun.getGunId(stack))
                .map(index -> index.getGunData().getBurstData().isContinuousShoot()).orElse(false);
        // a gun with no fire mode set yet fires once per pull, like 1.1.8's controller trigger does
        if (continuous || pressed && mode != FireMode.AUTO) {
            lastShot = IClientPlayerGunOperator.fromLocalPlayer(player).shoot();
            if (lastShot != ShootResult.SUCCESS) {
                TaczVR.LOGGER.debug("VR trigger didn't fire: {}", lastShot);
            }
        }
    }

    /**
     * Guns fed straight from the inventory, with no magazine, came with 1.1.8.
     */
    public static boolean useInventoryAmmo(IGun iGun, ItemStack gun) {
        return false;
    }

    public static int rpm(IGun iGun, ItemStack gun) {
        return TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(index -> index.getGunData().getRoundsPerMinute(iGun.getFireMode(gun)))
                .orElse(300);
    }
}
