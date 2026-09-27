package com.taczvr.client;

import com.taczvr.mixin.client.VRPlayerModelAccessor;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.client.render.VRPlayerModel;
import org.vivecraft.client.utils.ModelUtils;
import org.vivecraft.client_vr.ClientDataHolderVR;

/**
 * Vivecraft's model arm ends in a fist right at the controller, which is where the gun's grip is, so the chunky fist
 * swallows the gun's middle. Instead of hiding the arm (which left the gun floating), the fist is pulled back to sit
 * on the back of the grip: the arm stays on the shoulder and the fist holds the gun.
 * Runs right after Vivecraft posed the model, so armor and sleeves copy the shorter arm.
 */
public final class GunArmFitter {
    // model pixels (about 6 cm each) between the controller and the end of the model arm
    private static final float WRIST_GAP = 0.6F;
    // arm length Vivecraft uses: the fist end is 10 pixels from the shoulder pivot
    private static final float ARM_LENGTH = 10.0F;

    /**
     * How many arms were fitted, for the self-test.
     */
    static int fitted = 0;

    private GunArmFitter() {
    }

    /**
     * Models without elbows: one box from the shoulder to the controller, made shorter.
     */
    public static void fitWholeArms(VRPlayerModel<?> model, LivingEntity entity) {
        VRPlayerModelAccessor access = (VRPlayerModelAccessor) model;
        ClientVRPlayers.RotInfo rotInfo = access.taczvr$getRotInfo();
        // the model is shared by all players and Vivecraft only sets the arm length for floating arms:
        // undo what we did for the previous player
        if (rotInfo == null || ClientDataHolderVR.getInstance().vrSettings.playerLimbsConnected) {
            model.leftArm.yScale = 1.0F;
            model.rightArm.yScale = 1.0F;
        }
        if (rotInfo != null && entity instanceof Player player && rotInfo.offHandPos.distanceSquared(rotInfo.mainHandPos) > 0.0F) {
            HumanoidArm main = access.taczvr$getMainArm();
            for (HumanoidArm side : HumanoidArm.values()) {
                if (!holdsGun(player, side == main)) {
                    continue;
                }
                ModelPart arm = side == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
                Vector3fc hand = side == main ? rotInfo.mainHandPos : rotInfo.offHandPos;
                // how far down the arm the controller is
                float reach = toController(entity, arm, hand, access).dot(axis(arm));
                float wanted = reach - WRIST_GAP;
                if (wanted < ARM_LENGTH * arm.yScale) {
                    arm.yScale = Math.max(0.3F, wanted / ARM_LENGTH);
                    fitted++;
                }
            }
        }
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
    }

    /**
     * Models with elbows: the forearm ends at the controller, it's moved back along itself.
     *
     * @param arm which side this limb is, null for legs
     */
    public static void fitForearm(VRPlayerModel<?> model, LivingEntity entity, ModelPart lower, Vector3fc lowerPos,
                                  @Nullable HumanoidArm arm) {
        VRPlayerModelAccessor access = (VRPlayerModelAccessor) model;
        ClientVRPlayers.RotInfo rotInfo = access.taczvr$getRotInfo();
        if (arm == null || rotInfo == null || !(entity instanceof Player player)
                || !holdsGun(player, arm == access.taczvr$getMainArm())) {
            return;
        }
        Vector3f axis = axis(lower);
        // positive when the forearm already stops short of the controller
        float shortBy = toController(entity, lower, lowerPos, access).dot(axis);
        float back = WRIST_GAP - shortBy;
        if (back > 0.0F) {
            lower.setPos(lower.x - axis.x * back, lower.y - axis.y * back, lower.z - axis.z * back);
            fitted++;
        }
    }

    private static boolean holdsGun(Player player, boolean mainHand) {
        if (!VrGunRenderer.handsOnGun(player) || !VrGunRenderer.rendersHeldGun(player, player.getMainHandItem())) {
            return false;
        }
        return mainHand || VrGunRenderer.leftHandOnGun(player) || OffhandGun.holds(player);
    }

    /**
     * From the part's pivot to the controller, in model pixels.
     */
    private static Vector3f toController(LivingEntity entity, ModelPart part, Vector3fc handPos, VRPlayerModelAccessor access) {
        boolean useWorldScale = access.taczvr$isMainPlayer() || ClientDataHolderVR.getInstance().vrSettings.applyPlayerWorldscale;
        Vector3f controller = new Vector3f();
        ModelUtils.worldToModel(entity, handPos, access.taczvr$getRotInfo(), access.taczvr$getBodyYaw(), useWorldScale, controller);
        return controller.sub(part.x, part.y, part.z);
    }

    /**
     * Direction the part's boxes run along (its local +Y), the same rotation order ModelPart renders with.
     */
    private static Vector3f axis(ModelPart part) {
        return new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(new Vector3f(0.0F, 1.0F, 0.0F));
    }
}
