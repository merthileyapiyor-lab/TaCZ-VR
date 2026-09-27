package com.taczvr.content;

import com.taczvr.TaczVRConfig;
import com.taczvr.server.Tactical;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown grenade: bounces off what it hits and goes off when its fuse runs out. A frag grenade explodes, a
 * flashbang blinds, a smoke grenade lies there hissing out a cloud of smoke.
 */
public class GrenadeEntity extends ThrowableItemProjectile {
    private static final float POWER = 3.0F;
    private int fuse = GrenadeItem.FUSE_TICKS;
    // counts down while a smoke grenade is smoking
    private int smoke = 0;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    public GrenadeEntity(Level level, LivingEntity owner) {
        super(ModContent.GRENADE_ENTITY.get(), owner, level);
    }

    public void setFuse(int ticks) {
        this.fuse = ticks;
    }

    public int getFuse() {
        return this.fuse;
    }

    public boolean isSmoking() {
        return this.smoke > 0;
    }

    public int smokeLeft() {
        return this.smoke;
    }

    public GrenadeItem.Kind kind() {
        return this.getItem().getItem() instanceof GrenadeItem grenade ? grenade.kind() : GrenadeItem.Kind.FRAG;
    }

    @Override
    protected Item getDefaultItem() {
        return ModContent.GRENADE.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.smoke > 0) {
            if (--this.smoke <= 0) {
                this.discard();
            } else if (this.smoke % 20 == 0) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 0.6F);
            }
        } else if (--this.fuse <= 0) {
            detonate();
        }
    }

    void detonate() {
        switch (kind()) {
            case FLASH -> {
                Tactical.flash(this.level(), this.position().add(0.0, 0.1, 0.0), this.getOwner());
                this.discard();
            }
            case SMOKE -> {
                this.smoke = Tactical.SMOKE_TICKS;
                Tactical.smoke(this.level(), this);
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
            }
            default -> explode();
        }
    }

    void explode() {
        Level.ExplosionInteraction interaction = TaczVRConfig.COMMON.grenadeBreaksBlocks.get()
                ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;
        this.level().explode(this, this.getX(), this.getY(0.0625), this.getZ(), POWER, interaction);
        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit) {
        super.onHitBlock(hit);
        Vec3 motion = this.getDeltaMovement();
        Direction face = hit.getDirection();
        // bounce: flip and damp the part going into the block, slow down along it
        double x = face.getAxis() == Direction.Axis.X ? -motion.x * 0.35 : motion.x * 0.6;
        double y = face.getAxis() == Direction.Axis.Y ? -motion.y * 0.35 : motion.y * 0.6;
        double z = face.getAxis() == Direction.Axis.Z ? -motion.z * 0.35 : motion.z * 0.6;
        Vec3 bounced = new Vec3(x, y, z);
        if (face == Direction.UP && bounced.lengthSqr() < 0.004) {
            bounced = Vec3.ZERO;
        } else if (motion.lengthSqr() > 0.01) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.6F, 1.6F);
        }
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        this.setPos(hit.getLocation().add(normal.scale(0.05)));
        this.setDeltaMovement(bounced);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        this.setDeltaMovement(this.getDeltaMovement().scale(-0.2));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Fuse", this.fuse);
        tag.putInt("Smoke", this.smoke);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.fuse = tag.getInt("Fuse");
        this.smoke = tag.getInt("Smoke");
        if (this.smoke > 0) {
            Tactical.smoke(this.level(), this);
        }
    }
}
