package com.taczvr.content;

import com.taczvr.TaczVR;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
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

    // 1.19.2 tabs aren't registered, the items name theirs
    public static final CreativeModeTab TAB = new CreativeModeTab(TaczVR.MOD_ID) {
        @Override
        public ItemStack makeIcon() {
            return new ItemStack(LOGO.get());
        }
    };

    public static final RegistryObject<Item> GRENADE = ITEMS.register("grenade",
            () -> new GrenadeItem(new Item.Properties().tab(TAB).stacksTo(16), GrenadeItem.Kind.FRAG));
    public static final RegistryObject<Item> FLASHBANG = ITEMS.register("flashbang",
            () -> new GrenadeItem(new Item.Properties().tab(TAB).stacksTo(16), GrenadeItem.Kind.FLASH));
    public static final RegistryObject<Item> SMOKE_GRENADE = ITEMS.register("smoke_grenade",
            () -> new GrenadeItem(new Item.Properties().tab(TAB).stacksTo(16), GrenadeItem.Kind.SMOKE));
    public static final RegistryObject<Item> COMBAT_KNIFE = ITEMS.register("combat_knife",
            () -> new KnifeItem(Tiers.IRON, 2, -1.6F, new Item.Properties().tab(TAB)));
    public static final RegistryObject<Item> MEDKIT = ITEMS.register("medkit", () -> new MedkitItem(new Item.Properties().tab(TAB).stacksTo(8)));
    public static final RegistryObject<Item> NIGHT_VISION_GOGGLES = ITEMS.register("night_vision_goggles",
            () -> new NightVisionItem(new Item.Properties().tab(TAB)));
    public static final RegistryObject<Item> GRAPPLING_HOOK = ITEMS.register("grappling_hook",
            () -> new GrapplingHookItem(new Item.Properties().tab(TAB).durability(128)));
    public static final RegistryObject<Item> RADIO = ITEMS.register("radio", () -> new RadioItem(new Item.Properties().tab(TAB).stacksTo(1)));
    // only the tab's icon, so it's in no tab itself
    public static final RegistryObject<Item> LOGO = ITEMS.register("logo", () -> new Item(new Item.Properties()));

    public static final RegistryObject<EntityType<GrenadeEntity>> GRENADE_ENTITY = ENTITIES.register("grenade",
            () -> EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(2).build("grenade"));
    public static final RegistryObject<EntityType<GrappleEntity>> GRAPPLE_ENTITY = ENTITIES.register("grapple",
            () -> EntityType.Builder.<GrappleEntity>of(GrappleEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(1).build("grapple"));

    private ModContent() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
    }
}
