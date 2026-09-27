package com.taczvr.client;

import com.tacz.guns.entity.EntityKineticBullet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashSet;
import java.util.Set;

/**
 * Someone else's bullet flying close past your head whizzes, from the side it passed on.
 */
public final class BulletWhiz {
    private static final double NEAR = 1.8;
    private static final Set<Integer> WHIZZED = new HashSet<>();
    static int whizzes = 0;

    private BulletWhiz() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.phase != TickEvent.Phase.END || player == null || mc.level == null) {
            return;
        }
        Vec3 head = player.getEyePosition();
        Set<Integer> seen = new HashSet<>();
        for (EntityKineticBullet bullet : mc.level.getEntitiesOfClass(EntityKineticBullet.class, new AABB(head, head).inflate(64.0))) {
            seen.add(bullet.getId());
            if (bullet.getOwner() == player || WHIZZED.contains(bullet.getId())) {
                continue;
            }
            Vec3 from = new Vec3(bullet.xo, bullet.yo, bullet.zo);
            Vec3 to = bullet.position();
            Vec3 closest = closestPoint(head, from, to);
            if (closest.distanceTo(head) < NEAR) {
                WHIZZED.add(bullet.getId());
                whizzes++;
                // quieter the further it passed
                float volume = (float) (0.9 - closest.distanceTo(head) * 0.35);
                mc.level.playLocalSound(closest.x, closest.y, closest.z, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, volume,
                        1.8F + mc.level.random.nextFloat() * 0.3F, false);
            }
        }
        WHIZZED.retainAll(seen);
    }

    static Vec3 closestPoint(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        double t = lengthSqr < 1.0E-8 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / lengthSqr));
        return a.add(ab.scale(t));
    }
}
