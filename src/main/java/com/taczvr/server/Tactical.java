package com.taczvr.server;

import com.taczvr.VrCommon;
import com.taczvr.content.GrenadeEntity;
import com.taczvr.content.NightVisionItem;
import com.taczvr.network.FlashPacket;
import com.taczvr.network.Net;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.data.VRPose;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Flashbangs and smoke grenades. A flash blinds the players who see it, the more the closer and the straighter they
 * look at it, and stuns mobs for a few seconds. Smoke hides whoever is in or behind it from mobs.
 */
public final class Tactical {
    public static final int SMOKE_TICKS = 400;
    private static final double SMOKE_RADIUS = 5.0;
    // the cloud grows at the start and thins out at the end
    private static final int SMOKE_FADE_TICKS = 60;
    private static final double FLASH_RANGE = 24.0;
    private static final double FLASH_FULL_RANGE = 6.0;
    private static final double MOB_STUN_RANGE = 12.0;
    private static final int STUN_TICKS = 80;

    private static final List<GrenadeEntity> CLOUDS = new ArrayList<>();
    private static final Map<Mob, Integer> STUNNED = new WeakHashMap<>();
    public static int flashes = 0;
    public static int smokePuffs = 0;

    private Tactical() {
    }

    public static void flash(Level level, Vec3 at, @Nullable Entity owner) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        flashes++;
        server.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 1.9F);
        server.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 3.0F, 0.8F);
        server.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        server.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.02);
        for (ServerPlayer player : server.players()) {
            float strength = flashStrength(player, at);
            if (strength > 0.05F) {
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FlashPacket(strength));
            }
        }
        for (Mob mob : server.getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(MOB_STUN_RANGE))) {
            if (mob.distanceToSqr(at) <= MOB_STUN_RANGE * MOB_STUN_RANGE && clear(server, at, mob.getEyePosition(), mob)) {
                STUNNED.put(mob, STUN_TICKS);
                mob.setTarget(null);
                mob.getNavigation().stop();
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 2));
            }
        }
    }

    /**
     * How blinded a player gets, 0 to 1: full up close looking at it, less further away or looking away, a little
     * even from behind a wall right next to it. Night vision goggles make it worse.
     */
    public static float flashStrength(ServerPlayer player, Vec3 at) {
        if (player.isSpectator()) {
            return 0.0F;
        }
        VRPose pose = VrCommon.isVRPlayer(player) ? VrCommon.getPose(player) : null;
        Vec3 eye = pose != null && pose.getHead() != null ? pose.getHead().getPos() : player.getEyePosition();
        Vec3 look = pose != null && pose.getHead() != null ? pose.getHead().getDir() : player.getViewVector(1.0F);
        double distance = eye.distanceTo(at);
        if (distance > FLASH_RANGE) {
            return 0.0F;
        }
        double facing = distance < 0.01 ? 1.0 : look.dot(at.subtract(eye).normalize());
        float looking = facing > 0.5 ? 1.0F : facing > 0.0 ? 0.7F : facing > -0.5 ? 0.45F : 0.3F;
        float near = distance <= FLASH_FULL_RANGE ? 1.0F : (float) (1.0 - (distance - FLASH_FULL_RANGE) / (FLASH_RANGE - FLASH_FULL_RANGE));
        float strength = looking * near;
        if (!clear(player.serverLevel(), at, eye, player)) {
            strength = distance < 8.0 ? strength * 0.15F : 0.0F;
        }
        if (NightVisionItem.isOn(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD))) {
            strength = Math.min(1.0F, strength * 1.5F);
        }
        return strength;
    }

    private static boolean clear(Level level, Vec3 from, Vec3 to, Entity entity) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS;
    }

    public static void smoke(Level level, GrenadeEntity grenade) {
        if (!level.isClientSide() && !CLOUDS.contains(grenade)) {
            CLOUDS.add(grenade);
        }
    }

    public static double smokeRadius(GrenadeEntity grenade) {
        int left = grenade.smokeLeft();
        int age = SMOKE_TICKS - left;
        return SMOKE_RADIUS * Math.min(1.0, (age + 10.0) / SMOKE_FADE_TICKS) * Math.min(1.0, left / (double) SMOKE_FADE_TICKS);
    }

    public static int clouds() {
        return CLOUDS.size();
    }

    public static boolean isStunned(Mob mob) {
        return STUNNED.containsKey(mob);
    }

    /**
     * For mobs' line of sight: stunned mobs see nothing, smoke hides what's in or behind it.
     */
    public static boolean blocksSight(Mob mob, Entity target) {
        if (STUNNED.isEmpty() && CLOUDS.isEmpty()) {
            return false;
        }
        if (STUNNED.containsKey(mob)) {
            return true;
        }
        Vec3 from = mob.getEyePosition();
        Vec3 to = target.getEyePosition();
        for (GrenadeEntity cloud : CLOUDS) {
            if (cloud.level() != mob.level() || !cloud.isSmoking()) {
                continue;
            }
            double r = smokeRadius(cloud);
            if (distanceToSegment(cloud.position().add(0.0, r * 0.4, 0.0), from, to) < r) {
                return true;
            }
        }
        return false;
    }

    private static double distanceToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        double t = lengthSqr < 1.0E-6 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / lengthSqr));
        return a.add(ab.scale(t)).distanceTo(p);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!STUNNED.isEmpty()) {
            Iterator<Map.Entry<Mob, Integer>> it = STUNNED.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Mob, Integer> entry = it.next();
                Mob mob = entry.getKey();
                if (!mob.isAlive() || entry.getValue() <= 1) {
                    it.remove();
                } else {
                    entry.setValue(entry.getValue() - 1);
                    mob.setTarget(null);
                }
            }
        }
        Iterator<GrenadeEntity> it = CLOUDS.iterator();
        while (it.hasNext()) {
            GrenadeEntity cloud = it.next();
            if (cloud.isRemoved() || !cloud.isSmoking()) {
                it.remove();
                continue;
            }
            if (cloud.tickCount % 2 != 0 || !(cloud.level() instanceof ServerLevel level)) {
                continue;
            }
            double r = smokeRadius(cloud);
            double spread = Math.max(0.3, r * 0.4);
            for (ServerPlayer player : level.players()) {
                if (player.distanceToSqr(cloud) < 128.0 * 128.0) {
                    level.sendParticles(player, ParticleTypes.CAMPFIRE_COSY_SMOKE, true, cloud.getX(), cloud.getY() + r * 0.3,
                            cloud.getZ(), 14, spread, spread * 0.45, spread, 0.003);
                }
            }
            smokePuffs++;
        }
    }
}
