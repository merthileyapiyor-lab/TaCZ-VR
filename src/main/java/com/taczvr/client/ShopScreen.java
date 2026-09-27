package com.taczvr.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.taczvr.content.ModContent;
import com.taczvr.network.Net;
import com.taczvr.network.ShopActionPacket;
import com.taczvr.server.ZombieShop;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/**
 * The zombie shop between waves: what's for sale with its price, your points, the time left and a ready button.
 * Opens by itself when a break starts and closes when the next wave comes. J opens it again.
 */
public final class ShopScreen extends Screen {
    public static final KeyMapping KEY = new KeyMapping("key.taczvr.shop", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.taczvr");
    private static final int COLUMNS = 3;
    private static final int CELL_W = 141;
    private static final int BUTTON_W = 121;

    private static boolean open = false;
    private static int points = 0;
    private static long breakEnds = 0L;
    private static boolean ready = false;
    static int opened = 0;

    private ShopScreen() {
        super(Component.translatableWithFallback("taczvr.shop.title", "Shop"));
    }

    public static boolean isOpen() {
        return open;
    }

    public static int points() {
        return points;
    }

    public static void state(boolean isOpen, int newPoints, int seconds, boolean isReady) {
        Minecraft mc = Minecraft.getInstance();
        boolean wasOpen = open;
        open = isOpen;
        points = newPoints;
        ready = isReady;
        if (isOpen && seconds > 0 && mc.level != null) {
            breakEnds = mc.level.getGameTime() + seconds * 20L;
        }
        if (isOpen && !wasOpen && mc.screen == null) {
            mc.setScreen(new ShopScreen());
            opened++;
        } else if (!isOpen && mc.screen instanceof ShopScreen) {
            mc.setScreen(null);
        } else if (mc.screen instanceof ShopScreen screen) {
            screen.rebuildWidgets();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (mc.player == null) {
            // left the world
            com.taczvr.DownedState.CLIENT.clear();
            open = false;
            return;
        }
        while (KEY.consumeClick()) {
            if (open && mc.screen == null) {
                mc.setScreen(new ShopScreen());
            }
        }
    }

    static ItemStack icon(String key) {
        if ("ammo".equals(key)) {
            return new ItemStack(Items.IRON_NUGGET);
        }
        var ours = ForgeRegistries.ITEMS.getValue(new ResourceLocation("taczvr", key));
        if (ours != null && ours != Items.AIR) {
            return new ItemStack(ours);
        }
        return GunItemBuilder.create().setId(new ResourceLocation("tacz", key)).build();
    }

    static Component name(String key) {
        if ("ammo".equals(key)) {
            return Component.translatableWithFallback("taczvr.shop.ammo", "Ammo for your gun");
        }
        return icon(key).getHoverName();
    }

    @Override
    protected void init() {
        int left = this.width / 2 - COLUMNS * CELL_W / 2;
        int top = this.height / 2 - 72;
        for (int i = 0; i < ZombieShop.OFFERS.size(); i++) {
            ZombieShop.Offer offer = ZombieShop.OFFERS.get(i);
            int x = left + (i % COLUMNS) * CELL_W + 20;
            int y = top + (i / COLUMNS) * 24;
            int index = i;
            Button button = Button.builder(Component.empty().append(name(offer.key())).append(" ").append(
                                    Component.literal(String.valueOf(offer.price())).withStyle(ChatFormatting.GOLD)),
                            b -> Net.CHANNEL.sendToServer(new ShopActionPacket(index)))
                    .bounds(x, y, BUTTON_W, 20).build();
            button.active = points >= offer.price();
            this.addRenderableWidget(button);
        }
        int rows = (ZombieShop.OFFERS.size() + COLUMNS - 1) / COLUMNS;
        Button readyButton = Button.builder(ready
                        ? Component.translatableWithFallback("taczvr.shop.waiting", "Waiting for the others...")
                        : Component.translatableWithFallback("taczvr.shop.ready", "Ready, next wave"),
                b -> Net.CHANNEL.sendToServer(new ShopActionPacket(ShopActionPacket.READY)))
                .bounds(this.width / 2 - 100, top + rows * 24 + 6, 200, 20).build();
        readyButton.active = !ready;
        this.addRenderableWidget(readyButton);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int top = this.height / 2 - 72;
        long left = Minecraft.getInstance().level == null ? 0 : Math.max(0, (breakEnds - Minecraft.getInstance().level.getGameTime()) / 20);
        graphics.drawCenteredString(this.font, Component.translatableWithFallback("taczvr.shop.header", "Shop  |  %s points  |  next wave in %s s",
                points, left), this.width / 2, top - 22, 0xFFD24A);
        graphics.drawCenteredString(this.font, Component.translatableWithFallback("taczvr.shop.note", "What you buy is taken back when the round ends"),
                this.width / 2, top - 11, 0x9A9A9A);
        int columnsLeft = this.width / 2 - COLUMNS * CELL_W / 2;
        for (int i = 0; i < ZombieShop.OFFERS.size(); i++) {
            int x = columnsLeft + (i % COLUMNS) * CELL_W;
            int y = top + (i / COLUMNS) * 24;
            graphics.renderItem(icon(ZombieShop.OFFERS.get(i).key()), x, y + 2);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        // the round goes on while you shop
        return false;
    }
}
