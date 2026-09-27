package com.taczvr.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A radio: hold use and it goes up to your mouth, what you say is heard by everyone carrying a radio (your team in a
 * team match). In VR holding it to your mouth is enough. Talks through Simple Voice Chat, see RadioVoice.
 */
public class RadioItem extends Item {
    public RadioItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatableWithFallback("item.taczvr.radio.tip",
                "Hold use and talk (VR: hold it to your mouth). Everyone with a radio hears you").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatableWithFallback("item.taczvr.radio.needs", "Needs Simple Voice Chat")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // up to the mouth, like a goat horn
        return UseAnim.TOOT_HORN;
    }
}
