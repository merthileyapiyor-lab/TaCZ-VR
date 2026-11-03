package com.taczvr;

import com.tacz.guns.api.item.IGun;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;

/**
 * The gun on your back: the one you held last if it's still there, otherwise the first one in the hotbar, then in the
 * rest of the inventory.
 */
public final class BackGun {
    private BackGun() {
    }

    /**
     * @param lastSlot the slot of the gun held last, or -1
     * @return the inventory slot (0-8 hotbar, 9-35 the rest), or -1 without a gun
     */
    public static int find(Player player, int lastSlot) {
        Inventory inventory = player.getInventory();
        if (lastSlot >= 0 && lastSlot < Inventory.INVENTORY_SIZE && isGun(inventory, lastSlot)) {
            return lastSlot;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot != inventory.selected && isGun(inventory, slot)) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isGun(Inventory inventory, int slot) {
        return IGun.getIGunOrNull(inventory.getItem(slot)) != null;
    }
}
