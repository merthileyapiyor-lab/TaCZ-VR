package com.taczvr.client.interact;

import com.taczvr.TaczVR;
import com.taczvr.TaczVRConfig;
import com.taczvr.client.GunPoseSolver;
import com.taczvr.client.VrClient;
import com.taczvr.client.VrGunController;
import com.taczvr.network.DetachAttachmentPacket;
import com.taczvr.network.Net;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.client.model.GunModelConstant;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.vivecraft.api.client.HeldInteractModule;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.VRBodyPartData;
import org.vivecraft.api.data.VRPose;

/**
 * Attachments by hand. Mounting: bring a scope, muzzle device, stock... held in the off-hand to its spot on the gun
 * and it snaps on, no button needed. Goes through TACZ's own refit message, so TACZ checks it fits the gun.
 * Removing: with an empty off-hand, hold grip on a mounted attachment for a moment to take it off into the hand.
 */
public final class AttachmentModule implements HeldInteractModule {
    private static final ResourceLocation ID = new ResourceLocation(TaczVR.MOD_ID, "attachments");
    // generous: the spots on top of the gun are right above the other controller, the controllers bump into each other
    private static final double MOUNT_REACH = 0.15;
    // the hand has to move this much further away before it snaps on again, or a swapped out scope would go right back
    private static final double REARM_DISTANCE = 0.08;
    private static final int MOUNT_COOLDOWN_TICKS = 10;
    private static final double REMOVE_REACH = 0.14;
    private static final int REMOVE_HOLD_TICKS = 16;
    // Inventory#getItem index of the off-hand slot
    private static final int OFFHAND_SLOT = 40;

    @Nullable
    private static Vector3d hoverPoint;
    private static boolean hoverMount;
    // when the highlight was last confirmed, Vivecraft stops asking while the hand is busy elsewhere
    private static int hoverTick;
    private static boolean mountArmed = true;
    private static int mountCooldown = 0;

    private boolean removing;
    private AttachmentType type = AttachmentType.NONE;
    private int heldTicks;

    /**
     * Where the off-hand would mount or remove an attachment right now, for highlighting. Null when nothing.
     */
    @Nullable
    public static Vector3d hoverPoint() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.tickCount - hoverTick <= 2 ? hoverPoint : null;
    }

    private static void setHover(LocalPlayer player, Vector3d at, boolean mount) {
        hoverPoint = at;
        hoverMount = mount;
        hoverTick = player.tickCount;
    }

    public static boolean hoverIsMount() {
        return hoverMount;
    }

    /**
     * Where the attachment held in the off-hand goes on the gun, shown while holding it so you know where to reach.
     *
     * @param fits false when the gun has the spot but TACZ won't take this attachment there
     */
    public record Guide(Vector3d point, AttachmentType type, boolean fits) {
    }

    @Nullable
    public static Guide guide(LocalPlayer player, GunPoseSolver.Pose pose) {
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        ItemStack offhand = player.getOffhandItem();
        IAttachment attachment = IAttachment.getIAttachmentOrNull(offhand);
        if (iGun == null || attachment == null || !TaczVRConfig.CLIENT.handAttachments.get()) {
            return null;
        }
        AttachmentType type = attachment.getType(offhand);
        Vector3d slot = slotPosition(pose, type);
        return slot == null ? null
                : new Guide(slot, type, iGun.allowAttachment(gun, offhand) && !iGun.hasAttachmentLock(gun));
    }

    /**
     * Called every tick while holding a gun in VR: snaps the off-hand's attachment on once it reaches its spot.
     */
    public static void tickAutoMount(LocalPlayer player, GunPoseSolver.Pose pose, VRPose vrPose) {
        if (mountCooldown > 0) {
            mountCooldown--;
        }
        VRBodyPartData off = vrPose.getOffHand();
        Guide guide = guide(player, pose);
        // an empty hand keeps its state: one that just took a scope off is still at the spot when the scope arrives
        if (off == null || guide == null || pose.twoHanded) {
            return;
        }
        Vec3 hand = off.getPos();
        double distance = guide.point().distance(hand.x, hand.y, hand.z);
        if (distance > MOUNT_REACH + REARM_DISTANCE) {
            mountArmed = true;
        }
        if (!guide.fits() || !mountArmed || mountCooldown > 0 || distance > MOUNT_REACH) {
            return;
        }
        setHover(player, guide.point(), true);
        NetworkHandler.CHANNEL.sendToServer(new ClientMessageRefitGun(OFFHAND_SLOT, player.getInventory().selected, guide.type()));
        player.playSound(SoundEvents.ARMOR_EQUIP_IRON, 0.6F, 1.4F);
        if (TaczVRConfig.CLIENT.haptics.get()) {
            VrClient.haptic(VRBodyPart.OFF_HAND, 0.06F, 0.8F);
            VrClient.haptic(VRBodyPart.MAIN_HAND, 0.03F, 0.4F);
        }
        mountArmed = false;
        mountCooldown = MOUNT_COOLDOWN_TICKS;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void reset(@Nullable LocalPlayer player, InteractionHand hand) {
        if (hand == InteractionHand.OFF_HAND) {
            this.removing = false;
            this.heldTicks = 0;
        }
    }

    /**
     * Only for taking attachments off: the grip stays free while holding one, mounting needs no button.
     */
    @Override
    public boolean isActive(LocalPlayer player, InteractionHand hand, Vec3 handPosition) {
        if (hand != InteractionHand.OFF_HAND) {
            return false;
        }
        this.removing = false;
        GunPoseSolver.Pose pose = VrGunController.lastPose();
        ItemStack gun = player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        // while holding the handguard the off-hand is busy, and letting go of a foregrip shouldn't pull it off
        if (pose == null || iGun == null || pose.twoHanded || !TaczVRConfig.CLIENT.enabled.get()
                || !TaczVRConfig.CLIENT.handAttachments.get() || iGun.hasAttachmentLock(gun)
                || !player.getOffhandItem().isEmpty()) {
            return false;
        }
        double best = REMOVE_REACH;
        for (AttachmentType attachmentType : AttachmentType.values()) {
            if (attachmentType == AttachmentType.NONE || iGun.getAttachment(gun, attachmentType).isEmpty()) {
                continue;
            }
            Vector3d slot = slotPosition(pose, attachmentType);
            if (slot == null) {
                continue;
            }
            double distance = slot.distance(handPosition.x, handPosition.y, handPosition.z);
            if (distance < best) {
                best = distance;
                this.removing = true;
                this.type = attachmentType;
                setHover(player, slot, false);
            }
        }
        return this.removing;
    }

    /**
     * Where an attachment of this type goes on the posed gun, or null if the gun has no real spot for it.
     */
    @Nullable
    public static Vector3d slotPosition(GunPoseSolver.Pose pose, AttachmentType type) {
        // the magazine well is where you'd put an extended magazine, its "_pos" bone often isn't
        Vector3d slot = type == AttachmentType.EXTENDED_MAG
                ? GunPoseSolver.boneWorld(pose, GunModelConstant.MAG_NORMAL_NODE)
                : GunPoseSolver.boneWorld(pose, type.name().toLowerCase() + GunModelConstant.ATTACHMENT_POS_SUFFIX);
        return slot != null && GunPoseSolver.isOnGun(pose, slot, 0.2) ? slot : null;
    }

    @Override
    public boolean onPress(LocalPlayer player, InteractionHand hand) {
        this.heldTicks = 0;
        return this.removing && serverHasMod();
    }

    @Override
    public boolean onHoldTick(LocalPlayer player, InteractionHand hand) {
        if (!this.removing) {
            return false;
        }
        // keep highlighting what comes off while grip is held
        hoverTick = player.tickCount;
        if (++this.heldTicks >= REMOVE_HOLD_TICKS) {
            Net.CHANNEL.sendToServer(new DetachAttachmentPacket(this.type));
            player.playSound(SoundEvents.ARMOR_EQUIP_IRON, 0.6F, 0.9F);
            // it lands in the hand right at its spot, don't put it straight back on
            mountArmed = false;
            return false;
        }
        return true;
    }

    @Override
    public void onRelease(@Nullable LocalPlayer player, InteractionHand hand) {
        this.heldTicks = 0;
        this.removing = false;
    }

    @Override
    public boolean swingsArm() {
        return false;
    }

    private static boolean serverHasMod() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && Net.CHANNEL.isRemotePresent(connection.getConnection());
    }
}
