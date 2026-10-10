package com.taczvr.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Blood vision goggles, worn as a helmet. While you hold your hand at the side of your head in VR (or hold the key),
 * every living thing nearby glows red through walls. The picture is drawn by {@code client.BloodVision}.
 */
public class BloodVisionItem extends ArmorItem {
    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(Type type) {
            return 150;
        }

        @Override
        public int getDefenseForType(Type type) {
            return 1;
        }

        @Override
        public int getEnchantmentValue() {
            return 9;
        }

        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_IRON;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(Items.IRON_INGOT);
        }

        @Override
        public String getName() {
            return "taczvr:blood_vision";
        }

        @Override
        public float getToughness() {
            return 0.0F;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.0F;
        }
    };

    public BloodVisionItem(Properties properties) {
        super(MATERIAL, Type.HELMET, properties);
    }

    /**
     * Whether the player wears goggles with blood vision, these or the dual vision goggles.
     */
    public static boolean isWorn(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof BloodVisionItem || DualVisionItem.isWorn(player);
    }

    /**
     * Night vision goggles dropped onto these make dual vision goggles.
     */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack other, Slot slot, ClickAction action, Player player,
                                            SlotAccess access) {
        return DualVisionItem.combine(stack, other, slot, action, player, access);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatableWithFallback("item.taczvr.blood_vision_goggles.tip",
                "Hold: in VR your hand at the side of your head, or K").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "taczvr:textures/models/armor/blood_vision_layer_1.png";
    }
}
