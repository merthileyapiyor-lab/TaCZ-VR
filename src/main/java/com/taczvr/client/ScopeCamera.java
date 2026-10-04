package com.taczvr.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.TaczVR;
import com.taczvr.TaczVRConfig;
import com.taczvr.mixin.client.CameraInvoker;
import com.taczvr.mixin.client.GameRendererAccessor;
import com.taczvr.mixin.client.MinecraftAccessor;
import com.taczvr.vr.VrBackends;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/**
 * The zoomed scope picture for a VR mod without a spyglass pass of its own (Visor): before each frame, while you
 * look through a magnifying scope, the level is rendered once more from the scope's eyepiece, down the scope, into a
 * texture of its own. {@link ScopeView} then draws that texture over the eyepiece.
 */
public final class ScopeCamera {
    private static final int SIZE = 512;

    private static TextureTarget target;
    private static boolean rendering = false;
    private static boolean failed = false;
    private static Vec3 position = Vec3.ZERO;
    private static float yaw;
    private static float pitch;
    private static int renderedFrame = -1;
    private static int frame = 0;
    static int frames = 0;

    private ScopeCamera() {
    }

    public static boolean isRendering() {
        return rendering;
    }

    /**
     * The picture rendered for this frame, -1 if there is none.
     */
    public static int texture() {
        return target != null && renderedFrame == frame ? target.getColorTextureId() : -1;
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        frame++;
        if (VrBackends.client().usesScopeCamera() && ScopeView.isViewing() && VrClient.isVRActive()) {
            render(event.renderTickTime);
        }
    }

    static void render(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        GunPoseSolver.Pose gun = VrGunController.lastPose();
        // the fabulous graphics' transparency pass only works on the real main target
        if (failed || rendering || gun == null || mc.level == null || mc.player == null || Minecraft.useShaderTransparency()
                || !TaczVRConfig.CLIENT.scopes.get()) {
            return;
        }
        Vector3d lens = gun.toWorld(new Vector3f(0.0F, 0.0F, -ScopeView.lensDistance()));
        Vector3f forward = gun.rotation.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        position = new Vec3(lens.x, lens.y, lens.z);
        // Minecraft's look direction: x = -sin(yaw) cos(pitch), y = -sin(pitch), z = cos(yaw) cos(pitch)
        yaw = (float) Math.toDegrees(Math.atan2(-forward.x, forward.z));
        pitch = (float) Math.toDegrees(Math.asin(Math.max(-1.0F, Math.min(1.0F, -forward.y))));

        if (target == null) {
            target = new TextureTarget(SIZE, SIZE, true, Minecraft.ON_OSX);
            target.setClearColor(0.0F, 0.0F, 0.0F, 1.0F);
        }
        RenderTarget main = mc.getMainRenderTarget();
        boolean renderHand = ((GameRendererAccessor) mc.gameRenderer).taczvr$renderHand();
        boolean bob = mc.options.bobView().get();
        rendering = true;
        try {
            ((MinecraftAccessor) mc).taczvr$setMainRenderTarget(target);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            mc.gameRenderer.setRenderHand(false);
            mc.options.bobView().set(false);
            mc.gameRenderer.renderLevel(partialTick, Util.getNanos(), new PoseStack());
            // Minecraft clears with a transparent fog colour, and the eyepiece shader skips transparent pixels:
            // make the whole picture opaque, colours untouched
            target.bindWrite(false);
            RenderSystem.colorMask(false, false, false, true);
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 1.0F);
            RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.colorMask(true, true, true, true);
            renderedFrame = frame;
            frames++;
        } catch (Throwable t) {
            failed = true;
            TaczVR.LOGGER.error("Rendering the scope picture failed, scopes show no zoom from now on", t);
        } finally {
            rendering = false;
            ((MinecraftAccessor) mc).taczvr$setMainRenderTarget(main);
            mc.gameRenderer.setRenderHand(renderHand);
            mc.options.bobView().set(bob);
            main.bindWrite(true);
        }
    }

    /**
     * Points the camera of the scope render down the scope, last so nothing turns it afterwards.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!rendering) {
            return;
        }
        ((CameraInvoker) event.getCamera()).taczvr$setPosition(position);
        event.setYaw(yaw);
        event.setPitch(pitch);
        event.setRoll(0.0F);
    }

    static RenderTarget target() {
        return target;
    }
}
