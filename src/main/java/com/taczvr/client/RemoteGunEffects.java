package com.taczvr.client;

import com.taczvr.mixin.client.AnimationControllerAccessor;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.animation.ObjectAnimation;
import com.tacz.guns.api.client.animation.statemachine.LuaAnimationStateMachine;
import com.tacz.guns.api.event.common.GunFireEvent;
import com.tacz.guns.api.event.common.GunReloadEvent;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.client.animation.statemachine.GunAnimationStateContext;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * What other VR players' guns are doing, so you see their shots, reloads and manual magazine changes.
 * TACZ tells every nearby client when someone fires or reloads; the magazine state comes from this mod's server.
 * TACZ never animates other players' guns, the animations are played here on private copies.
 */
public final class RemoteGunEffects {
    static final long MUZZLE_FLASH_MILLIS = 50;
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Map<BedrockGunModel, Map<String, Optional<ObjectAnimation>>> ANIMATIONS = new WeakHashMap<>();

    private static final class State {
        long fireMillis = -1;
        long offhandFireMillis = -1;
        @Nullable
        String action;
        long actionStart;
        boolean magazineOut;
        boolean holdingMagazine;
    }

    private RemoteGunEffects() {
    }

    @Nullable
    private static Player remote(@Nullable Entity entity, LogicalSide side) {
        Minecraft mc = Minecraft.getInstance();
        return side.isClient() && entity instanceof Player player && player != mc.player ? player : null;
    }

    private static State state(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new State());
    }

    @SubscribeEvent
    public static void onFire(GunFireEvent event) {
        Player shooter = remote(event.getShooter(), event.getLogicalSide());
        if (shooter == null) {
            return;
        }
        State state = state(shooter);
        state.fireMillis = System.currentTimeMillis();
        // the slide/bolt kick, but don't cut a reload short with it
        if (!isReloading(shooter)) {
            start(state, "shoot");
        }
    }

    @SubscribeEvent
    public static void onReload(GunReloadEvent event) {
        Player shooter = remote(event.getEntity(), event.getLogicalSide());
        if (shooter == null) {
            return;
        }
        State state = state(shooter);
        state.magazineOut = false;
        state.holdingMagazine = false;
        start(state, reloadAnimation(event.getGunItemStack()));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
    }

    /**
     * From the server: another player took the magazine out, holds a new one, or put it in.
     */
    public static void setMagazine(int entityId, boolean out, boolean holding) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
        if (entity instanceof Player player && player != mc.player) {
            State state = state(player);
            state.magazineOut = out;
            state.holdingMagazine = holding;
        }
    }

    /**
     * From the server: another player fired their off-hand gun.
     */
    public static void offhandFired(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
        if (entity instanceof Player player && player != mc.player) {
            state(player).offhandFireMillis = System.currentTimeMillis();
        }
    }

    static long offhandFlashStart(Player player) {
        State state = STATES.get(player.getUUID());
        if (state == null || System.currentTimeMillis() - state.offhandFireMillis > MUZZLE_FLASH_MILLIS) {
            return -1;
        }
        return state.offhandFireMillis;
    }

    public static boolean magazineOut(Player player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.magazineOut;
    }

    public static boolean holdingMagazine(Player player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.holdingMagazine;
    }

    /**
     * Millis of the last shot within the muzzle flash time, else -1.
     */
    static long muzzleFlashStart(Player player) {
        State state = STATES.get(player.getUUID());
        if (state == null || System.currentTimeMillis() - state.fireMillis > MUZZLE_FLASH_MILLIS) {
            return -1;
        }
        return state.fireMillis;
    }

    static boolean isReloading(Player player) {
        State state = STATES.get(player.getUUID());
        return state != null && state.action != null && state.action.startsWith("reload");
    }

    /**
     * Poses the model for this player's gun: the rest pose with whatever it is doing on top.
     * Undo with {@link BedrockGunModel#cleanAnimationTransform()}.
     */
    static void apply(Player player, GunDisplayInstance display, BedrockGunModel model) {
        RestPose.apply(display, model);
        State state = STATES.get(player.getUUID());
        if (state == null || state.action == null) {
            return;
        }
        ObjectAnimation animation = animation(display, model, state.action);
        float seconds = (System.currentTimeMillis() - state.actionStart) / 1000.0F;
        if (animation == null || seconds > animation.getMaxEndTimeS()) {
            state.action = null;
            return;
        }
        animation.update(false, seconds * 1.0E9F);
    }

    private static void start(State state, String name) {
        state.action = name;
        state.actionStart = System.currentTimeMillis();
    }

    /**
     * Same choice TACZ makes for your own gun: empty or tactical, with the extended magazine variant if there is one.
     */
    private static String reloadAnimation(ItemStack gun) {
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return "reload_tactical";
        }
        Bolt bolt = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).map(CommonGunIndex::getGunData)
                .map(data -> data.getBolt()).orElse(Bolt.CLOSED_BOLT);
        boolean empty = bolt == Bolt.OPEN_BOLT ? iGun.getCurrentAmmoCount(gun) <= 0 : !iGun.hasBulletInBarrel(gun);
        String name = empty ? "reload_empty" : "reload_tactical";
        boolean extended = !DefaultAssets.isEmptyAttachmentId(iGun.getAttachmentId(gun, AttachmentType.EXTENDED_MAG));
        return extended ? name + "_xmag" : name;
    }

    @Nullable
    private static ObjectAnimation animation(GunDisplayInstance display, BedrockGunModel model, String name) {
        Map<String, Optional<ObjectAnimation>> byName = ANIMATIONS.computeIfAbsent(model, m -> new HashMap<>());
        Optional<ObjectAnimation> cached = byName.get(name);
        if (cached == null) {
            cached = Optional.ofNullable(copy(display, model, name));
            // guns without the extended magazine variant use the normal one
            if (cached.isEmpty() && name.endsWith("_xmag")) {
                cached = Optional.ofNullable(copy(display, model, name.substring(0, name.length() - 5)));
            }
            byName.put(name, cached);
        }
        return cached.orElse(null);
    }

    @Nullable
    private static ObjectAnimation copy(GunDisplayInstance display, BedrockGunModel model, String name) {
        LuaAnimationStateMachine<GunAnimationStateContext> stateMachine = display.getAnimationStateMachine();
        if (stateMachine == null) {
            return null;
        }
        ObjectAnimation prototype = ((AnimationControllerAccessor) stateMachine.getAnimationController())
                .taczvr$getPrototypes().get(name);
        if (prototype == null) {
            return null;
        }
        ObjectAnimation copy = new ObjectAnimation(prototype);
        copy.applyAnimationListeners(model);
        return copy;
    }

    /**
     * For the self-test: pretend TACZ reported this.
     */
    static void testEvent(LivingEntity shooter, ItemStack gun, boolean reload) {
        if (reload) {
            onReload(new GunReloadEvent(shooter, gun, LogicalSide.CLIENT));
        } else {
            onFire(new GunFireEvent(shooter, gun, LogicalSide.CLIENT));
        }
    }
}
