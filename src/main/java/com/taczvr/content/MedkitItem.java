package com.taczvr.content;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import com.taczvr.server.GameManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A medical syringe. In VR push it into your other forearm, or into a friend. Otherwise hold use to inject
 * yourself, or use it on someone else.
 */
public class MedkitItem extends Item {
    public static final float HEAL = 8.0F;
    public static int heals = 0;
    public static int revives = 0;

    public MedkitItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.taczvr.medkit.tip")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getHealth() >= player.getMaxHealth()) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 24;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide()) {
            heal(entity, entity);
            consume(entity, stack);
        }
        return stack;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target.getHealth() >= target.getMaxHealth()) {
            return InteractionResult.PASS;
        }
        if (!player.getLevel().isClientSide()) {
            heal(target, player);
            consume(player, stack);
        }
        return InteractionResult.sidedSuccess(player.getLevel().isClientSide());
    }

    /**
     * Heals {@code target}. A friend down on the ground in the zombie waves gets straight back up.
     */
    public static void heal(LivingEntity target, @Nullable LivingEntity by) {
        if (target instanceof ServerPlayer downed && by instanceof ServerPlayer helper && helper != downed
                && GameManager.revive(downed, helper)) {
            revives++;
        }
        heals++;
        target.heal(HEAL);
        target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
        target.getLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.8F, 1.4F);
        target.getLevel().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 0.6F);
        if (target.getLevel() instanceof ServerLevel level) {
            // round the body, not in front of the eyes
            level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY(0.35), target.getZ(), 3, 0.35, 0.15, 0.35, 0.0);
        }
    }

    public static void consume(LivingEntity entity, ItemStack stack) {
        if (!(entity instanceof Player player) || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }
}
