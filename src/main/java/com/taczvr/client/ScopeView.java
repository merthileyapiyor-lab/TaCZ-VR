package com.taczvr.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.taczvr.TaczVRConfig;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.List;

/**
 * Working magnified scopes. While you look through a TACZ scope, the VR mod renders a zoomed picture from the scope's
 * eyepiece (with Vivecraft its spyglass pass, see VivecraftScope), with the field of view set from the scope's zoom.
 * The picture is then drawn over the eyepiece with a reticle.
 */
public final class ScopeView {
    // aim frame units (blocks at gun scale 1), used when the scope model has no eyepiece bone
    private static final float FALLBACK_LENS_DISTANCE = 0.25F;
    private static final int LENS_SEGMENTS = 32;

    private static boolean viewing = false;
    private static float fovDegrees = 10.0F;
    private static float lensDistance = FALLBACK_LENS_DISTANCE;

    private ScopeView() {
    }

    public static boolean isViewing() {
        return viewing;
    }

    public static float fovDegrees() {
        return fovDegrees;
    }

    public static float lensDistance() {
        return lensDistance;
    }

    static void stop() {
        viewing = false;
    }

    /**
     * Called every tick with the local player's gun.
     *
     * @param aiming the sights are at the eye (TACZ aim state)
     */
    static void update(ItemStack stack, IGun iGun, VrPose vrPose, GunPoseSolver.Pose gun, boolean aiming) {
        viewing = false;
        if (!aiming || !TaczVRConfig.CLIENT.scopes.get()) {
            return;
        }
        ResourceLocation scopeId = iGun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (scopeId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            scopeId = iGun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        }
        ClientAttachmentIndex index = TimelessAPI.getClientAttachmentIndex(scopeId).orElse(null);
        float zoom = iGun.getAimingZoom(stack);
        // red dots and holo sights don't magnify, look through them normally
        if (index == null || !index.isScope() || zoom < 1.2F) {
            return;
        }
        int zoomNumber = AttachmentItemDataAccessor.getZoomNumberFromTag(iGun.getAttachmentTag(stack, AttachmentType.SCOPE));
        lensDistance = eyepieceDistance(index, zoomNumber);

        VrPart head = vrPose.getHead();
        if (head == null) {
            return;
        }
        Vector3d lens = gun.toWorld(new Vector3f(0.0F, 0.0F, -lensDistance));
        Vec3 eye = head.getPos();
        double eyeDistance = Math.max(0.03, Math.min(0.4, lens.distance(eye.x, eye.y, eye.z)));
        // the eyepiece covers this much of your view, the scope shows 1/zoom of that
        double apparent = Math.toDegrees(2.0 * Math.atan(lensRadius(gun.scale) / eyeDistance));
        fovDegrees = (float) Math.max(0.5, Math.min(40.0, apparent / zoom));
        viewing = true;
    }

    private static float lensRadius(float gunScale) {
        return (float) (TaczVRConfig.CLIENT.scopeLensRadius.get() * gunScale / 0.3);
    }

    /**
     * How far the eyepiece sits in front of the scope's view point, in aim frame units.
     */
    static float eyepieceDistance(ClientAttachmentIndex index, int zoomNumber) {
        BedrockAttachmentModel model = index.getAttachmentModel();
        if (model == null) {
            return FALLBACK_LENS_DISTANCE;
        }
        int[] views = index.getViews();
        List<com.tacz.guns.client.model.bedrock.BedrockPart> viewPath = views == null || views.length == 0
                ? null : model.getScopeViewPath(views[zoomNumber % views.length] - 1);
        Vector3f ocular = GunPoseSolver.boneInModel(model, "ocular");
        if (viewPath == null || viewPath.isEmpty() || ocular == null) {
            return FALLBACK_LENS_DISTANCE;
        }
        PoseStack poseStack = new PoseStack();
        for (com.tacz.guns.client.model.bedrock.BedrockPart part : viewPath) {
            part.translateAndRotateAndScale(poseStack);
        }
        Vector3f view = poseStack.last().pose().getTranslation(new Vector3f());
        // the eyepiece is in front of (-Z) the view point. Its glass and ring can sit behind the bone's pivot (the
        // Contender pistol scope), the picture has to go behind all of it or the scope's own tube covers it
        float back = Math.max(backOf(model, "ocular"), backOf(model, "ocular_ring"));
        float distance = view.z - (Float.isNaN(back) ? ocular.z : Math.max(back, ocular.z));
        return distance > 0.01F && distance < 2.0F ? distance : FALLBACK_LENS_DISTANCE;
    }

    /**
     * How far back (+Z, towards the eye) the geometry of a bone reaches, in model units. NaN without the bone.
     */
    private static float backOf(BedrockAttachmentModel model, String boneName) {
        Vector3f pivot = GunPoseSolver.boneInModel(model, boneName);
        List<com.tacz.guns.client.model.bedrock.BedrockPart> path = GunPoseSolver.bonePath(model, boneName);
        if (pivot == null || path == null || path.isEmpty()) {
            return Float.NaN;
        }
        float back = backOfCubes(path.get(path.size() - 1), 0.0F);
        return Float.isNaN(back) ? Float.NaN : pivot.z + back;
    }

    private static float backOfCubes(com.tacz.guns.client.model.bedrock.BedrockPart part, float offset) {
        float back = Float.NaN;
        for (com.tacz.guns.client.model.bedrock.BedrockCube cube : part.cubes) {
            float maxZ;
            if (cube instanceof com.tacz.guns.client.model.bedrock.BedrockCubeBox box) {
                maxZ = box.maxZ;
            } else if (cube instanceof com.tacz.guns.client.model.bedrock.BedrockCubePerFace face) {
                maxZ = face.maxZ;
            } else {
                continue;
            }
            float z = offset + maxZ / 16.0F;
            back = Float.isNaN(back) ? z : Math.max(back, z);
        }
        // cubes with their own pivot are child parts
        for (com.tacz.guns.client.model.bedrock.BedrockPart child : part.children) {
            float z = backOfCubes(child, offset + child.z / 16.0F);
            if (!Float.isNaN(z)) {
                back = Float.isNaN(back) ? z : Math.max(back, z);
            }
        }
        return back;
    }

    /**
     * Draws the zoomed picture and a reticle over the eyepiece. Pose stack is the level render one.
     */
    static void drawEyepiece(PoseStack poseStack, Vec3 cam, GunPoseSolver.Pose gun) {
        int texture = VrClient.scopeTexture();
        if (texture >= 0) {
            drawEyepiece(poseStack, cam, gun, texture);
        }
    }

    /**
     * Draws a texture as the eyepiece picture, with the reticle on top.
     */
    static void drawEyepiece(PoseStack poseStack, Vec3 cam, GunPoseSolver.Pose gun, int textureId) {
        // slightly towards the eye so the scope's own glass doesn't cover it
        Vector3d center = gun.toWorld(new Vector3f(0.0F, 0.0F, -lensDistance + 0.02F));
        float radius = lensRadius(gun.scale);

        poseStack.pushPose();
        poseStack.translate(center.x - cam.x, center.y - cam.y, center.z - cam.z);
        poseStack.mulPose(gun.rotation);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, textureId);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX);
        buffer.vertex(matrix, 0.0F, 0.0F, 0.0F).uv(0.5F, 0.5F).endVertex();
        for (int i = 0; i <= LENS_SEGMENTS; i++) {
            double a = Math.PI * 2.0 * i / LENS_SEGMENTS;
            float x = (float) Math.cos(a);
            float y = (float) Math.sin(a);
            // render targets are stored bottom up, so v grows with y
            buffer.vertex(matrix, x * radius, y * radius, 0.0F).uv(0.5F + 0.5F * x, 0.5F + 0.5F * y).endVertex();
        }
        BufferUploader.drawWithShader(buffer.end());

        // simple crosshair reticle with a gap in the middle
        float w = radius * 0.012F;
        float gap = radius * 0.08F;
        float z = 0.0004F;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(buffer, matrix, -radius, -w, -gap, w, z);
        quad(buffer, matrix, gap, -w, radius, w, z);
        quad(buffer, matrix, -w, -radius, w, -gap, z);
        quad(buffer, matrix, -w, gap, w, radius, z);
        quad(buffer, matrix, -w, -w, w, w, z);
        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.enableCull();
        poseStack.popPose();
    }

    private static void quad(BufferBuilder buffer, Matrix4f m, float x0, float y0, float x1, float y1, float z) {
        buffer.vertex(m, x0, y0, z).color(0, 0, 0, 255).endVertex();
        buffer.vertex(m, x1, y0, z).color(0, 0, 0, 255).endVertex();
        buffer.vertex(m, x1, y1, z).color(0, 0, 0, 255).endVertex();
        buffer.vertex(m, x0, y1, z).color(0, 0, 0, 255).endVertex();
    }
}
