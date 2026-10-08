package com.taczvr.compat;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Minecraft 1.19.3's {@code Button.builder}, for 1.19.2 where buttons are made with their constructor.
 */
public final class Buttons {
    private final Component message;
    private final Button.OnPress onPress;
    private int x;
    private int y;
    private int width = 150;
    private int height = 20;

    private Buttons(Component message, Button.OnPress onPress) {
        this.message = message;
        this.onPress = onPress;
    }

    public static Buttons builder(Component message, Button.OnPress onPress) {
        return new Buttons(message, onPress);
    }

    public Buttons bounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        return this;
    }

    public Buttons pos(int x, int y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Buttons width(int width) {
        this.width = width;
        return this;
    }

    public Button build() {
        return new Button(this.x, this.y, this.width, this.height, this.message, this.onPress);
    }
}
