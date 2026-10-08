package com.taczvr.network;

import com.taczvr.compat.TaczCompat;
import com.taczvr.server.ServerAssist;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.gun.AbstractGunItem;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.util.AttachmentDataUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Manual magazine handling of the gun in the main hand: drop the magazine, seat a new one, rack the first round.
 * Ammo is taken from and given back to the inventory the same way TACZ's own reload does.
 */
public record VrMagazinePacket(Action action) {
    public enum Action { EJECT, INSERT, CHAMBER }

    public static void encode(VrMagazinePacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.action);
    }

    public static VrMagazinePacket decode(FriendlyByteBuf buf) {
        return new VrMagazinePacket(buf.readEnum(Action.class));
    }

    /**
     * Only guns with a detachable magazine are reloaded by hand; tubes, fuel and inventory fed guns keep TACZ's reload.
     */
    @Nullable
    public static GunData magazineGunData(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null || TaczCompat.useInventoryAmmo(iGun, gun)) {
            return null;
        }
        GunData data = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).map(CommonGunIndex::getGunData).orElse(null);
        if (data == null || data.getReloadData().getType() != FeedType.MAGAZINE) {
            return null;
        }
        return data;
    }

    public static void handle(VrMagazinePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.isSpectator()) {
                return;
            }
            ItemStack gun = player.getMainHandItem();
            GunData data = magazineGunData(gun);
            if (!(gun.getItem() instanceof AbstractGunItem gunItem) || data == null) {
                return;
            }
            IGunOperator operator = IGunOperator.fromLivingEntity(player);
            if (operator.getSynReloadState().getStateType().isReloading()) {
                return;
            }
            boolean freeAmmo = !operator.needCheckAmmo();
            switch (msg.action) {
                case EJECT -> {
                    // with infinite ammo the gun refills itself, giving the rounds back would make ammo out of nothing
                    if (freeAmmo || player.isCreative() || ServerAssist.infiniteAmmo(player)) {
                        gunItem.setCurrentAmmoCount(gun, 0);
                    } else {
                        // gives the rounds left in the magazine back to the inventory, keeps the chambered one
                        gunItem.dropAllAmmo(player, gun);
                    }
                }
                case INSERT -> {
                    int max = AttachmentDataUtils.getAmmoCountWithAttachment(gun, data);
                    int current = gunItem.getCurrentAmmoCount(gun);
                    int needed = max - current;
                    if (needed <= 0) {
                        return;
                    }
                    int found;
                    if (freeAmmo) {
                        found = needed;
                    } else if (gunItem.useDummyAmmo(gun)) {
                        found = gunItem.findAndExtractDummyAmmo(gun, needed);
                    } else {
                        found = player.getCapability(ForgeCapabilities.ITEM_HANDLER, null)
                                .map(cap -> gunItem.findAndExtractInventoryAmmos(cap, gun, needed)).orElse(0);
                    }
                    gunItem.setCurrentAmmoCount(gun, current + found);
                }
                case CHAMBER -> {
                    if (data.getBolt() == Bolt.CLOSED_BOLT && !gunItem.hasBulletInBarrel(gun)
                            && gunItem.getCurrentAmmoCount(gun) > 0) {
                        gunItem.reduceCurrentAmmoCount(gun);
                        gunItem.setBulletInBarrel(gun, true);
                    }
                }
            }
            player.inventoryMenu.broadcastChanges();
        });
        ctx.setPacketHandled(true);
    }
}
