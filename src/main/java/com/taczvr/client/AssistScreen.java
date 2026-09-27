package com.taczvr.client;

import net.minecraft.client.gui.GuiGraphics;
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
        super(Component.translatableWithFallback("taczvr.assist.title", "Assist"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 4 + 24;
        this.addRenderableWidget(CycleButton.onOffBuilder(ClientAssist.aimAssist())
                .create(x, y, 200, 20, Component.translatableWithFallback("taczvr.assist.aim", "Aim assist"),
                        (button, value) -> ClientAssist.setAimAssist(value)));
        this.addRenderableWidget(CycleButton.onOffBuilder(ClientAssist.infiniteAmmo())
                .create(x, y + 24, 200, 20, Component.translatableWithFallback("taczvr.assist.ammo", "Infinite ammo"),
                        (button, value) -> ClientAssist.setInfiniteAmmo(value)));
        this.addRenderableWidget(CycleButton.onOffBuilder(ClientAssist.glow())
                .create(x, y + 48, 200, 20, Component.translatableWithFallback("taczvr.assist.glow", "Glowing targets"),
                        (button, value) -> ClientAssist.setGlow(value)));
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .bounds(x, y + 84, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
