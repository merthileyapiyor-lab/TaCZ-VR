package com.taczvr.server;

import com.taczvr.TaczVRConfig;
import com.tacz.guns.api.event.common.GunFireEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Muzzle flashes light up the dark: an invisible light block flashes at the muzzle for a moment with every shot.
 */
public final class MuzzleLight {
    private static final int LIGHT_LEVEL = 13;
    private static final int LIFE_TICKS = 3;
    // only where it's dark enough to notice
    private static final int DARK_BELOW = 10;
    private static final List<Flash> FLASHES = new ArrayList<>();
    static int flashes = 0;

    private record Flash(ServerLevel level, BlockPos pos, long until) {
    }

    private MuzzleLight() {
    }

    @SubscribeEvent
    public static void onFire(GunFireEvent event) {
        if (event.getLogicalSide().isClient() || !TaczVRConfig.COMMON.muzzleLight.get()
                || !(event.getShooter().getLevel() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity shooter = event.getShooter();
        Vec3 muzzle = null;
        if (shooter instanceof ServerPlayer player) {
            ServerAimStore.Aim aim = ServerAimStore.getAim(player);
            if (aim != null) {
                muzzle = aim.origin();
            }
        }
        if (muzzle == null) {
            muzzle = shooter.getEyePosition().add(shooter.getLookAngle().scale(0.8));
        }
        BlockPos pos = new BlockPos(muzzle);
        if (!level.getBlockState(pos).isAir() || level.getMaxLocalRawBrightness(pos) >= DARK_BELOW) {
            return;
        }
        level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL), 3);
        FLASHES.add(new Flash(level, pos, level.getGameTime() + LIFE_TICKS));
        flashes++;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || FLASHES.isEmpty()) {
            return;
        }
        Iterator<Flash> it = FLASHES.iterator();
        while (it.hasNext()) {
            Flash flash = it.next();
            if (flash.level.getGameTime() >= flash.until) {
                clear(flash);
                it.remove();
            }
        }
    }

    // never leave light blocks behind in the world
    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        FLASHES.forEach(MuzzleLight::clear);
        FLASHES.clear();
    }

    private static void clear(Flash flash) {
        if (flash.level.getBlockState(flash.pos).is(Blocks.LIGHT)) {
            flash.level.setBlock(flash.pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    public static int flashCount() {
        return flashes;
    }
}
