package com.taczvr.vr.visor;

import com.taczvr.TaczVRConfig;
import com.taczvr.client.GripDriver;
import com.taczvr.client.OffhandGun;
import com.taczvr.client.VrGunRenderer;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.events.input.ActionButtonVREvent;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.client.events.SwingBlockVREvent;
import org.vmstudio.visor.api.client.events.SwingEntityVREvent;
import org.vmstudio.visor.api.client.events.render.HandRenderStateVREvent;
import org.vmstudio.visor.api.client.render.decoration.hand.HandRenderState;
import org.vmstudio.visor.api.common.eventbus.listener.VREventHandler;
import org.vmstudio.visor.api.common.eventbus.listener.VREventListener;

/**
 * Visor events: the grip for our grip modules, the left trigger for the off-hand gun, no roomscale swing with a gun,
 * and no Visor hand inside the gun.
 */
public final class VisorClientEvents implements VREventListener {
    // Visor's action ids: the grip (hotbar) per hand, and the left trigger
    static final String GRIP_MAIN = "hotbar_main";
    static final String GRIP_OFFHAND = "hotbar_offhand";
    static final String LEFT_TRIGGER = "mouse_left_offhand";
    public static int leftTriggerSkipped = 0;
    // Visor's left trigger, from its press and release events (they come whether or not the click is skipped)
    static volatile boolean leftTriggerDown = false;

    // waving a gun around shouldn't break blocks or punch mobs
    @VREventHandler
    public void onSwingBlock(SwingBlockVREvent event) {
        if (blocksSwing(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @VREventHandler
    public void onSwingEntity(SwingEntityVREvent event) {
        if (blocksSwing(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    /**
     * Visor's own hand at the controller would end up inside the gun: it's hidden while a hand is drawn on the gun's
     * grip instead (and on the handguard when the off-hand holds it). Menus keep their pointing hand.
     */
    @VREventHandler
    public void onHandRenderState(HandRenderStateVREvent event) {
        if (event.getState().isWorldHand() && VrGunRenderer.hidesFirstPersonHand(event.getHandType().asInteractionHand())) {
            event.setState(HandRenderState.OFF);
        }
    }

    /**
     * Visor's grip opens the hotbar: while a grip module has that hand (at a magazine, an attachment, a friend's
     * hand...) the module gets the press instead. With a gun in the hand, the left trigger fires a second pistol
     * ({@link com.taczvr.client.OffhandGun} reads it) instead of clicking.
     */
    @VREventHandler
    public void onActionButton(ActionButtonVREvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        String id = event.getActionButton().getId();
        if (GRIP_MAIN.equals(id) || GRIP_OFFHAND.equals(id)) {
            InteractionHand hand = GRIP_MAIN.equals(id) ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (GripDriver.onGrip(player, hand, event.isPressEvent())) {
                event.setCanceled(true);
            }
        } else if (LEFT_TRIGGER.equals(id)) {
            leftTriggerDown = event.isPressEvent();
            // with a gun in the hand the left trigger is no click into the game, Visor's two-handed mode would make it
            // fire the gun in the other hand: it fires a second pistol (OffhandGun), or nothing. Menus still get it
            if (mc.screen == null && TaczVRConfig.CLIENT.enabled.get() && IGun.mainHandHoldGun(player) && !pointsAtMenu(HandType.OFFHAND)) {
                event.setCanceled(true);
                leftTriggerSkipped++;
            }
        }
    }

    private static boolean pointsAtMenu(HandType hand) {
        try {
            return VisorAPI.client().getGuiManager().getCursorHandler().getFocusedOverlay(hand, false) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean blocksSwing(LocalPlayer player) {
        return player != null && TaczVRConfig.CLIENT.enabled.get() && TaczVRConfig.CLIENT.disableSwingWithGun.get()
                && IGun.mainHandHoldGun(player);
    }
}
