package com.taczvr.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Night vision and blood vision goggles in one, made by dropping one pair onto the other in the inventory. Night
 * vision is switched like on the night vision goggles, but at the right side of the head; blood vision shows while a
 * hand is held at the left side of the head.
 */
public class DualVisionItem extends NightVisionItem {

    public DualVisionItem(Properties properties) {
        super(properties);
    }

    /**
     * Puts the two goggles together when one is dropped onto the other, the night vision setting carries over.
     *
     * @param inSlot  the goggles in the slot that was clicked
     * @param carried the goggles held on the cursor
     * @return whether they were put together
     */
    static boolean combine(ItemStack inSlot, ItemStack carried, Slot slot, ClickAction action, Player player,
                           SlotAccess carriedAccess) {
        if (action != ClickAction.PRIMARY || !slot.allowModification(player)) {
            return false;
        }
        ItemStack nightVision;
        ItemStack bloodVision;
        if (isPlainNightVision(inSlot) && carried.getItem() instanceof BloodVisionItem) {
            nightVision = inSlot;
            bloodVision = carried;
        } else if (inSlot.getItem() instanceof BloodVisionItem && isPlainNightVision(carried)) {
            nightVision = carried;
            bloodVision = inSlot;
        } else {
            return false;
        }
        ItemStack both = new ItemStack(ModContent.DUAL_VISION_GOGGLES.get());
        if (nightVision.getTag() != null) {
            both.setTag(nightVision.getTag().copy());
        }
        both.setDamageValue(Math.max(nightVision.getDamageValue(), bloodVision.getDamageValue()));
        slot.set(both);
        carriedAccess.set(ItemStack.EMPTY);
        if (!player.level().isClientSide()) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SMITHING_TABLE_USE,
                    SoundSource.PLAYERS, 0.6F, 1.4F);
        }
        return true;
    }

    private static boolean isPlainNightVision(ItemStack stack) {
        return stack.getItem() instanceof NightVisionItem && !(stack.getItem() instanceof DualVisionItem);
    }

    public static boolean isWorn(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof DualVisionItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add((isOn(stack) ? Component.translatableWithFallback("item.taczvr.night_vision_goggles.on", "On")
                : Component.translatableWithFallback("item.taczvr.night_vision_goggles.off", "Off"))
                .withStyle(isOn(stack) ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatableWithFallback("item.taczvr.dual_vision_goggles.tip",
                "Right side of your head + grip: night vision on/off (B). Hand at the left side: blood vision (K)")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "taczvr:textures/models/armor/dual_vision_layer_1.png";
    }
}
