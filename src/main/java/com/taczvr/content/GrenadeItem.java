package com.taczvr.content;

import com.taczvr.VrCommon;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

/**
 * A hand grenade, flashbang or smoke grenade. Press and hold use (A in VR) to pull the pin, the fuse starts burning.
 * Let go to throw: in VR it flies the way and as fast as your hand swings, otherwise the way you look. Hold it too
 * long and it goes off in your hand.
 */
public class GrenadeItem extends Item {
    public static final int FUSE_TICKS = 80;
    // an arm swing is ~0.4 blocks/tick at the hand, a real throw carries further than that
    private static final double VR_THROW_BOOST = 1.8;
    private static final double MAX_THROW_SPEED = 3.0;

    public enum Kind {
        FRAG(FUSE_TICKS),
        FLASH(50),
        SMOKE(40);

        public final int fuse;

        Kind(int fuse) {
            this.fuse = fuse;
        }
    }

    private final Kind kind;

    public GrenadeItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIPWIRE_CLICK_ON, SoundSource.PLAYERS, 1.0F, 1.6F);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        int held = getUseDuration(stack) - remaining;
        if (level.isClientSide()) {
            return;
        }
        if (held % 20 == 0 && held > 0) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.5F, 2.0F);
        }
        if (held >= this.kind.fuse) {
            // cooked too long
            GrenadeEntity grenade = new GrenadeEntity(level, entity);
            grenade.setItem(stack.copyWithCount(1));
            grenade.setPos(throwOrigin(entity));
            grenade.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(grenade);
            consume(entity, stack);
            entity.stopUsingItem();
            grenade.detonate();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide()) {
            return;
        }
        int fuse = Math.max(1, this.kind.fuse - (getUseDuration(stack) - timeLeft));
        GrenadeEntity grenade = new GrenadeEntity(level, entity);
        grenade.setItem(stack.copyWithCount(1));
        grenade.setPos(throwOrigin(entity));
        grenade.setDeltaMovement(throwVelocity(entity));
        grenade.setFuse(fuse);
        level.addFreshEntity(grenade);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.EGG_THROW, SoundSource.PLAYERS, 0.8F, 0.7F);
        consume(entity, stack);
    }

    private static void consume(LivingEntity entity, ItemStack stack) {
        if (!(entity instanceof Player player) || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    @Nullable
    private static VrPart throwingHand(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return null;
        }
        VrPose pose = VrCommon.getPose(player);
        return pose == null ? null : pose.getHand(entity.getUsedItemHand());
    }

    private static Vec3 throwOrigin(LivingEntity entity) {
        VrPart hand = throwingHand(entity);
        return hand != null ? hand.getPos() : entity.getEyePosition().subtract(0.0, 0.1, 0.0);
    }

    private static Vec3 throwVelocity(LivingEntity entity) {
        Vec3 velocity = null;
        if (entity instanceof Player player && VrCommon.isVRPlayer(player)) {
            VrHand part = entity.getUsedItemHand() == InteractionHand.MAIN_HAND ? VrHand.MAIN_HAND : VrHand.OFF_HAND;
            Vec3 swing = VrCommon.handVelocity(player, part);
            if (swing != null) {
                velocity = swing.scale(VR_THROW_BOOST);
            }
        }
        if (velocity == null) {
            velocity = entity.getLookAngle().scale(1.2).add(0.0, 0.15, 0.0);
        }
        double speed = velocity.length();
        return speed > MAX_THROW_SPEED ? velocity.scale(MAX_THROW_SPEED / speed) : velocity;
    }
}
