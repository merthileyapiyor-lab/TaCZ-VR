package com.taczvr.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.taczvr.TaczVR;
import com.taczvr.content.NightVisionItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

/**
 * What flashbangs and goggles do to your picture: a white flash that fades (over the whole screen flat, over each
 * eye's view in VR), a green tint while night vision goggles are on, and {@link BloodVision}.
 */
public final class ScreenEffects {
    private static long flashStart = -1000L;
    private static float flashPower = 0.0F;
    static int flashesSeen = 0;
    static int flashFramesVr = 0;
    static int flashFramesFlat = 0;
    static int nightVisionFrames = 0;

    private ScreenEffects() {
    }

    public static void flash(float strength) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        // a second flash only makes it worse
        float left = flashAlpha(0.0F);
        flashPower = Math.min(1.0F, Math.max(left, strength));
        flashStart = mc.level.getGameTime();
        flashesSeen++;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 2.0F, 0.5F * strength));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.9F, 0.35F * strength));
    }

    /**
     * How white the picture is now: fully for a moment, longer the stronger the flash, then it fades.
     */
    static float flashAlpha(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || flashPower <= 0.0F) {
            return 0.0F;
        }
        float t = mc.level.getGameTime() - flashStart + partialTick;
        float hold = 15.0F + 45.0F * flashPower;
        float fade = 70.0F;
        if (t < 0.0F || t > hold + fade) {
            return 0.0F;
        }
        return t < hold ? flashPower : flashPower * (1.0F - (t - hold) / fade);
    }

    static boolean nightVisionOn() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && NightVisionItem.isOn(mc.player.getItemBySlot(EquipmentSlot.HEAD));
    }

    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(TaczVR.MOD_ID + "_flash", ScreenEffects::renderGui);
    }

    private static void renderGui(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        float alpha = VrClient.isVRActive() ? 0.0F : flashAlpha(partialTick);
        if (alpha > 0.0F) {
            graphics.fill(0, 0, width, height, ((int) (alpha * 255.0F) << 24) | 0xFFFFFF);
            flashFramesFlat++;
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        // blood vision brings its own red picture, the green steps back meanwhile so it doesn't turn black
        float green = 1.0F - BloodVision.strength();
        if (nightVisionOn() && green > 0.0F) {
            // multiplied in, so it stays a picture and not a green fog
            RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
            fullscreen(1.0F - 0.55F * green, 1.0F, 1.0F - 0.5F * green, 1.0F);
            RenderSystem.defaultBlendFunc();
            nightVisionFrames++;
        }
        BloodVision.render(event);
        float alpha = VrClient.isVRActive() ? flashAlpha(event.getPartialTick()) : 0.0F;
        if (alpha > 0.0F) {
            RenderSystem.defaultBlendFunc();
            fullscreen(1.0F, 1.0F, 1.0F, alpha);
            flashFramesVr++;
        }
    }

    /**
     * A quad over the whole view being drawn, each eye in VR, blended with whatever blend function is set.
     */
    static void fullscreen(float r, float g, float b, float a) {
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorting sorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.vertex(-1.0F, -1.0F, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(1.0F, -1.0F, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(1.0F, 1.0F, 0.0F).color(r, g, b, a).endVertex();
        buffer.vertex(-1.0F, 1.0F, 0.0F).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(projection, sorting);
    }
}
