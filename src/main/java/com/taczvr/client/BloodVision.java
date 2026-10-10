package com.taczvr.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.taczvr.VrCommon;
import com.taczvr.content.BloodVisionItem;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/**
 * Blood vision, from the blood vision goggles: while they're on your head and you hold your gun hand at that side of
 * your head (in VR) or hold the key, the world turns dark red and every living thing within {@link #RANGE} blocks
 * glows red, walls or not. Pull the hand away and it fades out.
 * <p>
 * The look follows Vampirism's blood vision. The code is our own: the creatures are drawn again over everything as
 * red shapes, in each eye's view, so it works in VR and doesn't need Minecraft's glowing outline.
 */
public final class BloodVision {
    public static final KeyMapping KEY = new KeyMapping("key.taczvr.blood_vision", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K, "key.categories.taczvr");
    static final double RANGE = 40.0;
    // the hand's spot: beside the eyes on the gun hand's side, in meters
    private static final double SIDE = 0.12;
    // close enough to switch on, and far enough to switch off again, so it doesn't flicker at the edge
    private static final double ON_WITHIN = 0.17;
    private static final double OFF_BEYOND = 0.23;
    // how far in front of the eyes the hand may be, the hand aiming down a scope is further out
    private static final double AHEAD_ON = 0.08;
    private static final double AHEAD_OFF = 0.14;
    private static final float FADE_IN = 0.25F;
    private static final float FADE_OUT = 0.34F;

    private static final BufferBuilder BUFFER = new BufferBuilder(1 << 16);
    // the view of the pass being rendered (each eye in VR), taken where the entities are drawn: the pose stack
    // Forge hands out after the level holds the projection instead
    private static final Matrix4f VIEW = new Matrix4f();
    private static boolean handAtHead = false;
    private static boolean active = false;
    private static float strength = 0.0F;
    private static float lastStrength = 0.0F;
    // set while we draw the creatures, their name tags would come out as red blocks
    private static boolean drawing = false;
    static int activations = 0;
    static int framesDrawn = 0;
    static int creaturesDrawn = 0;

    private BloodVision() {
    }

    static boolean active() {
        return active;
    }

    static float strength() {
        return strength;
    }

    /**
     * Whether the gun hand is at the side of the head, on the gun hand's side, and not out in front of the face, where
     * it is while aiming down a scope. Further out than when it came in counts as still there.
     */
    static boolean handAtHead(VrPose pose, boolean wasThere) {
        VrPart head = pose.getHead();
        VrPart hand = pose.getMainHand();
        if (head == null || hand == null) {
            return false;
        }
        float scale = VrClient.localWorldScale();
        Vector3f side = head.getRotation().transform(new Vector3f(pose.isLeftHanded() ? -1.0F : 1.0F, 0.0F, 0.0F));
        Vec3 spot = head.getPos().add(side.x * SIDE * scale, side.y * SIDE * scale, side.z * SIDE * scale);
        double distance = hand.getPos().distanceTo(spot) / scale;
        double ahead = hand.getPos().subtract(head.getPos()).dot(head.getDir()) / scale;
        return wasThere ? distance < OFF_BEYOND && ahead < AHEAD_OFF : distance < ON_WITHIN && ahead < AHEAD_ON;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        lastStrength = strength;
        boolean want = false;
        if (mc.player != null && BloodVisionItem.isWorn(mc.player)) {
            VrPose pose = VrClient.isVRActive() ? VrClient.localTickPose() : null;
            handAtHead = pose != null && handAtHead(pose, handAtHead);
            want = handAtHead || KEY.isDown();
        } else {
            handAtHead = false;
        }
        if (want && !active) {
            activations++;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.6F));
            VrClient.haptic(VrHand.MAIN_HAND, 0.06F, 0.6F);
        }
        active = want;
        strength = Mth.clamp(strength + (want ? FADE_IN : -FADE_OUT), 0.0F, 1.0F);
    }

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            VIEW.set(event.getPoseStack().last().pose());
        }
    }

    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (drawing) {
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * Draws it into the view being rendered, each eye in VR. Called by {@link ScreenEffects} after the level.
     */
    static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        float s = Mth.lerp(event.getPartialTick(), lastStrength, strength);
        if (s <= 0.0F || mc.level == null || mc.player == null) {
            return;
        }
        // darker and redder, multiplied in so the world stays a picture
        RenderSystem.blendFunc(GlStateManager.SourceFactor.DST_COLOR, GlStateManager.DestFactor.ZERO);
        ScreenEffects.fullscreen(1.0F - 0.45F * s, 1.0F - 0.8F * s, 1.0F - 0.8F * s, 1.0F);
        RenderSystem.defaultBlendFunc();
        drawCreatures(mc, event, s);
        framesDrawn++;
    }

    static boolean shows(Minecraft mc, Entity entity) {
        return entity instanceof LivingEntity living && living.isAlive() && entity != mc.player
                && entity != mc.getCameraEntity() && !entity.isSpectator() && !(entity instanceof ArmorStand);
    }

    private static void drawCreatures(Minecraft mc, RenderLevelStageEvent event, float s) {
        Vec3 cam = event.getCamera().getPosition();
        float partialTick = event.getPartialTick();
        PoseStack poseStack = new PoseStack();
        poseStack.last().pose().set(VIEW);
        Shapes shapes = new Shapes(BUFFER);
        BUFFER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        drawing = true;
        try {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!shows(mc, entity)) {
                    continue;
                }
                Vec3 at = entity.getPosition(partialTick);
                double distance = at.distanceTo(cam);
                if (distance > RANGE) {
                    continue;
                }
                // close ones bright, far ones fainter
                float near = distance < 8.0 ? 1.0F : 1.0F - 0.6F * (float) ((distance - 8.0) / (RANGE - 8.0));
                shapes.alpha = 0.35F * s * near;
                EntityRenderer<? super Entity> renderer = mc.getEntityRenderDispatcher().getRenderer(entity);
                Vec3 offset = renderer.getRenderOffset(entity, partialTick);
                poseStack.pushPose();
                poseStack.translate(at.x - cam.x + offset.x, at.y - cam.y + offset.y, at.z - cam.z + offset.z);
                renderer.render(entity, Mth.lerp(partialTick, entity.yRotO, entity.getYRot()), partialTick, poseStack,
                        shapes, LightTexture.FULL_BRIGHT);
                poseStack.popPose();
                creaturesDrawn++;
            }
        } catch (Throwable t) {
            VrCommon.logOnce(t);
        } finally {
            drawing = false;
        }
        BufferBuilder.RenderedBuffer shapesDrawn = BUFFER.endOrDiscardIfEmpty();
        if (shapesDrawn == null) {
            return;
        }
        // over everything, walls or not, glowing: each layer adds light
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(shapesDrawn);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * Takes whatever an entity renderer draws and keeps only the shape, in blood red. Only quads, other kinds of
     * drawing (lines, strips) are dropped.
     */
    private static final class Shapes implements MultiBufferSource, VertexConsumer {
        private static final VertexConsumer DISCARD = new Discard();
        private final VertexConsumer out;
        float alpha = 0.35F;

        Shapes(VertexConsumer out) {
            this.out = out;
        }

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            return type.mode() == VertexFormat.Mode.QUADS ? this : DISCARD;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            this.out.vertex(x, y, z).color(0.9F, 0.04F, 0.02F, this.alpha);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
            this.out.endVertex();
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }

    private static final class Discard implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
