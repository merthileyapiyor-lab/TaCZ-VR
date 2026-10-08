package com.taczvr.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The part of Minecraft 1.20's {@code GuiGraphics} the mod's screens use, drawn the 1.19.2 way through a PoseStack.
 */
public final class GuiGraphics {
    private final PoseStack poseStack;

    public GuiGraphics(PoseStack poseStack) {
        this.poseStack = poseStack;
    }

    public PoseStack pose() {
        return this.poseStack;
    }

    public void fill(int x0, int y0, int x1, int y1, int color) {
        GuiComponent.fill(this.poseStack, x0, y0, x1, y1, color);
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        GuiComponent.drawCenteredString(this.poseStack, font, text, x, y, color);
    }

    public void drawCenteredString(Font font, FormattedCharSequence text, int x, int y, int color) {
        GuiComponent.drawCenteredString(this.poseStack, font, text, x, y, color);
    }

    public void drawCenteredString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawCenteredString(this.poseStack, font, text, x, y, color);
    }

    public void drawString(Font font, Component text, int x, int y, int color) {
        GuiComponent.drawString(this.poseStack, font, text, x, y, color);
    }

    public void drawString(Font font, FormattedCharSequence text, int x, int y, int color) {
        GuiComponent.drawString(this.poseStack, font, text, x, y, color);
    }

    public void drawString(Font font, String text, int x, int y, int color) {
        GuiComponent.drawString(this.poseStack, font, text, x, y, color);
    }

    /**
     * The item at its GUI size; like 1.19.2's item renderer it ignores the PoseStack.
     */
    public void renderItem(ItemStack stack, int x, int y) {
        Minecraft.getInstance().getItemRenderer().renderGuiItem(stack, x, y);
    }
}
