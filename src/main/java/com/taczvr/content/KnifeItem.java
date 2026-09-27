package com.taczvr.content;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A combat knife: quick hits, stab forward with it in VR, and a stab in the back does a lot more.
 */
public class KnifeItem extends SwordItem {
    public static final float BACKSTAB = 2.5F;
    public static int backstabs = 0;

    public KnifeItem(Tier tier, int damage, float speed, Properties properties) {
        super(tier, damage, speed, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatableWithFallback("item.taczvr.combat_knife.tip", "VR: stab forward. From behind: x2.5 damage")
                .withStyle(ChatFormatting.GRAY));
    }

    /**
     * The attacker stands behind the victim, judged by where the victim's body faces.
     */
    public static boolean isBehind(Entity attacker, LivingEntity victim) {
        float yaw = victim.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 toAttacker = attacker.position().subtract(victim.position()).multiply(1.0, 0.0, 1.0);
        return toAttacker.lengthSqr() > 1.0E-4 && facing.dot(toAttacker.normalize()) < -0.3;
    }

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        DamageSource source = event.getSource();
        if (!(source.getDirectEntity() instanceof Player attacker) || !source.is(DamageTypes.PLAYER_ATTACK)
                || !(attacker.getMainHandItem().getItem() instanceof KnifeItem)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!isBehind(attacker, victim)) {
            return;
        }
        event.setAmount(event.getAmount() * BACKSTAB);
        backstabs++;
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
        if (victim.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 12, 0.2, 0.2, 0.2, 0.3);
        }
    }
}
