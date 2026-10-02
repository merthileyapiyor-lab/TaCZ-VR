package com.taczvr.server;

import com.taczvr.VrCommon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

/**
 * A VR player's shield stops bullets and other projectiles by where it is held, no need to "use" it: if the shot's
 * path passes the hand holding the shield before it gets to the body, the shield takes it.
 */
public final class ShieldBlock {
    // how far from the hand a shot still hits the shield, a shield is about 60cm tall
    private static final double SHIELD_RADIUS = 0.4;
    static int blocked = 0;

    private ShieldBlock() {
    }

    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getSource().getDirectEntity() instanceof Projectile shot)) {
            return;
        }
        InteractionHand hand = shieldHand(player);
        VrPose pose = hand == null ? null : VrCommon.getPose(player);
        VrPart held = pose == null ? null : pose.getHand(hand);
        Vec3 motion = shot.getDeltaMovement();
        if (held == null || motion.lengthSqr() < 1.0E-6) {
            return;
        }
        Vec3 dir = motion.normalize();
        // a bit back along the path, the shot may already be inside the player
        Vec3 from = shot.position().subtract(dir.scale(3.0));
        Vec3 shield = held.getPos();
        double alongShield = shield.subtract(from).dot(dir);
        double alongBody = player.getBoundingBox().getCenter().subtract(from).dot(dir);
        double miss = from.add(dir.scale(alongShield)).distanceTo(shield);
        if (miss > SHIELD_RADIUS || alongShield > alongBody + 0.1 || alongShield < 0.0) {
            return;
        }
        event.setCanceled(true);
        blocked++;
        ItemStack stack = player.getItemInHand(hand);
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS,
                1.0F, 0.8F + player.getRandom().nextFloat() * 0.4F);
        shot.discard();
    }

    @Nullable
    private static InteractionHand shieldHand(ServerPlayer player) {
        if (player.getOffhandItem().canPerformAction(ToolActions.SHIELD_BLOCK)) {
            return InteractionHand.OFF_HAND;
        }
        return player.getMainHandItem().canPerformAction(ToolActions.SHIELD_BLOCK) ? InteractionHand.MAIN_HAND : null;
    }

    public static int blockedCount() {
        return blocked;
    }
}
