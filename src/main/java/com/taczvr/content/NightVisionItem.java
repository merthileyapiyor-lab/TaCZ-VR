package com.taczvr.content;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Night vision goggles, worn as a helmet. On, they give night vision with a green picture. Switch them with the key
 * (N) or in VR by touching them with a hand and pressing grip.
 */
public class NightVisionItem extends ArmorItem {
    private static final String OFF = "Off";
    // refreshed before it gets short, night vision flickers in its last 10 seconds
    private static final int EFFECT_TICKS = 400;
    private static final int REFRESH_BELOW = 300;

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForSlot(EquipmentSlot slot) {
            return 150;
        }

        @Override
        public int getDefenseForSlot(EquipmentSlot slot) {
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
            return "taczvr:night_vision";
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

    public NightVisionItem(Properties properties) {
        super(MATERIAL, EquipmentSlot.HEAD, properties);
    }

    public static boolean isOn(ItemStack stack) {
        return stack.getItem() instanceof NightVisionItem && (stack.getTag() == null || !stack.getTag().getBoolean(OFF));
    }

    /**
     * Flips the goggles on the player's head, if they wear some.
     */
    public static boolean toggle(ServerPlayer player) {
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof NightVisionItem)) {
            return false;
        }
        boolean on = !isOn(helmet);
        helmet.getOrCreateTag().putBoolean(OFF, !on);
        player.getLevel().playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                on ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.35F, 2.0F);
        updateEffect(player);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add((isOn(stack) ? Component.translatable("item.taczvr.night_vision_goggles.on")
                : Component.translatable("item.taczvr.night_vision_goggles.off"))
                .withStyle(isOn(stack) ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.taczvr.night_vision_goggles.tip")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return "taczvr:textures/models/armor/night_vision_layer_1.png";
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player && player.tickCount % 10 == 0) {
            updateEffect(player);
        }
    }

    private static void updateEffect(ServerPlayer player) {
        boolean on = isOn(player.getItemBySlot(EquipmentSlot.HEAD));
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        // ours is ambient and hidden, a potion's isn't
        boolean ours = current != null && current.isAmbient() && !current.isVisible();
        if (on && (current == null || ours && current.getDuration() < REFRESH_BELOW)) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_TICKS, 0, true, false, false));
        } else if (!on && ours) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }
}
