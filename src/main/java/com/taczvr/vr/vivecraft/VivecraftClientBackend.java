package com.taczvr.vr.vivecraft;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.taczvr.client.ClientSetup;
import com.taczvr.vr.GripModule;
import com.taczvr.vr.VrClientBackend;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPass;
import com.taczvr.vr.VrPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.vivecraft.api.client.HeldInteractModule;
import org.vivecraft.api.client.InteractModule;
import org.vivecraft.api.client.VRClientAPI;
import org.vivecraft.api.client.VRRenderingAPI;
import org.vivecraft.api.client.data.RenderPass;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client.render.VRPlayerModel;
import org.vivecraft.client_vr.ClientDataHolderVR;

import java.util.List;

public final class VivecraftClientBackend implements VrClientBackend {
    @Override
    public boolean isVRActive() {
        return VRClientAPI.instance().isVRActive();
    }

    @Override
    public @Nullable VrPose localTickPose() {
        return VivecraftPoses.wrap(VRClientAPI.instance().getPreTickWorldPose());
    }

    @Override
    public @Nullable VrPose latestRoomPose() {
        return VivecraftPoses.wrap(VRClientAPI.instance().getLatestRoomPose());
    }

    @Override
    public @Nullable VrPose renderPose(Player player) {
        if (player == Minecraft.getInstance().player) {
            return VivecraftPoses.wrap(VRClientAPI.instance().getWorldRenderPose());
        }
        return VivecraftPoses.wrap(VRRenderingAPI.instance().getWorldRenderPose(player));
    }

    /**
     * Vivecraft builds remote poses from the un-interpolated entity position.
     */
    @Override
    public Vec3 remoteInterpolationOffset(Player player, float partialTick) {
        if (player == Minecraft.getInstance().player) {
            return Vec3.ZERO;
        }
        return player.getPosition(partialTick).subtract(player.position());
    }

    @Override
    public float localWorldScale() {
        return VRClientAPI.instance().getWorldScale();
    }

    @Override
    public void haptic(VrHand hand, float seconds, float amplitude) {
        VRClientAPI.instance().triggerHapticPulse(VivecraftPoses.part(hand), seconds, 160.0F, amplitude, 0.0F);
    }

    @Override
    public @Nullable VrPass currentPass() {
        RenderPass pass = rawPass();
        if (pass == null) {
            return null;
        }
        if (RenderPass.isFirstPerson(pass)) {
            return VrPass.FIRST_PERSON;
        }
        if (pass == RenderPass.SCOPER || pass == RenderPass.SCOPEL) {
            return VrPass.SCOPE;
        }
        return RenderPass.isThirdPerson(pass) ? VrPass.THIRD_PERSON : VrPass.OTHER;
    }

    @Nullable
    public static RenderPass rawPass() {
        try {
            return VRRenderingAPI.instance().getCurrentRenderPass();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Vivecraft's left trigger, which teleports otherwise.
     */
    @Override
    public boolean offHandTriggerDown() {
        return VivecraftVRMod.INSTANCE.keyTeleport.isDown();
    }

    @Override
    public boolean isVrPlayerModel(PlayerModel<?> model) {
        return model instanceof VRPlayerModel<?>;
    }

    /**
     * Vivecraft's spyglass picture, which {@link VivecraftScope} points down the scope.
     */
    @Override
    public int scopeTexture() {
        RenderTarget target = ClientDataHolderVR.getInstance().vrRenderer.telescopeFramebufferR;
        return target == null ? -1 : target.getColorTextureId();
    }

    @Override
    public void registerGripModules(List<GripModule> modules) {
        // Vivecraft hands the grip button to these while the hand is at something they can grab
        VRClientAPI.instance().addClientRegistrationHandler(event -> {
            event.registerInteractModules(modules.stream().map(VivecraftClientBackend::adapt).toArray(InteractModule[]::new));
            ClientSetup.modulesRegistered = true;
        });
    }

    private static InteractModule adapt(GripModule module) {
        return module.isHeld() ? new Held(module) : new Simple(module);
    }

    private static class Simple implements InteractModule {
        final GripModule module;

        Simple(GripModule module) {
            this.module = module;
        }

        @Override
        public int getPriority() {
            return this.module.getPriority();
        }

        @Override
        public ResourceLocation getId() {
            return this.module.getId();
        }

        @Override
        public void reset(LocalPlayer player, InteractionHand hand) {
            this.module.reset(player, hand);
        }

        @Override
        public boolean isActive(LocalPlayer player, InteractionHand hand, Vec3 handPosition) {
            return this.module.isActive(player, hand, handPosition);
        }

        @Override
        public boolean onPress(LocalPlayer player, InteractionHand hand) {
            return this.module.onPress(player, hand);
        }

        @Override
        public boolean swingsArm() {
            return this.module.swingsArm();
        }
    }

    private static final class Held extends Simple implements HeldInteractModule {
        Held(GripModule module) {
            super(module);
        }

        @Override
        public boolean onHoldTick(LocalPlayer player, InteractionHand hand) {
            return this.module.onHoldTick(player, hand);
        }

        @Override
        public void onRelease(LocalPlayer player, InteractionHand hand) {
            this.module.onRelease(player, hand);
        }
    }
}
