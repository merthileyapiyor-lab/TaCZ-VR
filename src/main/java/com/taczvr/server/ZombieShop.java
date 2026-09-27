package com.taczvr.server;

import com.taczvr.content.ModContent;
import com.taczvr.network.Net;
import com.taczvr.network.ShopStatePacket;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Points and the shop of the zombie waves. Kills, cleared waves and revives earn points, spent in the shop that
 * opens between waves. Everything bought is marked and taken away again when the round ends.
 */
public final class ZombieShop {
    public static final String BOUGHT_TAG = "taczvr_bought";
    public static final int KILL_POINTS = 25;
    public static final int BOSS_POINTS = 300;
    public static final int REVIVE_POINTS = 50;
    public static final int WAVE_POINTS = 50;
    private static final String OBJECTIVE = "taczvr_points";

    /**
     * What the shop sells. Guns come with a full magazine, ammo is for the gun in your hand.
     */
    public record Offer(String key, int price, Function<ServerPlayer, ItemStack> make) {
    }

    public static final List<Offer> OFFERS = List.of(
            gun("glock_17", 100), gun("deagle", 300), gun("uzi", 350), gun("ump45", 450), gun("m870", 500),
            gun("ak47", 700), gun("kar98", 750), gun("m4a1", 800), gun("m249", 1500),
            new Offer("ammo", 80, ZombieShop::ammoForHeld),
            new Offer("grenade", 120, p -> new ItemStack(ModContent.GRENADE.get(), 2)),
            new Offer("flashbang", 80, p -> new ItemStack(ModContent.FLASHBANG.get(), 2)),
            new Offer("smoke_grenade", 60, p -> new ItemStack(ModContent.SMOKE_GRENADE.get(), 2)),
            new Offer("medkit", 150, p -> new ItemStack(ModContent.MEDKIT.get(), 2)),
            new Offer("combat_knife", 60, p -> new ItemStack(ModContent.COMBAT_KNIFE.get())));

    private static final Map<UUID, Integer> POINTS = new HashMap<>();
    private static final Set<UUID> READY = new HashSet<>();
    private static boolean open = false;
    @Nullable
    private static Objective sidebarBefore = null;
    public static int purchases = 0;

    private ZombieShop() {
    }

    private static Offer gun(String id, int price) {
        ResourceLocation gunId = new ResourceLocation("tacz", id);
        return new Offer(id, price, p -> {
            int ammo = TimelessAPI.getCommonGunIndex(gunId).map(CommonGunIndex::getGunData).map(data -> data.getAmmoAmount()).orElse(0);
            return GunItemBuilder.create().setId(gunId).setAmmoCount(ammo).setAmmoInBarrel(true).build();
        });
    }

    /**
     * Three magazines for the gun you hold, nothing without one.
     */
    private static ItemStack ammoForHeld(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(held);
        if (iGun == null) {
            return ItemStack.EMPTY;
        }
        return TimelessAPI.getCommonGunIndex(iGun.getGunId(held)).map(CommonGunIndex::getGunData)
                .map(data -> AmmoItemBuilder.create().setId(data.getAmmoId()).setCount(Math.min(64 * 4, Math.max(1, data.getAmmoAmount() * 3))).build())
                .orElse(ItemStack.EMPTY);
    }

    public static int points(ServerPlayer player) {
        return POINTS.getOrDefault(player.getUUID(), 0);
    }

    public static boolean isOpen() {
        return open;
    }

    public static void start(List<ServerPlayer> players) {
        POINTS.clear();
        READY.clear();
        open = false;
        Scoreboard scoreboard = players.isEmpty() ? null : players.get(0).getScoreboard();
        if (scoreboard == null) {
            return;
        }
        Objective old = scoreboard.getObjective(OBJECTIVE);
        if (old != null) {
            scoreboard.removeObjective(old);
        }
        sidebarBefore = scoreboard.getDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR);
        Objective objective = scoreboard.addObjective(OBJECTIVE, ObjectiveCriteria.DUMMY,
                Component.translatableWithFallback("taczvr.shop.points", "Points").withStyle(ChatFormatting.GOLD), ObjectiveCriteria.RenderType.INTEGER);
        scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, objective);
        for (ServerPlayer player : players) {
            add(player, 0);
        }
    }

    public static void add(ServerPlayer player, int amount) {
        int now = POINTS.getOrDefault(player.getUUID(), 0) + amount;
        POINTS.put(player.getUUID(), now);
        Scoreboard scoreboard = player.getScoreboard();
        Objective objective = scoreboard.getObjective(OBJECTIVE);
        if (objective != null) {
            scoreboard.getOrCreatePlayerScore(player.getScoreboardName(), objective).setScore(now);
        }
        if (open) {
            sendState(player, true, 0);
        }
    }

    /**
     * The break between waves: the shop opens for everyone still in the round.
     */
    public static void openFor(List<ServerPlayer> players, int ticks) {
        open = true;
        READY.clear();
        for (ServerPlayer player : players) {
            sendState(player, true, ticks);
        }
    }

    public static void close(List<ServerPlayer> players) {
        open = false;
        READY.clear();
        for (ServerPlayer player : players) {
            sendState(player, false, 0);
        }
    }

    private static void sendState(ServerPlayer player, boolean isOpen, int ticks) {
        if (!(player instanceof net.minecraftforge.common.util.FakePlayer) && player.connection != null
                && Net.CHANNEL.isRemotePresent(player.connection.connection)) {
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShopStatePacket(isOpen, points(player), ticks / 20,
                    READY.contains(player.getUUID())));
        }
    }

    /**
     * @return true if everyone who can shop said they're ready
     */
    public static boolean ready(ServerPlayer player, List<ServerPlayer> shoppers) {
        if (!open) {
            return false;
        }
        READY.add(player.getUUID());
        sendState(player, true, 0);
        return shoppers.stream().allMatch(p -> READY.contains(p.getUUID()));
    }

    /**
     * Buys offer {@code index}. The item is marked so the end of the round can take it back.
     */
    public static boolean buy(ServerPlayer player, int index) {
        if (!open || index < 0 || index >= OFFERS.size()) {
            return false;
        }
        Offer offer = OFFERS.get(index);
        int have = points(player);
        if (have < offer.price) {
            player.displayClientMessage(Component.translatableWithFallback("taczvr.shop.poor", "Not enough points")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ItemStack stack = offer.make.apply(player);
        if (stack.isEmpty()) {
            player.displayClientMessage(Component.translatableWithFallback("taczvr.shop.no_gun", "Hold the gun you want ammo for")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        stack.getOrCreateTag().putBoolean(BOUGHT_TAG, true);
        add(player, -offer.price);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.inventoryMenu.broadcastChanges();
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.2F);
        purchases++;
        return true;
    }

    public static boolean isBought(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(BOUGHT_TAG);
    }

    /**
     * The end of the round takes back what was bought: from the inventory and from the ground. Your own attachments
     * on a bought gun come back to you.
     */
    public static void takeBack(List<ServerPlayer> players, @Nullable ServerLevel level) {
        for (ServerPlayer player : players) {
            List<ItemStack> returned = new ArrayList<>();
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (!isBought(stack)) {
                    continue;
                }
                IGun iGun = IGun.getIGunOrNull(stack);
                if (iGun != null) {
                    for (AttachmentType type : AttachmentType.values()) {
                        if (type == AttachmentType.NONE) {
                            continue;
                        }
                        ItemStack attachment = iGun.getAttachment(stack, type);
                        if (!attachment.isEmpty() && !isBought(attachment)) {
                            returned.add(attachment.copy());
                        }
                    }
                }
                inventory.setItem(slot, ItemStack.EMPTY);
            }
            for (ItemStack attachment : returned) {
                if (!inventory.add(attachment)) {
                    player.drop(attachment, false);
                }
            }
            player.inventoryMenu.broadcastChanges();
        }
        if (level != null) {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, players.isEmpty() ? null
                    : players.get(0).getBoundingBox().inflate(256.0), item -> isBought(item.getItem()))) {
                item.discard();
            }
        }
    }

    public static void end(List<ServerPlayer> players, @Nullable ServerLevel level) {
        close(players);
        takeBack(players, level);
        if (!players.isEmpty()) {
            Scoreboard scoreboard = players.get(0).getScoreboard();
            Objective objective = scoreboard.getObjective(OBJECTIVE);
            if (objective != null) {
                scoreboard.removeObjective(objective);
            }
            if (sidebarBefore != null && scoreboard.getObjective(sidebarBefore.getName()) == sidebarBefore) {
                scoreboard.setDisplayObjective(Scoreboard.DISPLAY_SLOT_SIDEBAR, sidebarBefore);
            }
        }
        sidebarBefore = null;
        POINTS.clear();
        READY.clear();
    }
}
