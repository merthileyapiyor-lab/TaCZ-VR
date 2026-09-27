package com.taczvr.client;

import com.taczvr.TaczVRConfig;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.sound.SoundManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Gunshots echo in caves and rooms and sound dry in the open: when walls and a ceiling are close around the shooter,
 * quieter copies of the distant shot sound follow, later the bigger the space.
 */
public final class GunEcho {
    private static final double REACH = 24.0;
    // how much of the surroundings has to be walled in to echo
    private static final double ENCLOSED = 0.6;
    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Vec3[] RAYS = rays();
    static int echoes = 0;
    static double lastEnclosed = -1.0;

    private record Pending(int due, LivingEntity shooter, ResourceLocation sound, float volume, float pitch) {
    }

    private GunEcho() {
    }

    private static Vec3[] rays() {
        List<Vec3> list = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * 2.0 * i / 8.0;
            list.add(new Vec3(Math.cos(a), 0.0, Math.sin(a)));
            list.add(new Vec3(Math.cos(a), 1.0, Math.sin(a)).normalize());
        }
        list.add(new Vec3(0.0, 1.0, 0.0));
        return list.toArray(new Vec3[0]);
    }

    @SubscribeEvent
    public static void onFire(GunFireEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.getLogicalSide().isClient() || mc.level == null || !TaczVRConfig.CLIENT.gunEcho.get()) {
            return;
        }
        LivingEntity shooter = event.getShooter();
        GunDisplayInstance display = TimelessAPI.getGunDisplay(event.getGunItemStack()).orElse(null);
        ResourceLocation sound = display == null ? null : display.getSounds(SoundManager.SHOOT_3P_SOUND);
        if (sound == null || shooter.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 96.0 * 96.0) {
            return;
        }
        Vec3 from = shooter.getEyePosition();
        int hits = 0;
        double total = 0.0;
        for (Vec3 ray : RAYS) {
            double distance = wallDistance(mc.level, from, ray, shooter);
            if (distance < REACH) {
                hits++;
                total += distance;
            }
        }
        double enclosed = (double) hits / RAYS.length;
        lastEnclosed = enclosed;
        if (enclosed < ENCLOSED) {
            return;
        }
        double size = total / hits;
        // a small room rings right after the shot, a big cave answers later
        int first = 1 + (int) (size / 6.0);
        float volume = (float) (0.45 * enclosed);
        int now = mc.player == null ? 0 : mc.player.tickCount;
        PENDING.add(new Pending(now + first, shooter, sound, volume, 0.92F));
        PENDING.add(new Pending(now + first * 2 + 1, shooter, sound, volume * 0.5F, 0.86F));
    }

    private static double wallDistance(Level level, Vec3 from, Vec3 dir, LivingEntity shooter) {
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(REACH)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, shooter));
        return hit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : hit.getLocation().distanceTo(from);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        if (mc.player == null) {
            PENDING.clear();
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending echo = it.next();
            if (mc.player.tickCount >= echo.due) {
                it.remove();
                if (echo.shooter.isAlive()) {
                    SoundPlayManager.playClientSound(echo.shooter, echo.sound, echo.volume, echo.pitch, 96);
                    echoes++;
                }
            }
        }
    }
}
