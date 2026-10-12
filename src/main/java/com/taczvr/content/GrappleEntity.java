package com.taczvr.content;

import com.taczvr.mixin.common.ServerGamePacketListenerAccessor;
import com.taczvr.network.GrapplePacket;
import com.taczvr.network.Net;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The hook of a grappling hook. It grabs the block you aim at the moment you fire (as far as you can see), its owner is
 * told the spot and pulled there on their side (GrappleClient), so it works even where the hook itself is too far
 * away to be sent to them. A miss flies off for a moment. Gone when the owner lets go, switches items or gets too far.
 */
public class GrappleEntity extends Projectile {
    // the farthest render distance there is: 32 chunks
    public static final double MAX_LENGTH = 512.0;
    private static final double MISS_SPEED = 12.0;
    private static final int MISS_TICKS = 8;
    private static final EntityDataAccessor<Boolean> ANCHORED = SynchedEntityData.defineId(GrappleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> OFF_HAND = SynchedEntityData.defineId(GrappleEntity.class, EntityDataSerializers.BOOLEAN);
    // server side: each player's hook out
    private static final Map<UUID, GrappleEntity> BY_OWNER = new HashMap<>();
    // server side: each player's render distance in chunks, as their client told it
    private static final Map<UUID, Integer> VIEW_CHUNKS = new HashMap<>();
    public static int anchoredCount = 0;
    // server side: fired from the inventory while carrying something with Carry On, no hand holds the hook
    private boolean fromInventory = false;

    public GrappleEntity(EntityType<? extends GrappleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public GrappleEntity(Level level, Player owner, InteractionHand hand) {
        this(ModContent.GRAPPLE_ENTITY.get(), level);
        this.setOwner(owner);
        this.entityData.set(OFF_HAND, hand == InteractionHand.OFF_HAND);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(ANCHORED, false);
        this.entityData.define(OFF_HAND, false);
    }

    public boolean isAnchored() {
        return this.entityData.get(ANCHORED);
    }

    public InteractionHand hand() {
        return this.entityData.get(OFF_HAND) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    void setFromInventory(boolean fromInventory) {
        this.fromInventory = fromInventory;
    }

    /**
     * Whether the owner still holds on: the hook in the hand it was fired from, or, fired while carrying, a hook in the
     * inventory and that hand still free.
     */
    private boolean heldBy(ServerPlayer player) {
        if (player.getItemInHand(this.hand()).getItem() instanceof GrapplingHookItem) {
            return true;
        }
        return this.fromInventory && player.getItemInHand(this.hand()).isEmpty()
                && !GrapplingHookItem.findInInventory(player).isEmpty();
    }

    /**
     * The player's hook, if they have one out. Server side.
     */
    @Nullable
    public static GrappleEntity of(Player player) {
        GrappleEntity hook = BY_OWNER.get(player.getUUID());
        return hook == null || hook.isRemoved() ? null : hook;
    }

    public static void setViewDistance(ServerPlayer player, int chunks) {
        VIEW_CHUNKS.put(player.getUUID(), chunks);
    }

    /**
     * How far the player's hook reaches: as far as they can see, their render distance, though no further than the
     * server sends chunks.
     */
    public static double reach(ServerPlayer player) {
        int chunks = player.server.getPlayerList().getViewDistance();
        Integer client = VIEW_CHUNKS.get(player.getUUID());
        if (client != null) {
            chunks = Math.min(chunks, client);
        }
        return Math.min(MAX_LENGTH, Math.max(2, chunks) * 16.0);
    }

    /**
     * Fires a hook from {@code from} along {@code dir}: it holds at once on the first block within reach.
     */
    public static GrappleEntity fire(ServerPlayer player, InteractionHand hand, Vec3 from, Vec3 dir) {
        Level level = player.level();
        Vec3 direction = dir.normalize();
        // only through loaded chunks: looking up a block in an unloaded one would load or even generate it
        double length = 0.0;
        double reach = reach(player);
        while (length < reach && level.hasChunkAt(BlockPos.containing(from.add(direction.scale(Math.min(reach, length + 8.0)))))) {
            length = Math.min(reach, length + 8.0);
        }
        BlockHitResult hit = level.clip(new ClipContext(from, from.add(direction.scale(length)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        GrappleEntity hook = new GrappleEntity(level, player, hand);
        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 at = hit.getLocation().subtract(direction.scale(0.1));
            hook.setPos(at);
            hook.entityData.set(ANCHORED, true);
            anchoredCount++;
            level.playSound(null, at.x, at.y, at.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0F, 1.2F);
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new GrapplePacket(true, at, hand == InteractionHand.OFF_HAND));
        } else {
            hook.setPos(from);
            hook.setDeltaMovement(direction.scale(MISS_SPEED));
        }
        level.addFreshEntity(hook);
        GrappleEntity old = BY_OWNER.put(player.getUUID(), hook);
        if (old != null && old != hook) {
            old.discard();
        }
        return hook;
    }

    @Override
    public void tick() {
        super.tick();
        if (isAnchored()) {
            this.setDeltaMovement(Vec3.ZERO);
            return;
        }
        // a miss, only there to be seen flying off
        this.setPos(this.position().add(this.getDeltaMovement()));
        if (!this.level().isClientSide() && this.tickCount > MISS_TICKS) {
            this.discard();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!this.level().isClientSide() && this.getOwner() instanceof ServerPlayer owner && BY_OWNER.get(owner.getUUID()) == this) {
            BY_OWNER.remove(owner.getUUID());
            if (isAnchored()) {
                Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> owner), new GrapplePacket(false, Vec3.ZERO, false));
            }
        }
    }

    /**
     * Looks after the hooks from here rather than their own tick: far away they sit in chunks that don't tick.
     */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        VIEW_CHUNKS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || BY_OWNER.isEmpty()) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        for (Map.Entry<UUID, GrappleEntity> entry : new ArrayList<>(BY_OWNER.entrySet())) {
            GrappleEntity hook = entry.getValue();
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(entry.getKey());
            if (hook.isRemoved()) {
                BY_OWNER.remove(entry.getKey(), hook);
                continue;
            }
            if (player == null || !player.isAlive() || player.isSpectator() || player.level() != hook.level()
                    || !hook.heldBy(player)
                    || hook.distanceToSqr(player) > (reach(player) + 8.0) * (reach(player) + 8.0)) {
                hook.discard();
                continue;
            }
            if (hook.isAnchored()) {
                player.resetFallDistance();
                // hanging and being pulled isn't flying
                ((ServerGamePacketListenerAccessor) player.connection).taczvr$setAboveGroundTickCount(0);
            }
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < (MAX_LENGTH + 16.0) * (MAX_LENGTH + 16.0);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }
}
