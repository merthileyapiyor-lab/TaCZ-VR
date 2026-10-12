package com.taczvr.client;

import com.taczvr.TaczVRConfig;
import com.taczvr.VrCommon;
import com.taczvr.vr.VrHand;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * LesRaisins Tactical Equipments melee weapons (and packs built on it) in VR: swinging the knife runs LesRaisins' own
 * attack, with its damage factors, combos, cooldowns and sounds, aimed where the hand points. A quick thrust along the
 * blade is its special attack, the trigger its normal one. Vivecraft's own swing, a plain hit with the base damage, is
 * off while one is held so nothing hits twice. LesRaisins is only looked up by name: nothing of it is changed or
 * shipped, and without it this does nothing.
 */
public final class LrMeleeVr {
    // meters per second of the hand, body movement taken out
    private static final double SWING_SPEED = 2.0;
    private static final double THRUST_SPEED = 1.8;
    // a thrust goes mostly along the blade
    private static final double THRUST_SHARE = 0.75;
    // the hand has to slow down below this before the next swing counts
    private static final double REARM_SPEED = 0.8;
    // what the blade touches is hit too, whatever the pack's hitbox says
    private static final double BLADE = 0.35;

    private static volatile boolean looked = false;
    @Nullable
    private static Lr lr;
    @Nullable
    private static Vec3 lastMain;
    private static boolean lastInRoom;
    private static boolean armed = true;
    static int attacks = 0;
    static int specials = 0;
    static int requests = 0;
    static int lastTargets = 0;

    private LrMeleeVr() {
    }

    /**
     * Whether a LesRaisins melee weapon in the main hand takes over Vivecraft's roomscale swing.
     */
    public static boolean takesOverSwing(Player player) {
        return TaczVRConfig.CLIENT.lrMeleeSwing.get() && isMelee(player.getMainHandItem()) && lr() != null;
    }

    static boolean isMelee(ItemStack stack) {
        Lr l = stack.isEmpty() ? null : lr();
        return l != null && l.meleeWeapon.isInstance(stack.getItem());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (event.phase != TickEvent.Phase.END || player == null || mc.level == null) {
            return;
        }
        VrPose pose = VrClient.isVRActive() ? VrClient.localTickPose() : null;
        VrPart main = pose == null ? null : pose.getMainHand();
        if (main == null || player.isSpectator() || mc.screen != null || !takesOverSwing(player)) {
            lastMain = null;
            armed = true;
            return;
        }
        Vec3 pos = main.getPos();
        Vec3 dir = main.getDir();
        // how fast the hand moves is measured in the room: a snap turn or a teleport swings the hand around in the
        // game but isn't a swing
        VrPose room = VrClient.latestRoomPose();
        VrPart roomMain = room == null ? null : room.getMainHand();
        boolean inRoom = roomMain != null;
        Vec3 movePos = inRoom ? roomMain.getPos() : pos;
        Vec3 moveDir = inRoom ? roomMain.getDir() : dir;
        if (lastMain != null && lastInRoom == inRoom) {
            Vec3 moved = movePos.subtract(lastMain);
            if (!inRoom) {
                moved = moved.subtract(player.position().subtract(player.xo, player.yo, player.zo));
            }
            Vec3 velocity = moved.scale(20.0);
            double speed = velocity.length();
            double forward = velocity.dot(moveDir);
            if (!armed) {
                armed = speed < REARM_SPEED;
            } else if (forward >= THRUST_SPEED && forward >= speed * THRUST_SHARE) {
                armed = !attack(player, pos, dir, true);
            } else if (speed >= SWING_SPEED) {
                armed = !attack(player, pos, dir, false);
            }
        }
        lastMain = movePos;
        lastInRoom = inRoom;
    }

    /**
     * The trigger is LesRaisins' normal attack too, aimed with the hand instead of the head.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (!event.isAttack() || player == null || !VrClient.isVRActive() || !takesOverSwing(player)) {
            return;
        }
        VrPose pose = VrClient.localTickPose();
        VrPart main = pose == null ? null : pose.getMainHand();
        if (main != null) {
            attack(player, main.getPos(), main.getDir(), false);
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    /**
     * Runs LesRaisins' attack from the hand: its own preparation (cooldown, combo count, swing sound), then the hit
     * request right away with what the hand points at, since the swing in VR has already happened.
     *
     * @return whether LesRaisins took the attack (it says no while cooling down or drawing the weapon)
     */
    static boolean attack(LocalPlayer player, Vec3 hand, Vec3 dir, boolean thrust) {
        Lr l = lr();
        ItemStack stack = player.getMainHandItem();
        if (l == null || !l.meleeWeapon.isInstance(stack.getItem())) {
            return false;
        }
        try {
            Object combat = l.combat(player);
            if (combat == null) {
                return false;
            }
            Object action = thrust && l.hasAction(stack, l.right) ? l.right : l.left;
            List<?> tasks = l.tasks(combat);
            int tasksBefore = tasks.size();
            Vec3 motion = player.getDeltaMovement();
            float yRot = player.getYRot();
            float xRot = player.getXRot();
            boolean prepared;
            // an attack without a delay collects its targets inside this call, along the look: make that the hand
            player.setYRot((float) (Mth.atan2(-dir.x, dir.z) * Mth.RAD_TO_DEG));
            player.setXRot((float) (-Mth.atan2(dir.y, dir.horizontalDistance()) * Mth.RAD_TO_DEG));
            try {
                prepared = (Boolean) l.preAttack.invoke(combat, action, hand, dir);
            } finally {
                player.setYRot(yRot);
                player.setXRot(xRot);
            }
            if (!prepared) {
                return false;
            }
            // no lunge forward: moving you without your legs is what makes VR sick
            player.setDeltaMovement(motion);
            boolean delayed = false;
            for (int i = tasks.size() - 1; i >= tasksBefore; i--) {
                Object task = tasks.get(i);
                if (l.delayAttack.isInstance(task)) {
                    delayed = true;
                    tasks.remove(i);
                } else if (l.delayMove.isInstance(task)) {
                    tasks.remove(i);
                }
            }
            List<Entity> targets = targets(l, player, stack, action, hand, dir);
            if (delayed) {
                int count = (Integer) l.getActionCount.invoke(combat, action) - 1;
                l.send.invoke(l.channel, l.request.newInstance(action, count, targets));
                requests++;
            }
            lastTargets = targets.size();
            attacks++;
            if (action == l.right) {
                specials++;
            }
            if (TaczVRConfig.CLIENT.haptics.get()) {
                if (targets.isEmpty()) {
                    VrClient.haptic(VrHand.MAIN_HAND, 0.03F, 0.3F);
                } else {
                    VrClient.haptic(VrHand.MAIN_HAND, 0.08F, action == l.right ? 1.0F : 0.8F);
                }
            }
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            VrCommon.logOnce(e);
            return false;
        }
    }

    /**
     * The pack's own hitbox from the eyes along the hand, plus whatever the blade itself touches.
     */
    private static List<Entity> targets(Lr l, LocalPlayer player, ItemStack stack, Object action, Vec3 hand, Vec3 dir)
            throws ReflectiveOperationException {
        Set<Entity> found = new LinkedHashSet<>();
        Object collected = l.collectTargets.invoke(stack.getItem(), player, stack, action, player.getEyePosition(), dir);
        if (collected instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Entity e) {
                    found.add(e);
                }
            }
        }
        Vec3 tip = hand.add(dir.scale(BLADE));
        for (Entity e : player.level().getEntities(player, new AABB(hand, tip).inflate(0.3))) {
            if (e instanceof LivingEntity living && living.isAlive() && !e.isSpectator()
                    && (e.getBoundingBox().inflate(0.1).contains(tip) || e.getBoundingBox().inflate(0.1).clip(hand, tip).isPresent())) {
                found.add(e);
            }
        }
        List<Entity> targets = new ArrayList<>(found);
        // LesRaisins' server takes 32 at most by default
        return targets.size() > 32 ? targets.subList(0, 32) : targets;
    }

    @Nullable
    private static Lr lr() {
        if (!looked) {
            try {
                lr = new Lr();
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
                lr = null;
            }
            looked = true;
        }
        return lr;
    }

    /**
     * The bits of LesRaisins used here, found by name.
     */
    private static final class Lr {
        final Class<?> meleeWeapon;
        final Capability<?> capability;
        final Method preAttack;
        final Method getActionCount;
        final Field delayedActions;
        final Class<?> delayAttack;
        final Class<?> delayMove;
        final Method collectTargets;
        final Method getMeleeIndex;
        final Constructor<?> request;
        final Object channel;
        final Method send;
        final Object left;
        final Object right;

        @SuppressWarnings({"unchecked", "rawtypes"})
        Lr() throws ReflectiveOperationException {
            String base = "me.xjqsh.lrtactical.";
            meleeWeapon = Class.forName(base + "api.item.IMeleeWeapon");
            Class<?> actionClass = Class.forName(base + "api.melee.MeleeAction");
            left = Enum.valueOf((Class<Enum>) actionClass, "LEFT");
            right = Enum.valueOf((Class<Enum>) actionClass, "RIGHT");
            capability = (Capability<?>) Class.forName(base + "capability.CombatPropertiesProvider").getField("CAPABILITY").get(null);
            Class<?> combat = Class.forName(base + "capability.CombatProperties");
            preAttack = combat.getMethod("preAttack", actionClass, Vec3.class, Vec3.class);
            getActionCount = combat.getMethod("getActionCount", actionClass);
            delayedActions = combat.getDeclaredField("delayedActions");
            delayedActions.setAccessible(true);
            delayAttack = Class.forName(base + "capability.CombatProperties$DelayAttack");
            delayMove = Class.forName(base + "capability.CombatProperties$DelayMove");
            collectTargets = meleeWeapon.getMethod("collectTargets", Player.class, ItemStack.class, actionClass, Vec3.class, Vec3.class);
            getMeleeIndex = meleeWeapon.getMethod("getMeleeIndex", ItemStack.class);
            request = Class.forName(base + "network.message.CMeleeAttackRequest").getConstructor(actionClass, int.class, List.class);
            channel = Class.forName(base + "network.NetworkHandler").getField("CHANNEL").get(null);
            send = channel.getClass().getMethod("sendToServer", Object.class);
        }

        @Nullable
        Object combat(Player player) {
            return player.getCapability(capability).resolve().orElse(null);
        }

        List<?> tasks(Object combat) throws IllegalAccessException {
            return (List<?>) delayedActions.get(combat);
        }

        /**
         * Whether the weapon has an attack for that action at all (a pack can leave the special one out).
         */
        boolean hasAction(ItemStack stack, Object action) {
            try {
                Optional<?> index = (Optional<?>) getMeleeIndex.invoke(stack.getItem(), stack);
                if (index.isEmpty()) {
                    return false;
                }
                Object data = index.get().getClass().getMethod("getData").invoke(index.get());
                Object combatData = data.getClass().getMethod("getAttackInfo").invoke(data);
                return combatData != null
                        && combatData.getClass().getMethod("getAttackInfo", action.getClass()).invoke(combatData, action) != null;
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }
    }
}
