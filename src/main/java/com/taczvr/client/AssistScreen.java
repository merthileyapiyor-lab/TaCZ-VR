package com.taczvr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.compat.Buttons;
import com.taczvr.compat.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The assist menu, opened from the pause screen.
 */
public final class AssistScreen extends Screen {
    private final Screen parent;

    AssistScreen(Screen parent) {
        super(Component.translatable("taczvr.assist.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 4 + 24;
        this.addRenderableWidget(CycleButton.onOffBuilder(ClientAssist.aimAssist())
                .create(x, y, 200, 20, Component.translatable("taczvr.assist.aim"),
                        (button, value) -> ClientAssist.setAimAssist(value)));
        this.addRenderableWidget(CycleButton.onOffBuilder(ClientAssist.infiniteAmmo())
                .create(x, y + 24, 200, 20, Component.translatable("taczvr.assist.ammo"),
                        (button, value) -> ClientAssist.setInfiniteAmmo(value)));
        this.addRenderableWidget(CycleButton.onOffBuilder(ClientAssist.glow())
                .create(x, y + 48, 200, 20, Component.translatable("taczvr.assist.glow"),
                        (button, value) -> ClientAssist.setGlow(value)));
        this.addRenderableWidget(Buttons.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(x, y + 84, 200, 20).build());
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        GuiGraphics graphics = new GuiGraphics(poseStack);
        this.renderBackground(poseStack);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4, 0xFFFFFF);
        super.render(poseStack, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
