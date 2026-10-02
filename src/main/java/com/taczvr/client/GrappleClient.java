package com.taczvr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.taczvr.content.GrappleEntity;
import com.taczvr.content.ModContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

/**
 * The grappling hook on the client: pulls you to your hook once it holds, and draws hook and rope.
 */
public final class GrappleClient {
    private static final double HANG_DISTANCE = 1.6;
    // where your own hook holds, as the server told it: the hook itself may be too far away to be sent to you
    @Nullable
    private static Vec3 anchor = null;
    private static InteractionHand anchorHand = InteractionHand.MAIN_HAND;
    static int pulls = 0;
    static int ropesDrawn = 0;

    private GrappleClient() {
    }

    public static void anchor(@Nullable Vec3 at, boolean offHand) {
        anchor = at;
        anchorHand = offHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Nullable
    static Vec3 anchor() {
        return anchor;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (player == null || mc.level == null) {
            anchor = null;
            return;
        }
        if (anchor == null) {
            return;
        }
        Vec3 to = anchor.subtract(player.position().add(0.0, player.getBbHeight() * 0.5, 0.0));
        double distance = to.length();
        // faster the further away, then you hang there
        player.setDeltaMovement(distance > HANG_DISTANCE ? to.scale(Math.min(1.6, 0.45 + distance * 0.05) / distance) : Vec3.ZERO);
        player.resetFallDistance();
        pulls++;
    }

    /**
     * Your own hook and rope once it holds, drawn from the spot the server sent.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || anchor == null || mc.player == null || mc.level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        poseStack.pushPose();
        poseStack.translate(anchor.x - cam.x, anchor.y - cam.y, anchor.z - cam.z);
        Vec3 hand = handPosition(mc.player, anchorHand, event.getPartialTick());
        drawHookAndRope(poseStack, buffers, mc.getItemRenderer(), anchor, hand, cam, Vec3.ZERO, true,
                LevelRenderer.getLightColor(mc.level, BlockPos.containing(anchor)), mc.level, mc.player.getId());
        poseStack.popPose();
        buffers.endBatch();
    }

    /**
     * The hook (prongs first: along the flight, or away from the hand once it holds) and the rope back to the hand.
     * The pose stack is at the hook.
     */
    static void drawHookAndRope(PoseStack poseStack, MultiBufferSource buffers, ItemRenderer items, Vec3 at, @Nullable Vec3 hand, Vec3 cam,
                                Vec3 flight, boolean anchored, int light, net.minecraft.world.level.Level level, int seed) {
        Vec3 facing = anchored && hand != null ? at.subtract(hand) : flight;
        poseStack.pushPose();
        if (facing.lengthSqr() > 1.0E-6) {
            Vec3 dir = facing.normalize();
            poseStack.mulPose(new Quaternionf().rotationTo(0.0F, -1.0F, 0.0F, (float) dir.x, (float) dir.y, (float) dir.z));
        }
        poseStack.scale(0.6F, 0.6F, 0.6F);
        items.renderStatic(new ItemStack(ModContent.GRAPPLING_HOOK.get()), ItemDisplayContext.NONE, light,
                OverlayTexture.NO_OVERLAY, poseStack, buffers, level, seed);
        poseStack.popPose();
        if (hand != null) {
            Vec3 rope = hand.subtract(at);
            Renderer.drawRope(poseStack, buffers.getBuffer(RenderType.leash()), rope, cam.subtract(at), light, anchored);
            // a line too, like a fishing line, so it shows from far away
            Renderer.drawLine(poseStack, buffers.getBuffer(RenderType.lineStrip()), rope, anchored);
            ropesDrawn++;
        }
    }

    /**
     * Where the rope starts: the tracked hand of a VR player, otherwise about where the hand holding it is.
     */
    static Vec3 handPosition(Player owner, InteractionHand hand, float partialTick) {
        VrPose pose = VrClient.renderPose(owner);
        VrPart part = pose == null ? null : pose.getHand(hand);
        if (part != null) {
            return part.getPos().add(VrClient.remoteInterpolationOffset(owner, partialTick));
        }
        boolean right = (owner.getMainArm() == HumanoidArm.RIGHT) == (hand == InteractionHand.MAIN_HAND);
        float yaw = Mth.lerp(partialTick, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x).scale(right ? -0.35 : 0.35);
        Minecraft mc = Minecraft.getInstance();
        if (owner == mc.player && mc.options.getCameraType().isFirstPerson()) {
            // from the bottom corner of your view
            Vec3 look = owner.getViewVector(partialTick);
            Vec3 lookSide = new Vec3(-look.z, 0.0, look.x).normalize().scale(right ? -0.35 : 0.35);
            return owner.getEyePosition(partialTick).add(look.scale(0.5)).add(lookSide).subtract(0.0, 0.3, 0.0);
        }
        return owner.getPosition(partialTick).add(0.0, owner.getBbHeight() * 0.55, 0.0).add(forward.scale(0.4)).add(side);
    }

    public static final class Renderer extends EntityRenderer<GrappleEntity> {
        private final ItemRenderer items;

        public Renderer(EntityRendererProvider.Context context) {
            super(context);
            this.items = context.getItemRenderer();
        }

        @Override
        public void render(GrappleEntity hook, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
            Player owner = hook.getOwner() instanceof Player player ? player : null;
            // your own hook that holds is drawn from the spot the server sent (onRenderLevel)
            if (owner == Minecraft.getInstance().player && hook.isAnchored() && anchor != null) {
                return;
            }
            Vec3 at = hook.getPosition(partialTick);
            Vec3 hand = owner == null ? null : handPosition(owner, hook.hand(), partialTick);
            drawHookAndRope(poseStack, buffers, this.items, at, hand, this.entityRenderDispatcher.camera.getPosition(), hook.getDeltaMovement(),
                    hook.isAnchored(), light, hook.level(), hook.getId());
            super.render(hook, yaw, partialTick, poseStack, buffers, light);
        }

        /**
         * A thin ribbon from the hook to the hand, turned to the camera, sagging while it's still flying out.
         */
        static void drawRope(PoseStack poseStack, VertexConsumer vc, Vec3 rope, Vec3 camera, int light, boolean tight) {
            Matrix4f matrix = poseStack.last().pose();
            int segments = 16;
            double sag = tight ? 0.0 : Math.min(1.5, rope.length() * 0.08);
            for (int i = 0; i <= segments; i++) {
                float t = (float) i / segments;
                Vec3 p = rope.scale(t).subtract(0.0, Math.sin(t * Math.PI) * sag, 0.0);
                Vec3 side = rope.cross(camera.subtract(p));
                side = side.lengthSqr() < 1.0E-8 ? new Vec3(0.02, 0.0, 0.0) : side.normalize().scale(0.02);
                float shade = i % 2 == 0 ? 0.55F : 0.45F;
                vc.vertex(matrix, (float) (p.x + side.x), (float) (p.y + side.y), (float) (p.z + side.z))
                        .color(shade, shade * 0.75F, shade * 0.5F, 1.0F).uv2(light).endVertex();
                vc.vertex(matrix, (float) (p.x - side.x), (float) (p.y - side.y), (float) (p.z - side.z))
                        .color(shade, shade * 0.75F, shade * 0.5F, 1.0F).uv2(light).endVertex();
            }
        }

        static void drawLine(PoseStack poseStack, VertexConsumer vc, Vec3 rope, boolean tight) {
            PoseStack.Pose pose = poseStack.last();
            int segments = 16;
            double sag = tight ? 0.0 : Math.min(1.5, rope.length() * 0.08);
            Vec3 last = Vec3.ZERO;
            for (int i = 0; i <= segments; i++) {
                float t = (float) i / segments;
                Vec3 p = rope.scale(t).subtract(0.0, Math.sin(t * Math.PI) * sag, 0.0);
                Vec3 normal = i == 0 ? rope.normalize() : p.subtract(last).normalize();
                vc.vertex(pose.pose(), (float) p.x, (float) p.y, (float) p.z).color(0.25F, 0.18F, 0.1F, 1.0F)
                        .normal(pose.normal(), (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
                last = p;
            }
        }

        @Override
        public ResourceLocation getTextureLocation(GrappleEntity hook) {
            return TextureAtlas.LOCATION_BLOCKS;
        }

        @Override
        public boolean shouldRender(GrappleEntity hook, net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
            // the rope can be on screen with the hook far off it
            Entity owner = hook.getOwner();
            return owner != null || super.shouldRender(hook, frustum, x, y, z);
        }
    }
}
