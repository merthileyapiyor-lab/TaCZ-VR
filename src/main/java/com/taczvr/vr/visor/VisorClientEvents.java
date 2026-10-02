package com.taczvr.vr.visor;

import com.taczvr.TaczVRConfig;
import com.taczvr.client.VrGunRenderer;
import com.tacz.guns.api.item.IGun;
import net.minecraft.client.player.LocalPlayer;
import org.vmstudio.visor.api.client.events.SwingBlockVREvent;
import org.vmstudio.visor.api.client.events.SwingEntityVREvent;
import org.vmstudio.visor.api.client.events.render.HandRenderStateVREvent;
import org.vmstudio.visor.api.client.render.decoration.hand.HandRenderState;
import org.vmstudio.visor.api.common.eventbus.listener.VREventHandler;
import org.vmstudio.visor.api.common.eventbus.listener.VREventListener;

/**
 * Visor events: no roomscale swing with a gun, and no Visor hand inside the gun.
 */
public final class VisorClientEvents implements VREventListener {
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

    static boolean blocksSwing(LocalPlayer player) {
        return player != null && TaczVRConfig.CLIENT.enabled.get() && TaczVRConfig.CLIENT.disableSwingWithGun.get()
                && IGun.mainHandHoldGun(player);
    }
}
