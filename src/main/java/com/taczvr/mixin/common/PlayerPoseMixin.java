package com.taczvr.mixin.common;

import com.taczvr.DownedState;
import com.taczvr.server.GameManager;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A player down on the ground in the zombie waves crawls. Done here and not with the forced pose, TACZ clears that
 * every tick for anyone not crawling with a gun.
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void taczvr$downedCrawl(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        boolean downed = self.getLevel().isClientSide() ? DownedState.CLIENT.contains(self.getId()) : GameManager.isDowned(self);
        if (downed) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
