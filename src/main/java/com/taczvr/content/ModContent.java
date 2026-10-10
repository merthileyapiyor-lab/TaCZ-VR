package com.taczvr.content;

import com.taczvr.TaczVR;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Things this mod adds to the game, all in its own creative tab.
 */
public final class ModContent {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TaczVR.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TaczVR.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TaczVR.MOD_ID);

    public static final RegistryObject<Item> GRENADE = ITEMS.register("grenade",
            () -> new GrenadeItem(new Item.Properties().stacksTo(16), GrenadeItem.Kind.FRAG));
    public static final RegistryObject<Item> FLASHBANG = ITEMS.register("flashbang",
            () -> new GrenadeItem(new Item.Properties().stacksTo(16), GrenadeItem.Kind.FLASH));
    public static final RegistryObject<Item> SMOKE_GRENADE = ITEMS.register("smoke_grenade",
            () -> new GrenadeItem(new Item.Properties().stacksTo(16), GrenadeItem.Kind.SMOKE));
    public static final RegistryObject<Item> COMBAT_KNIFE = ITEMS.register("combat_knife",
            () -> new KnifeItem(Tiers.IRON, 2, -1.6F, new Item.Properties()));
    public static final RegistryObject<Item> MEDKIT = ITEMS.register("medkit", () -> new MedkitItem(new Item.Properties().stacksTo(8)));
    public static final RegistryObject<Item> NIGHT_VISION_GOGGLES = ITEMS.register("night_vision_goggles",
            () -> new NightVisionItem(new Item.Properties()));
    public static final RegistryObject<Item> BLOOD_VISION_GOGGLES = ITEMS.register("blood_vision_goggles",
            () -> new BloodVisionItem(new Item.Properties()));
    public static final RegistryObject<Item> DUAL_VISION_GOGGLES = ITEMS.register("dual_vision_goggles",
            () -> new DualVisionItem(new Item.Properties()));
    public static final RegistryObject<Item> GRAPPLING_HOOK = ITEMS.register("grappling_hook",
            () -> new GrapplingHookItem(new Item.Properties().durability(128)));
    public static final RegistryObject<Item> RADIO = ITEMS.register("radio", () -> new RadioItem(new Item.Properties().stacksTo(1)));
    // only the tab's icon
    public static final RegistryObject<Item> LOGO = ITEMS.register("logo", () -> new Item(new Item.Properties()));

    public static final RegistryObject<EntityType<GrenadeEntity>> GRENADE_ENTITY = ENTITIES.register("grenade",
            () -> EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(2).build("grenade"));
    public static final RegistryObject<EntityType<GrappleEntity>> GRAPPLE_ENTITY = ENTITIES.register("grapple",
            () -> EntityType.Builder.<GrappleEntity>of(GrappleEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1).build("grapple"));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.taczvr"))
            .icon(() -> new ItemStack(LOGO.get()))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .displayItems((parameters, output) -> {
                output.accept(GRENADE.get());
                output.accept(FLASHBANG.get());
                output.accept(SMOKE_GRENADE.get());
                output.accept(COMBAT_KNIFE.get());
                output.accept(MEDKIT.get());
                output.accept(NIGHT_VISION_GOGGLES.get());
                output.accept(BLOOD_VISION_GOGGLES.get());
                output.accept(DUAL_VISION_GOGGLES.get());
                output.accept(GRAPPLING_HOOK.get());
                output.accept(RADIO.get());
            })
            .build());

    private ModContent() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        TABS.register(modBus);
    }
}
