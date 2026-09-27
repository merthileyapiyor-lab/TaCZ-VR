package com.taczvr.client;

import com.taczvr.network.GameCommandPacket;
import com.taczvr.network.Net;
import com.taczvr.server.GameManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * The game menu in the pause screen, for ops and whoever hosts the world: start a round (first to fall loses, last
 * one standing, team match, three lives, zombie waves; nobody really dies) or stop it.
 */
public final class GameMenu {
    private GameMenu() {
    }

    static boolean canControl(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null) {
            return false;
        }
        IntegratedServer server = mc.getSingleplayerServer();
        return player.hasPermissions(2) || server != null && server.isSingleplayerOwner(player.getGameProfile());
    }

    private static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && Net.CHANNEL.isRemotePresent(connection.getConnection());
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getScreen() instanceof PauseScreen pause && canControl(mc) && serverHasMod()) {
            event.addListener(Button.builder(Component.translatableWithFallback("taczvr.game.button", "Game"),
                    button -> mc.setScreen(new GameScreen(pause))).bounds(8, 32, 110, 20).build());
        }
    }

    public static final class GameScreen extends Screen {
        private final Screen parent;

        GameScreen(Screen parent) {
            super(Component.translatableWithFallback("taczvr.game.title", "Game"));
            this.parent = parent;
        }

        @Override
        protected void init() {
            int left = this.width / 2 - 152;
            int right = this.width / 2 + 2;
            int y = this.height / 4 + 36;
            mode(left, y, "taczvr.game.start", "First to fall loses", GameManager.FIRST_FALL);
            mode(right, y, "taczvr.game.start.last", "Last one standing", GameManager.LAST_STANDING);
            mode(left, y + 24, "taczvr.game.start.teams", "Team match", GameManager.TEAMS);
            mode(right, y + 24, "taczvr.game.start.lives", "Three lives", GameManager.LIVES);
            mode(left, y + 48, "taczvr.game.start.zombies", "Zombie waves", GameManager.ZOMBIES);
            this.addRenderableWidget(Button.builder(Component.translatableWithFallback("taczvr.game.stop", "Stop the round"),
                    button -> Net.CHANNEL.sendToServer(new GameCommandPacket(GameManager.STOP))).bounds(right, y + 48, 150, 20).build());
            this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                    .bounds(this.width / 2 - 100, y + 80, 200, 20).build());
        }

        private void mode(int x, int y, String key, String fallback, int mode) {
            this.addRenderableWidget(Button.builder(Component.translatableWithFallback(key, fallback), button -> start(mode))
                    .bounds(x, y, 150, 20).build());
        }

        private void start(int mode) {
            Net.CHANNEL.sendToServer(new GameCommandPacket(mode));
            this.minecraft.setScreen(null);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4, 0xFFFFFF);
            graphics.drawCenteredString(this.font, Component.translatableWithFallback("taczvr.game.info",
                    "Everyone fights, nobody dies: at 0 health the round ends and nothing is lost"),
                    this.width / 2, this.height / 4 + 16, 0xA0A0A0);
            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(this.parent);
        }
    }
}
