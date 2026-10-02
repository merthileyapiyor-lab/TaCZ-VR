package com.taczvr.content;

import com.taczvr.VrCommon;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.UseAnim;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

import java.util.List;

/**
 * A grappling hook. Use (A in VR) grabs the block the hand points at (or where you look) up to 200 blocks away, at
 * once, and pulls you there, where you hang until you use it again.
 */
public class GrapplingHookItem extends Item {

    public GrapplingHookItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatableWithFallback("item.taczvr.grappling_hook.tip", "Use: fire / let go. Reaches 200 blocks")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // held down it doesn't repeat (fire, let go, fire...): "using" it until the button comes up
        player.startUsingItem(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.consume(stack);
        }
        GrappleEntity current = GrappleEntity.of(player);
        if (current != null) {
            current.discard();
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.6F, 1.4F);
            return InteractionResultHolder.consume(stack);
        }
        Vec3 from = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        VrPose pose = VrCommon.isVRPlayer(player) ? VrCommon.getPose(player) : null;
        VrPart aim = pose == null ? null : pose.getHand(hand);
        if (aim != null) {
            from = aim.getPos();
            dir = aim.getDir();
        }
        GrappleEntity.fire(serverPlayer, hand, from, dir);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
        stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }
}
