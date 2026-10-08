package com.taczvr.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.compat.Axis;
import com.taczvr.compat.Joml;
import com.taczvr.TaczVRConfig;
import com.taczvr.mixin.client.BedrockModelAccessor;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.nbt.AttachmentItemDataAccessor;
import com.tacz.guns.client.model.BedrockAttachmentModel;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.GunModelConstant;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.client.model.bedrock.ModelRendererWrapper;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Works out where a TACZ gun model sits in the world when a VR player holds it.
 * <p>
 * TACZ renders its first person gun so that the sight's view bone ends up at the camera, looking down -Z.
 * We reuse exactly that chain, but instead of the camera we put an "aim frame" into the world: its -Z axis is the
 * sight line and it is placed so the gun's pistol grip (the third person hand bone) sits in the VR controller.
 */
public final class GunPoseSolver {
    // fallbacks in aim frame units (blocks, unscaled) for models without the bones, based on the default AK
    private static final Vector3f FALLBACK_GRIP = new Vector3f(0.0F, -0.25F, -0.3F);
    private static final Vector3f FALLBACK_MUZZLE = new Vector3f(0.0F, -0.1F, -1.8F);
    // the magazine bone pivot sits at the top of the mag well, the hand touches the magazine lower down
    private static final float MAGAZINE_TOUCH_DROP = 0.2F;

    private static final Map<UUID, Boolean> TWO_HANDED = new HashMap<>();
    private static List<? extends String> parsedOverridesSource = null;
    private static final Map<String, Float> SCALE_OVERRIDES = new HashMap<>();

    private GunPoseSolver() {
    }

    public static final class Pose {
        public GunDisplayInstance display;
        public BedrockGunModel model;
        public List<BedrockPart> viewPath;
        /**
         * Origin of the aim frame (where the eye is when looking down the sights) in world space.
         */
        public final Vector3d origin = new Vector3d();
        public final Quaternionf rotation = new Quaternionf();
        public float scale;
        public final Vector3d grip = new Vector3d();
        public final Vector3d muzzle = new Vector3d();
        public final Vector3d forward = new Vector3d();
        @Nullable
        public Vector3d magazine;
        public boolean twoHanded;
        /**
         * Held in the left hand (a left-handed player's gun, or the off-hand gun when dual wielding).
         */
        public boolean leftHanded;

        public Vector3d toWorld(Vector3f aimFrameLocal) {
            Vector3f v = this.rotation.transform(new Vector3f(aimFrameLocal).mul(this.scale));
            return new Vector3d(this.origin).add(v.x, v.y, v.z);
        }

        /**
         * Direction bullets leave the muzzle, so they cross the sight line at {@code zeroDistance}.
         */
        public Vec3 bulletDirection(double zeroDistance) {
            Vector3d target = new Vector3d(this.forward).mul(zeroDistance).add(this.origin);
            Vector3d dir = target.sub(this.muzzle);
            if (dir.lengthSquared() < 1.0E-8) {
                return new Vec3(this.forward.x, this.forward.y, this.forward.z);
            }
            dir.normalize();
            return new Vec3(dir.x, dir.y, dir.z);
        }
    }

    /**
     * @param kickRadians   visual recoil, rotates the muzzle up around the grip
     * @param kickBack      visual recoil, pushes the gun back along the barrel (meters)
     */
    @Nullable
    public static Pose solve(Player player, ItemStack stack, VrPose vrPose, Vec3 positionOffset, float worldScale,
                             float kickRadians, float kickBack) {
        return solve(player.getUUID(), stack, vrPose, positionOffset, worldScale, kickRadians, kickBack,
                offHandFree(player));
    }

    @Nullable
    public static Pose solve(UUID playerId, ItemStack stack, VrPose vrPose, Vec3 positionOffset, float worldScale,
                             float kickRadians, float kickBack) {
        return solve(playerId, stack, vrPose, positionOffset, worldScale, kickRadians, kickBack, true);
    }

    /**
     * A hand bringing a scope, a magazine or ammo to the gun is not holding the handguard, even when it passes by it.
     */
    private static boolean offHandFree(Player player) {
        ItemStack off = player.getOffhandItem();
        if (IAttachment.getIAttachmentOrNull(off) != null || IAmmo.getIAmmoOrNull(off) != null
                || IGun.getIGunOrNull(off) != null) {
            return false;
        }
        return player != Minecraft.getInstance().player || !MagazineHandler.isHoldingMagazine();
    }

    /**
     * @param offHandFree false while the off-hand holds something for the gun, it can't take the handguard then
     */
    @Nullable
    public static Pose solve(UUID playerId, ItemStack stack, VrPose vrPose, Vec3 positionOffset, float worldScale,
                             float kickRadians, float kickBack, boolean offHandFree) {
        IGun iGun = IGun.getIGunOrNull(stack);
        if (iGun == null) {
            return null;
        }
        GunDisplayInstance display = TimelessAPI.getGunDisplay(stack).orElse(null);
        if (display == null || display.getGunModel() == null) {
            return null;
        }
        VrPart mainHand = vrPose.getMainHand();
        if (mainHand == null) {
            return null;
        }
        TaczVRConfig.Client cfg = TaczVRConfig.CLIENT;
        BedrockGunModel model = display.getGunModel();

        Pose pose = new Pose();
        pose.leftHanded = vrPose.isLeftHanded();
        pose.display = display;
        pose.model = model;
        pose.viewPath = pickViewPath(model, iGun, stack);
        pose.scale = (float) (cfg.gunScale.get() * scaleMultiplier(iGun, stack)) * worldScale;

        Matrix4f chain = aimFrameChain(pose.viewPath);
        Vector3f gripLocal = boneOrigin(chain, model.getThirdPersonHandOriginPath(), FALLBACK_GRIP);
        Vector3f muzzleLocal = boneOrigin(chain, model.getMuzzleFlashPosPath(), FALLBACK_MUZZLE);
        Vector3f magLocal = boneOrigin(chain, magazinePath(model), null);
        if (magLocal != null) {
            magLocal.y -= MAGAZINE_TOUCH_DROP;
        }

        // the controller, with the configured grip adjustment
        Vector3d controllerPos = toVector(mainHand.getPos().add(positionOffset));
        Quaternionf rot = new Quaternionf(mainHand.getRotation());
        rot.rotateX((float) Math.toRadians(cfg.gripPitch.get()));
        Vector3f gripOffset = rot.transform(new Vector3f(
                cfg.gripOffsetX.get().floatValue(),
                cfg.gripOffsetY.get().floatValue(),
                cfg.gripOffsetZ.get().floatValue()).mul(worldScale));
        Vector3d grip = new Vector3d(controllerPos).add(gripOffset.x, gripOffset.y, gripOffset.z);

        pose.twoHanded = applyTwoHanded(playerId, vrPose, positionOffset, rot, grip, gripLocal, muzzleLocal, pose.scale,
                offHandFree);

        if (kickRadians != 0.0F) {
            rot.rotateX(kickRadians);
        }
        Vector3f forward = rot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        if (kickBack != 0.0F) {
            grip.sub(forward.x * kickBack, forward.y * kickBack, forward.z * kickBack);
        }

        pose.rotation.set(rot);
        pose.forward.set(forward.x, forward.y, forward.z);
        pose.grip.set(grip);
        // origin = grip - R * (s * gripLocal)
        Vector3f gripWorldOffset = rot.transform(new Vector3f(gripLocal).mul(pose.scale));
        pose.origin.set(grip).sub(gripWorldOffset.x, gripWorldOffset.y, gripWorldOffset.z);
        pose.muzzle.set(pose.toWorld(muzzleLocal));
        pose.magazine = magLocal == null ? null : pose.toWorld(magLocal);
        return pose;
    }

    /**
     * Rotates {@code rot} so the barrel points at the off-hand when the off-hand is on the handguard.
     */
    private static boolean applyTwoHanded(UUID id, VrPose vrPose, Vec3 positionOffset, Quaternionf rot,
                                          Vector3d grip, Vector3f gripLocal, Vector3f muzzleLocal, float scale,
                                          boolean offHandFree) {
        TaczVRConfig.Client cfg = TaczVRConfig.CLIENT;
        VrPart offHand = vrPose.getOffHand();
        if (!cfg.twoHanded.get() || !offHandFree || offHand == null || vrPose.isSeated()) {
            TWO_HANDED.remove(id);
            return false;
        }
        Vector3f barrel = rot.transform(new Vector3f(muzzleLocal).sub(gripLocal));
        float gunLength = barrel.length() * scale;
        if (gunLength < 1.0E-4F) {
            TWO_HANDED.remove(id);
            return false;
        }
        barrel.normalize();

        Vector3d off = toVector(offHand.getPos().add(positionOffset));
        Vector3f toOff = new Vector3f((float) (off.x - grip.x), (float) (off.y - grip.y), (float) (off.z - grip.z));
        // measured against where the gun points with one hand, so an arm swinging past while walking doesn't grab it
        float along = toOff.dot(barrel);
        float offLine = new Vector3f(barrel).mul(along).sub(toOff).length();
        boolean was = TWO_HANDED.getOrDefault(id, false);
        double maxOffLine = was ? cfg.twoHandKeepOffset.get() : cfg.twoHandStartOffset.get();
        boolean active = along > cfg.twoHandMinDistance.get()
                && along < gunLength + 0.2F
                && offLine < maxOffLine;
        TWO_HANDED.put(id, active);
        if (active) {
            toOff.normalize();
            rot.premul(new Quaternionf().rotationTo(barrel, toOff));
        }
        return active;
    }

    /**
     * Matrix from TACZ model space to our aim frame, the same chain TACZ uses for first person rendering.
     */
    public static Matrix4f aimFrameChain(List<BedrockPart> viewPath) {
        return new Matrix4f()
                .translate(0.0F, 1.5F, 0.0F)
                .rotateZ((float) Math.PI)
                .translate(0.0F, 1.5F, 0.0F)
                .mul(positioningNodeInverse(viewPath))
                .translate(0.0F, -1.5F, 0.0F);
    }

    /**
     * Applies {@link #aimFrameChain} to a PoseStack the way TACZ does, so the normals are correct too.
     */
    public static void applyAimFrameChain(PoseStack poseStack, List<BedrockPart> viewPath) {
        poseStack.translate(0.0F, 1.5F, 0.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.translate(0.0F, 1.5F, 0.0F);
        Joml.mulPoseMatrix(poseStack, positioningNodeInverse(viewPath));
        poseStack.translate(0.0F, -1.5F, 0.0F);
    }

    /**
     * The inverse of a camera positioning bone chain, the way TACZ works it out for first person (TACZ 1.1.4 keeps it
     * private).
     */
    static Matrix4f positioningNodeInverse(@Nullable List<BedrockPart> nodePath) {
        Matrix4f matrix = new Matrix4f();
        if (nodePath != null) {
            for (int i = nodePath.size() - 1; i >= 0; i--) {
                BedrockPart part = nodePath.get(i);
                matrix.rotateX(-part.xRot).rotateY(-part.yRot).rotateZ(-part.zRot);
                if (part.getParent() != null) {
                    matrix.translate(-part.x / 16.0F, -part.y / 16.0F, -part.z / 16.0F);
                } else {
                    matrix.translate(-part.x / 16.0F, 1.5F - part.y / 16.0F, -part.z / 16.0F);
                }
            }
        }
        return matrix;
    }

    /**
     * The sight the player looks through: the mounted scope if there is one, else the iron sights.
     * Mirrors TACZ's first person positioning.
     */
    private static List<BedrockPart> pickViewPath(BedrockGunModel model, IGun iGun, ItemStack stack) {
        ResourceLocation scopeId = iGun.getAttachmentId(stack, AttachmentType.SCOPE);
        if (scopeId.equals(DefaultAssets.EMPTY_ATTACHMENT_ID)) {
            scopeId = iGun.getBuiltInAttachmentId(stack, AttachmentType.SCOPE);
        }
        if (!DefaultAssets.isEmptyAttachmentId(scopeId) && model.getScopePosPath() != null) {
            List<BedrockPart> path = new ArrayList<>(model.getScopePosPath());
            TimelessAPI.getClientAttachmentIndex(scopeId).ifPresent(index -> {
                BedrockAttachmentModel attachmentModel = index.getAttachmentModel();
                // TACZ 1.1.4 scopes have one view for all their zoom levels
                if (attachmentModel != null) {
                    List<BedrockPart> scopeView = attachmentModel.getScopeViewPath();
                    if (scopeView != null) {
                        path.addAll(scopeView);
                    }
                }
            });
            return path;
        }
        if (model.getIronSightPath() != null) {
            return model.getIronSightPath();
        }
        return model.getIdleSightPath();
    }

    @Nullable
    private static List<BedrockPart> magazinePath(BedrockGunModel model) {
        return bonePath(model, GunModelConstant.MAG_NORMAL_NODE);
    }

    /**
     * World position of a bone of the posed gun, in its rest pose. Null if the model doesn't have the bone.
     */
    @Nullable
    public static Vector3d boneWorld(Pose pose, String boneName) {
        List<BedrockPart> path = bonePath(pose.model, boneName);
        if (path == null) {
            return null;
        }
        Vector3f local = boneOrigin(aimFrameChain(pose.viewPath), path, null);
        return local == null ? null : pose.toWorld(local);
    }

    /**
     * Models keep unused bones as placeholders somewhere off the gun. True if {@code point} is along the gun,
     * within {@code reach} meters (at gun scale 0.3) of the line through grip and muzzle.
     */
    public static boolean isOnGun(Pose pose, Vector3d point, double reach) {
        Vector3d axis = new Vector3d(pose.muzzle).sub(pose.grip);
        double length = axis.length();
        if (length < 1.0E-4) {
            return true;
        }
        axis.div(length);
        Vector3d toPoint = new Vector3d(point).sub(pose.grip);
        double along = toPoint.dot(axis);
        double offAxis = new Vector3d(axis).mul(along).sub(toPoint).length();
        return along > -length && along < length * 1.2 && offAxis < reach * pose.scale / 0.3;
    }

    /**
     * Position of the end of a bone path in the aim frame (unscaled), in the rest pose.
     */
    static Vector3f boneInAimFrame(Pose pose, List<BedrockPart> path) {
        Vector3f local = boneOrigin(aimFrameChain(pose.viewPath), path, null);
        return local == null ? new Vector3f() : local;
    }

    /**
     * Bone position in the model's own space, in its rest pose.
     */
    @Nullable
    static Vector3f boneInModel(BedrockModel model, String boneName) {
        List<BedrockPart> path = bonePath(model, boneName);
        return path == null ? null : boneOrigin(new Matrix4f(), path, null);
    }

    /**
     * Bones from the model root down to the named bone, or null if the model doesn't have it.
     */
    @Nullable
    static List<BedrockPart> bonePath(BedrockModel model, String boneName) {
        ModelRendererWrapper wrapper = ((BedrockModelAccessor) model).taczvr$getModelMap().get(boneName);
        if (wrapper == null) {
            return null;
        }
        List<BedrockPart> path = new ArrayList<>();
        for (BedrockPart part = wrapper.getModelRenderer(); part != null; part = part.getParent()) {
            path.add(0, part);
        }
        return path;
    }

    /**
     * Position of a bone's pivot in the aim frame, in the rest pose (before animations are applied).
     */
    @Nullable
    private static Vector3f boneOrigin(Matrix4f chain, @Nullable List<BedrockPart> path, @Nullable Vector3f fallback) {
        if (path == null || path.isEmpty()) {
            return fallback == null ? null : new Vector3f(fallback);
        }
        PoseStack poseStack = new PoseStack();
        Joml.setPose(poseStack, chain);
        for (BedrockPart part : path) {
            part.translateAndRotateAndScale(poseStack);
        }
        return Joml.translation(poseStack);
    }

    private static double scaleMultiplier(IGun iGun, ItemStack stack) {
        List<? extends String> source = TaczVRConfig.CLIENT.scaleOverrides.get();
        if (source != parsedOverridesSource) {
            SCALE_OVERRIDES.clear();
            for (String entry : source) {
                int eq = entry.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                try {
                    SCALE_OVERRIDES.put(entry.substring(0, eq).trim(), Float.parseFloat(entry.substring(eq + 1).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
            parsedOverridesSource = source;
        }
        if (SCALE_OVERRIDES.isEmpty()) {
            return 1.0;
        }
        ResourceLocation gunId = iGun.getGunId(stack);
        Float byId = SCALE_OVERRIDES.get(gunId.toString());
        if (byId != null) {
            return byId;
        }
        String type = TimelessAPI.getClientGunIndex(gunId).map(ClientGunIndex::getType).orElse(null);
        if (type != null) {
            Float byType = SCALE_OVERRIDES.get(type);
            if (byType != null) {
                return byType;
            }
        }
        return 1.0;
    }

    public static void forget(UUID player) {
        TWO_HANDED.remove(player);
    }

    /**
     * Two-handed state from the last solve for this player.
     */
    public static boolean isTwoHanded(UUID player) {
        return TWO_HANDED.getOrDefault(player, false);
    }

    private static Vector3d toVector(Vec3 v) {
        return new Vector3d(v.x, v.y, v.z);
    }
}
