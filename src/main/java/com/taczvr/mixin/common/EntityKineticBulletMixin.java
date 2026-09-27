package com.taczvr.mixin.common;

import com.taczvr.server.ServerAimStore;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.pojo.data.gun.BulletData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * TACZ spawns bullets at the shooter's eyes. For VR players spawn them at the gun's muzzle.
 */
@Mixin(value = EntityKineticBullet.class, remap = false)
public abstract class EntityKineticBulletMixin {
    @Shadow
    private Vec3 startPos;

    @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;ZLcom/tacz/guns/resource/pojo/data/gun/GunData;Lcom/tacz/guns/resource/pojo/data/gun/BulletData;)V",
            at = @At("RETURN"))
    private void taczvr$startAtMuzzle(EntityType<?> type, Level level, LivingEntity thrower, ItemStack gunItem,
                                      ResourceLocation ammoId, ResourceLocation gunId, ResourceLocation gunDisplayId,
                                      boolean isTracerAmmo, GunData gunData, BulletData bulletData, CallbackInfo ci) {
        if (level.isClientSide() || !(thrower instanceof ServerPlayer player)) {
            return;
        }
        ServerAimStore.Aim aim = ServerAimStore.getAim(player);
        if (aim == null) {
            return;
        }
        Entity self = (Entity) (Object) this;
        self.setPos(aim.origin());
        this.startPos = self.position();
    }
}
