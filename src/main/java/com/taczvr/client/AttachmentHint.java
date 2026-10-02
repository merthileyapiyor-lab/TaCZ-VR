package com.taczvr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.TaczVRConfig;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.compat.oculus.OculusCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import com.taczvr.vr.VrPass;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * In VR there's no tooltip to tell which of your guns an attachment fits. Holding one, a small label floats above
 * the hand: whether it fits the gun in your other hand, and which guns in your inventory take it.
 */
public final class AttachmentHint {
    private static final int MAX_GUNS = 4;
    // a line is about 2 cm tall, readable with the hand in front of you
    private static final float TEXT_SCALE = 0.002F;
    private static final Map<InteractionHand, List<Component>> LINES = new EnumMap<>(InteractionHand.class);
    static int drawn = 0;

    private AttachmentHint() {
    }

    /**
     * The label for the attachment in {@code hand}, empty when it holds none.
     */
    static List<Component> lines(InteractionHand hand) {
        return LINES.getOrDefault(hand, List.of());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        LINES.clear();
        if (player == null || !VrClient.isVRActive() || !TaczVRConfig.CLIENT.attachmentHints.get()) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack attachment = player.getItemInHand(hand);
            if (IAttachment.getIAttachmentOrNull(attachment) != null) {
                ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
                LINES.put(hand, describe(player.getInventory(), attachment, other));
            }
        }
    }

    static List<Component> describe(Inventory inventory, ItemStack attachment, ItemStack otherHand) {
        List<Component> lines = new ArrayList<>();
        IGun held = IGun.getIGunOrNull(otherHand);
        if (held != null) {
            lines.add(held.allowAttachment(otherHand, attachment)
                    ? Component.translatableWithFallback("taczvr.hint.held_fits", "Fits the gun in your hand").withStyle(ChatFormatting.GREEN)
                    : Component.translatableWithFallback("taczvr.hint.held_no", "Doesn't fit the gun in your hand").withStyle(ChatFormatting.RED));
        }
        List<Component> guns = new ArrayList<>();
        int more = 0;
        for (int slot = 0; slot < inventory.items.size(); slot++) {
            ItemStack stack = inventory.items.get(slot);
            IGun iGun = IGun.getIGunOrNull(stack);
            if (iGun == null || stack == otherHand || !iGun.allowAttachment(stack, attachment)) {
                continue;
            }
            if (guns.size() >= MAX_GUNS) {
                more++;
                continue;
            }
            // hotbar slots by their number, the rest is in the bag
            Component where = slot < Inventory.getSelectionSize()
                    ? Component.literal(String.valueOf(slot + 1))
                    : Component.translatableWithFallback("taczvr.hint.bag", "bag");
            guns.add(Component.empty().append(stack.getHoverName()).append(Component.literal(" (").append(where).append(")")
                    .withStyle(ChatFormatting.GRAY)));
        }
        if (!guns.isEmpty()) {
            lines.add(Component.translatableWithFallback("taczvr.hint.fits", "Fits:").withStyle(ChatFormatting.GOLD));
            lines.addAll(guns);
            if (more > 0) {
                lines.add(Component.translatableWithFallback("taczvr.hint.more", "+%s more", more).withStyle(ChatFormatting.GRAY));
            }
        } else if (held == null || !held.allowAttachment(otherHand, attachment)) {
            lines.add(Component.translatableWithFallback("taczvr.hint.none", "Fits none of your guns").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || LINES.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        VrPass pass = VrClient.currentPass();
        if (player == null || OculusCompat.isRenderShadow() || pass != null && !pass.isFirstPerson()) {
            return;
        }
        VrPose pose = VrClient.renderPose(player);
        if (pose == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Font font = mc.font;
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        for (Map.Entry<InteractionHand, List<Component>> entry : LINES.entrySet()) {
            VrPart hand = pose.getHand(entry.getKey());
            List<Component> lines = entry.getValue();
            if (hand == null || lines.isEmpty()) {
                continue;
            }
            // a bit above the hand, the lines stacked upwards from there
            Vec3 at = hand.getPos().add(0.0, 0.1 + lines.size() * 10 * TEXT_SCALE, 0.0);
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            poseStack.mulPose(event.getCamera().rotation());
            poseStack.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);
            Matrix4f matrix = poseStack.last().pose();
            for (int i = 0; i < lines.size(); i++) {
                Component line = lines.get(i);
                float x = -font.width(line) / 2.0F;
                font.drawInBatch(line, x, i * 10.0F, 0xFFFFFFFF, false, matrix, buffers, Font.DisplayMode.NORMAL, 0xB0000000,
                        LightTexture.FULL_BRIGHT);
            }
            poseStack.popPose();
            drawn++;
        }
        buffers.endBatch();
    }
}
