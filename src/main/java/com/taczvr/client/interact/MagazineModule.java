package com.taczvr.client.interact;

import com.taczvr.TaczVR;
import com.taczvr.TaczVRConfig;
import com.taczvr.client.GunPoseSolver;
import com.taczvr.client.MagazineHandler;
import com.taczvr.client.VrClient;
import com.taczvr.client.VrGunController;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import com.taczvr.vr.GripModule;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;

/**
 * Off-hand part of the manual magazine change: grip the magazine to pull it out, grab a new one from the belt
 * (hold grip to keep holding it, {@link MagazineHandler} seats it when it touches the magazine well), and pull back
 * the charging handle to chamber the first round.
 */
public final class MagazineModule implements GripModule {
    private static final ResourceLocation ID = new ResourceLocation(TaczVR.MOD_ID, "magazine");
    private static final double HANDLE_REACH = 0.1;
    private static final double MAGAZINE_REACH = 0.08;
    private static final double RACK_PULL = 0.05;

    private enum Action { PULL, GRAB, RACK }

    @Nullable
    private Action action;
    @Nullable
    private Vec3 rackStart;
    private boolean pulled;

    @Override
    public boolean isHeld() {
        return true;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public int getPriority() {
        return 950;
    }

    @Override
    public void reset(@Nullable LocalPlayer player, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) {
            this.action = null;
            this.rackStart = null;
            this.pulled = false;
        }
    }

    @Override
    public boolean isActive(LocalPlayer player, InteractionHand hand, Vec3 handPosition) {
        this.action = null;
        if (hand != InteractionHand.OFF_HAND || !TaczVRConfig.CLIENT.enabled.get()) {
            return false;
        }
        ItemStack gun = player.getMainHandItem();
        GunPoseSolver.Pose pose = VrGunController.lastPose();
        VrPose vrPose = VrGunController.lastVrPose();
        if (pose == null || vrPose == null || !MagazineHandler.appliesTo(gun) || pose.twoHanded) {
            return false;
        }
        if (!MagazineHandler.isMagazineOut() && player.getOffhandItem().isEmpty() && pose.magazine != null
                && pose.magazine.distance(handPosition.x, handPosition.y, handPosition.z) < MAGAZINE_REACH * pose.scale / 0.3) {
            this.action = Action.PULL;
            return true;
        }
        if (MagazineHandler.isMagazineOut() && !MagazineHandler.isHoldingMagazine() && player.getOffhandItem().isEmpty()
                && MagazineHandler.isAtBelt(vrPose, handPosition, VrClient.localWorldScale()) && hasAmmo(player, gun)) {
            this.action = Action.GRAB;
            return true;
        }
        if (MagazineHandler.needsRack() && !MagazineHandler.isMagazineOut()) {
            Vector3d handle = MagazineHandler.chargingHandle(pose);
            if (handle.distance(handPosition.x, handPosition.y, handPosition.z) < HANDLE_REACH * pose.scale / 0.3) {
                this.action = Action.RACK;
                return true;
            }
        }
        return false;
    }

    private static boolean hasAmmo(LocalPlayer player, ItemStack gun) {
        return player.isCreative() || gun.getItem() instanceof AbstractGunItem gunItem && gunItem.canReload(player, gun);
    }

    @Override
    public boolean onPress(LocalPlayer player, InteractionHand hand) {
        this.pulled = false;
        this.rackStart = null;
        if (this.action == Action.PULL) {
            MagazineHandler.eject(player, VrGunController.lastPose());
            return true;
        }
        if (this.action == Action.GRAB) {
            MagazineHandler.grab(player);
            return true;
        }
        if (this.action == Action.RACK) {
            this.rackStart = offHandPos();
            return this.rackStart != null;
        }
        return false;
    }

    @Override
    public boolean onHoldTick(LocalPlayer player, InteractionHand hand) {
        if (this.action == Action.GRAB) {
            // stays held until it's seated in the gun or grip is let go
            return MagazineHandler.isHoldingMagazine();
        }
        if (this.action == Action.RACK && this.rackStart != null) {
            Vec3 now = offHandPos();
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            if (now == null || pose == null) {
                return false;
            }
            // pulled back = moved against the barrel direction
            double back = -((now.x - this.rackStart.x) * pose.forward.x + (now.y - this.rackStart.y) * pose.forward.y
                    + (now.z - this.rackStart.z) * pose.forward.z);
            if (!this.pulled && back > RACK_PULL * pose.scale / 0.3) {
                this.pulled = true;
                if (TaczVRConfig.CLIENT.haptics.get()) {
                    VrClient.haptic(VrHand.OFF_HAND, 0.03F, 0.6F);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void onRelease(@Nullable LocalPlayer player, InteractionHand hand) {
        if (this.action == Action.GRAB) {
            // let go before it reached the gun: the magazine goes back to the pouch
            MagazineHandler.letGo();
        } else if (this.action == Action.RACK && this.pulled && player != null) {
            MagazineHandler.rack(player);
        }
        this.action = null;
        this.rackStart = null;
        this.pulled = false;
    }

    @Override
    public boolean swingsArm() {
        return false;
    }

    @Nullable
    private static Vec3 offHandPos() {
        // the same tick's pose the gun pose was solved from
        VrPose pose = VrGunController.lastVrPose();
        VrPart off = pose == null ? null : pose.getOffHand();
        return off == null ? null : off.getPos();
    }
}
