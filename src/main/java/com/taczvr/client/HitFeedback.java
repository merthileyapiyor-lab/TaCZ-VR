package com.taczvr.client;

import com.taczvr.TaczVRConfig;
import com.tacz.guns.api.event.common.EntityHurtByGunEvent;
import com.tacz.guns.api.event.common.EntityKillByGunEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.vivecraft.api.data.VRBodyPart;

/**
 * Feel your hits: a buzz in the gun hand and a hit sound, stronger for headshots, and a longer buzz for a kill.
 * The HUD hit marker TACZ normally shows is hidden in VR.
 */
public final class HitFeedback {
    static int hits = 0;
    static int kills = 0;

    private HitFeedback() {
    }

    private static boolean mine(LivingEntity attacker) {
        Minecraft mc = Minecraft.getInstance();
        return attacker != null && attacker == mc.player && TaczVRConfig.CLIENT.hitFeedback.get() && VrClient.isVRActive();
    }

    @SubscribeEvent
    public static void onHurt(EntityHurtByGunEvent.Post event) {
        if (!event.getLogicalSide().isClient() || !mine(event.getAttacker())) {
            return;
        }
        hits++;
        boolean head = event.isHeadShot();
        buzz(head ? 0.06F : 0.035F, head ? 1.0F : 0.7F);
        play(head ? 1.7F : 1.25F, head ? 0.7F : 0.5F);
    }

    @SubscribeEvent
    public static void onKill(EntityKillByGunEvent event) {
        if (!event.getLogicalSide().isClient() || !mine(event.getAttacker())) {
            return;
        }
        kills++;
        buzz(0.14F, 1.0F);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 0.6F));
    }

    private static void buzz(float seconds, float amplitude) {
        if (!TaczVRConfig.CLIENT.haptics.get()) {
            return;
        }
        VrClient.haptic(VRBodyPart.MAIN_HAND, seconds, amplitude);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && GunPoseSolver.isTwoHanded(mc.player.getUUID())) {
            VrClient.haptic(VRBodyPart.OFF_HAND, seconds, amplitude * 0.6F);
        }
    }

    private static void play(float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ARROW_HIT_PLAYER, pitch, volume));
    }
}
