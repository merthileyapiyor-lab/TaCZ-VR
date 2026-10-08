package com.taczvr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.compat.Buttons;
import com.taczvr.TaczVR;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import com.taczvr.compat.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * "Update TaCZ VR": says which version is out, with Later and Quit Game. Quit Game installs it and closes the game.
 */
public final class UpdateScreen extends Screen {
    private static final int MAX_NOTE_LINES = 8;
    // the self-test swaps these, so it can install into a test folder and not really quit
    static Runnable stopGame = () -> Minecraft.getInstance().stop();
    @Nullable
    static Path testJar = null;

    private final Screen parent;
    private final UpdateChecker.Release release;
    private final String ours;
    private Button later;
    private Button quit;
    private volatile int percent = -1;
    @Nullable
    private volatile Component error = null;
    private volatile boolean installed = false;

    UpdateScreen(Screen parent, UpdateChecker.Release release, String ours) {
        super(Component.translatable("taczvr.update.title"));
        this.parent = parent;
        this.release = release;
        this.ours = ours;
    }

    @Override
    protected void init() {
        int y = this.height - 40;
        this.later = this.addRenderableWidget(Buttons.builder(Component.translatable("taczvr.update.later"), b -> this.onClose())
                .bounds(this.width / 2 - 154, y, 150, 20).build());
        this.quit = this.addRenderableWidget(Buttons.builder(Component.translatable("taczvr.update.quit"), b -> this.install())
                .bounds(this.width / 2 + 4, y, 150, 20).build());
        this.updateButtons();
    }

    private void install() {
        Path jar = testJar != null ? testJar : UpdateChecker.currentJar();
        if (jar == null) {
            this.error = Component.translatable("taczvr.update.no_jar");
            return;
        }
        this.error = null;
        this.percent = 0;
        this.updateButtons();
        CompletableFuture.runAsync(() -> {
            try {
                boolean now = UpdateChecker.install(this.release, jar, p -> this.percent = p);
                TaczVR.LOGGER.info("Update: {} downloaded, {}", this.release.jarName(), now ? "swapped in" : "swapped in once the game has closed");
                this.installed = true;
                Minecraft.getInstance().execute(stopGame);
            } catch (Exception e) {
                TaczVR.LOGGER.warn("Update failed", e);
                this.error = Component.translatable("taczvr.update.failed", String.valueOf(e.getMessage()));
                this.percent = -1;
                Minecraft.getInstance().execute(this::updateButtons);
            }
        });
    }

    private void updateButtons() {
        boolean busy = this.percent >= 0;
        this.later.active = !busy;
        this.quit.active = !busy;
    }

    @Override
    public void tick() {
        if (this.percent >= 0 && !this.installed) {
            this.quit.setMessage(Component.translatable("taczvr.update.downloading", this.percent));
        } else if (this.percent < 0) {
            this.quit.setMessage(Component.translatable("taczvr.update.quit"));
        }
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        GuiGraphics graphics = new GuiGraphics(poseStack);
        this.renderBackground(poseStack);
        int y = 30;
        graphics.pose().pushPose();
        graphics.pose().translate(this.width / 2.0F, y, 0.0F);
        graphics.pose().scale(2.0F, 2.0F, 1.0F);
        graphics.drawCenteredString(this.font, this.title, 0, 0, 0xFFD24A);
        graphics.pose().popPose();
        y += 30;
        graphics.drawCenteredString(this.font, Component.translatable("taczvr.update.new",
                Component.literal(this.release.version()).withStyle(ChatFormatting.GREEN), this.ours), this.width / 2, y, 0xFFFFFF);
        y += 18;
        for (FormattedCharSequence line : this.noteLines()) {
            graphics.drawString(this.font, line, this.width / 2 - 150, y, 0xBBBBBB);
            y += 10;
        }
        y += 8;
        for (FormattedCharSequence line : this.font.split(Component.translatable("taczvr.update.how"), 300)) {
            graphics.drawCenteredString(this.font, line, this.width / 2, y, 0x9A9A9A);
            y += 10;
        }
        Component failed = this.error;
        if (failed != null) {
            y += 6;
            for (FormattedCharSequence line : this.font.split(failed.copy().withStyle(ChatFormatting.RED), 300)) {
                graphics.drawCenteredString(this.font, line, this.width / 2, y, 0xFF5555);
                y += 10;
            }
        }
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    /**
     * The release notes from GitHub, without markdown marks, cut to a few lines.
     */
    List<FormattedCharSequence> noteLines() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String raw : this.release.notes().replace("\r", "").split("\n")) {
            String text = raw.replaceAll("^#+\\s*", "").replaceAll("^[-*]\\s+", "• ").replace("**", "").replace("`", "").strip();
            if (text.isEmpty()) {
                continue;
            }
            for (FormattedCharSequence line : this.font.split(Component.literal(text), 300)) {
                if (lines.size() == MAX_NOTE_LINES) {
                    return lines;
                }
                lines.add(line);
            }
        }
        return lines;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return this.percent < 0;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    boolean busy() {
        return this.percent >= 0;
    }

    @Nullable
    Component error() {
        return this.error;
    }

    Button laterButton() {
        return this.later;
    }

    Button quitButton() {
        return this.quit;
    }
}
