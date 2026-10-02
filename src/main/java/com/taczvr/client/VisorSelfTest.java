package com.taczvr.client;

import com.taczvr.TaczVR;
import com.taczvr.VrCommon;
import com.taczvr.mixin.client.MuzzleFlashRenderAccessor;
import com.taczvr.vr.VrBackends;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;
import com.taczvr.vr.visor.TaczVrVisorAddon;
import com.taczvr.vr.visor.VisorClientBackend;
import com.taczvr.vr.visor.VisorPoses;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.events.SwingBlockVREvent;
import org.vmstudio.visor.api.client.events.render.HandRenderStateVREvent;
import org.vmstudio.visor.api.client.render.decoration.hand.HandRenderState;
import org.vmstudio.visor.api.client.input.InputHelper;
import org.vmstudio.visor.api.client.input.MouseButtonType;
import org.vmstudio.visor.api.common.HandType;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;

/**
 * The in-game self-test with Visor instead of Vivecraft ({@code ./gradlew runClient -Pselftest -Pvisor}).
 * Visor runs without a headset here, so the controller pose is scripted like in {@link SelfTest}, but the trigger and
 * the A button go through Visor's own input path, and Visor's poses and events are checked against Visor itself.
 */
public final class VisorSelfTest {
    private static final ResourceLocation AK = new ResourceLocation("tacz", "ak47");
    private static final Quaternionf NORTH = new Quaternionf();
    private static final Quaternionf EAST = new Quaternionf().rotationY((float) Math.toRadians(-90.0));

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean started = false;
    private static int worldTicks = -1;
    private static int passes = 0;
    private static int failures = 0;

    private static boolean captureBullet = false;
    @Nullable
    private static Vec3 bulletPos;
    @Nullable
    private static Vec3 bulletVel;
    private static Vec3 expectedMuzzle = Vec3.ZERO;
    private static Vec3 expectedDir = Vec3.ZERO;

    @FunctionalInterface
    private interface Step {
        boolean tick(Minecraft mc) throws Exception;
    }

    @FunctionalInterface
    private interface Action {
        void run(Minecraft mc) throws Exception;
    }

    @FunctionalInterface
    private interface Check {
        boolean test(Minecraft mc) throws Exception;
    }

    private VisorSelfTest() {
    }

    public static void register() {
        if (Boolean.getBoolean("taczvr.selftest")) {
            MinecraftForge.EVENT_BUS.register(VisorSelfTest.class);
        }
    }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post event) {
        if (started || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        started = true;
        check("visor: Visor is the VR mod", VrBackends.isVisor() && ModList.get().isLoaded("visor"), VrBackends.common().name());
        check("visor: Vivecraft isn't installed", !ModList.get().isLoaded("vivecraft"), "");
        boolean vivecraftClasses;
        try {
            Class.forName("org.vivecraft.api.VRAPI", false, VisorSelfTest.class.getClassLoader());
            vivecraftClasses = true;
        } catch (ClassNotFoundException e) {
            vivecraftClasses = false;
        }
        check("visor: no Vivecraft classes around, the game started without them", !vivecraftClasses, "");
        try {
            long stamp = MuzzleFlashRenderAccessor.taczvr$getShootTimeStamp();
            pass("visor: our TACZ mixins still applied (stamp=" + stamp + ")");
        } catch (Throwable t) {
            fail("visor: our TACZ mixins still applied", t.toString());
        }

        // TACZ only builds its gun index after joining a world
        Minecraft mc = Minecraft.getInstance();
        String name = "taczvr_selftest_" + System.currentTimeMillis();
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.execute(() -> mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || !started || mc.player == null || mc.level == null) {
            return;
        }
        worldTicks++;
        if (worldTicks == 20) {
            buildSteps();
        }
        if (worldTicks < 20 || STEPS.isEmpty()) {
            return;
        }
        boolean done;
        try {
            done = STEPS.peek().tick(mc);
        } catch (Throwable t) {
            failures++;
            TaczVR.LOGGER.error("[SELFTEST] FAIL step threw", t);
            done = true;
        }
        if (done) {
            STEPS.poll();
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (captureBullet && !event.getLevel().isClientSide() && bulletPos == null
                && event.getEntity() instanceof EntityKineticBullet bullet) {
            bulletVel = bullet.getDeltaMovement();
            bulletPos = bullet.position();
        }
    }

    // --- the script ---

    private static void buildSteps() {
        run(mc -> {
            mc.options.pauseOnLostFocus = false;
            if (mc.screen != null) {
                mc.setScreen(null);
            }
            mc.player.connection.sendCommand("time set 6000");
            mc.player.connection.sendCommand("gamerule doDaylightCycle false");
            mc.player.connection.sendCommand("gamerule doMobSpawning false");
            mc.player.connection.sendCommand("tp @s 0 -60 0 180 0");
            mc.player.connection.sendCommand("gamemode survival");
        });
        sleep(30);
        run(mc -> {
            check("visor: TaCZ VR loaded as a Visor addon", TaczVrVisorAddon.loaded, "");
            check("visor: not in VR without a headset", !VrClient.isVRActive(), "");
            check("visor: you're not a VR player to the server either", !server(sp -> VrCommon.isVRPlayer(sp)), "");
            check("visor: the grip modules were handed over", VisorClientBackend.GRIP_MODULES.size() == 4, "modules=" + VisorClientBackend.GRIP_MODULES.size());
        });
        poseConversion();
        run(mc -> server(sp -> {
            Inventory inv = sp.getInventory();
            inv.clearContent();
            inv.setItem(0, GunItemBuilder.create().setId(AK).setAmmoCount(30).setAmmoInBarrel(true).build());
            ResourceLocation ammo = TimelessAPI.getCommonGunIndex(AK).map(CommonGunIndex::getGunData)
                    .map(data -> data.getAmmoId()).orElseThrow();
            for (int slot = 1; slot <= 3; slot++) {
                inv.setItem(slot, AmmoItemBuilder.create().setId(ammo).setCount(30).build());
            }
            inv.selected = 0;
            sp.connection.send(new ClientboundSetCarriedItemPacket(0));
            sp.inventoryMenu.broadcastChanges();
            return null;
        }));
        await("visor: client holds the AK", 40, mc -> clientAmmo(mc) == 30, () -> "ammo=" + clientAmmo(Minecraft.getInstance()));
        run(mc -> {
            VrCommon.testForceVr = true;
            rig(eye().add(0.1, -0.2, -0.3), new Quaternionf(EAST).rotateX((float) Math.toRadians(15.0)), eye().add(-0.35, -0.35, -0.3));
        });
        sleep(20);
        run(mc -> {
            check("visor: the gun is posed in the hand", VrGunController.lastPose() != null, "");
            check("visor: we draw the gun, Visor's hand doesn't", VrGunRenderer.rendersHeldGun(mc.player, mc.player.getMainHandItem()), "");
            mc.options.hideGui = true;
        });
        sleep(3);
        run(mc -> screenshot(mc, "taczvr_visor_gun.png"));
        sleep(5);
        trigger();
        reload();
        swing();
        run(mc -> {
            VrClient.testPose = null;
            VrCommon.testForceVr = false;
            mc.options.hideGui = false;
            log("DONE, passed=%d failed=%d", passes, failures);
            if (Boolean.getBoolean("taczvr.selftest.exit")) {
                mc.stop();
            }
        });
    }

    /**
     * Visor's own pose objects, filled like Visor fills them from the headset, read the way the backend reads them:
     * the direction has to be the -Z axis of the rotation, or the gun would point off its barrel.
     */
    private static void poseConversion() {
        run(mc -> {
            org.vmstudio.visor.api.common.player.VRPose pose = org.vmstudio.visor.api.common.player.VRPose.create();
            Matrix4f roomRotation = new Matrix4f().rotationYXZ((float) Math.toRadians(40.0), (float) Math.toRadians(25.0), (float) Math.toRadians(10.0));
            Vector3f roomDir = roomRotation.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F));
            Vector3f origin = new Vector3f(10.0F, 64.0F, -5.0F);
            float rotationY = (float) Math.toRadians(30.0);
            pose.update(new Vector3f(0.2F, 1.4F, -0.3F), roomRotation, roomDir, origin, rotationY, 1.0F);
            VrPart part = VisorPoses.wrap(pose);
            if (part == null) {
                fail("visor: a Visor pose reads as a hand", "null");
                return;
            }
            Vector3f axis = part.getRotation().transform(new Vector3f(0.0F, 0.0F, -1.0F));
            double off = Math.toDegrees(Math.acos(Math.min(1.0, new Vec3(axis.x, axis.y, axis.z).dot(part.getDir()))));
            log("visor pose: pos=%s dir=%s -Z=%s", v(part.getPos()), v(part.getDir()), v(new Vec3(axis.x, axis.y, axis.z)));
            check("visor: hand direction is the -Z of its rotation", off < 1.0, String.format(Locale.ROOT, "%.2f deg apart", off));
            double fromOrigin = part.getPos().distanceTo(new Vec3(origin.x, origin.y, origin.z));
            check("visor: hand position is in the world, around the play space origin",
                    Math.abs(fromOrigin - new Vector3f(0.2F, 1.4F, -0.3F).length()) < 0.01, String.format(Locale.ROOT, "%.3f from origin", fromOrigin));
            check("visor: an empty Visor pose is no hand", VisorPoses.wrap(org.vmstudio.visor.api.common.player.VRPose.EMPTY) == null, "");
        });
    }

    /**
     * Visor's trigger presses the left mouse button, TACZ has to shoot from the muzzle where the gun points.
     */
    private static void trigger() {
        run(mc -> {
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            expectedMuzzle = new Vec3(pose.muzzle.x, pose.muzzle.y, pose.muzzle.z);
            expectedDir = pose.bulletDirection(25.0);
            bulletPos = null;
            bulletVel = null;
            captureBullet = true;
            mc.setWindowActive(true);
            grabMouse(mc);
            InputHelper.pressMouse(MouseButtonType.LEFT);
        });
        sleep(2);
        run(mc -> InputHelper.releaseMouse(MouseButtonType.LEFT));
        await("visor: Visor's trigger fires the gun", 20, mc -> bulletPos != null, () -> "no bullet");
        run(mc -> {
            captureBullet = false;
            if (bulletPos == null || bulletVel == null) {
                return;
            }
            Vec3 dir = bulletVel.normalize();
            double fromMuzzle = bulletPos.distanceTo(expectedMuzzle);
            double offAim = Math.toDegrees(Math.acos(Math.min(1.0, dir.dot(expectedDir))));
            double offLook = Math.toDegrees(Math.acos(Math.min(1.0, dir.dot(mc.player.getLookAngle()))));
            check("visor: bullet starts at the muzzle", fromMuzzle < 0.15, String.format(Locale.ROOT, "%.3f blocks off", fromMuzzle));
            check("visor: bullet flies where the gun points", offAim < 12.0, String.format(Locale.ROOT, "%.1f deg off", offAim));
            check("visor: not where the head looks", offLook > 60.0, String.format(Locale.ROOT, "%.1f deg from look", offLook));
        });
        sleep(20);
    }

    /**
     * Visor's A button presses the right mouse button, which reloads.
     */
    private static void reload() {
        run(mc -> {
            check("visor: the shot used a round", clientAmmo(mc) < 30, "ammo=" + clientAmmo(mc));
            check("visor: not reloading before A", !reloading(mc), "");
            InputHelper.pressMouse(MouseButtonType.RIGHT);
        });
        sleep(2);
        run(mc -> InputHelper.releaseMouse(MouseButtonType.RIGHT));
        await("visor: A (right mouse) starts TACZ's reload", 40, VisorSelfTest::reloading,
                () -> "ammo=" + clientAmmo(Minecraft.getInstance()));
        await("visor: and the magazine is full after it", 120, mc -> clientAmmo(mc) == 30 && !reloading(mc),
                () -> "ammo=" + clientAmmo(Minecraft.getInstance()));
    }

    private static boolean reloading(Minecraft mc) {
        return IGunOperator.fromLivingEntity(mc.player).getSynReloadState().getStateType().isReloading();
    }

    /**
     * Visor's roomscale swing can't break blocks while a gun is in the hand, but still can with an empty hand.
     */
    private static void swing() {
        run(mc -> {
            check("visor: Visor's own hand is hidden, a hand is drawn on the grip",
                    handState(HandType.MAIN, HandRenderState.WORLD_HAND) == HandRenderState.OFF, "");
            check("visor: the free off-hand stays", handState(HandType.OFFHAND, HandRenderState.WORLD_HAND) == HandRenderState.WORLD_HAND, "");
            check("visor: a pointing hand in menus stays", handState(HandType.MAIN, HandRenderState.GUI_HAND) == HandRenderState.GUI_HAND, "");
            check("visor: no block breaking swing with a gun", swingCanceled(mc), "the swing went through");
            mc.player.getInventory().selected = 5;
        });
        sleep(3);
        run(mc -> check("visor: an empty hand still swings", !swingCanceled(mc), "it was cancelled"));
        run(mc -> mc.player.getInventory().selected = 0);
    }

    private static HandRenderState handState(HandType hand, HandRenderState state) {
        HandRenderStateVREvent event = new HandRenderStateVREvent(hand, state);
        VisorAPI.eventBus().callEvent(event);
        return event.getState();
    }

    private static boolean swingCanceled(Minecraft mc) {
        Vec3 at = mc.player.position().add(1.0, 0.0, 0.0);
        SwingBlockVREvent event = new SwingBlockVREvent(mc.player, HandType.MAIN, mc.player.getMainHandItem().getItem(),
                mc.level.getBlockState(mc.player.blockPosition().below()),
                new BlockHitResult(at, net.minecraft.core.Direction.UP, mc.player.blockPosition().below(), false), 3.0F);
        VisorAPI.eventBus().callEvent(event);
        return event.isCanceled();
    }

    // --- the fake rig ---

    private static Vec3 eye() {
        return Minecraft.getInstance().player.getEyePosition();
    }

    private static VrPart part(Vec3 pos, Quaternionfc rot) {
        Vector3f d = rot.transform(new Vector3f(0.0F, 0.0F, -1.0F));
        return VrPart.of(pos, new Vec3(d.x, d.y, d.z), new Quaternionf(rot));
    }

    private static void rig(Vec3 main, Quaternionfc rot, @Nullable Vec3 off) {
        VrPose pose = VrPose.of(part(eye(), NORTH), part(main, rot), off == null ? null : part(off, NORTH), false, false);
        VrClient.testPose = pose;
        VrCommon.testPose = pose;
    }

    // --- helpers ---

    private static int clientAmmo(Minecraft mc) {
        ItemStack stack = mc.player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(stack);
        return iGun == null ? -1 : iGun.getCurrentAmmoCount(stack);
    }

    private static <T> T server(Function<ServerPlayer, T> function) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        return server.submit(() -> function.apply(server.getPlayerList().getPlayer(id))).join();
    }

    private static void grabMouse(Minecraft mc) {
        try {
            Field field = MouseHandler.class.getDeclaredField("mouseGrabbed");
            field.setAccessible(true);
            field.setBoolean(mc.mouseHandler, true);
        } catch (Throwable t) {
            log("could not mark the mouse grabbed: %s", t);
        }
    }

    private static void screenshot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> log("screenshot: %s", message.getString()));
    }

    private static String v(Vec3 vec) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", vec.x, vec.y, vec.z);
    }

    // --- step helpers ---

    private static void run(Action action) {
        STEPS.add(mc -> {
            action.run(mc);
            return true;
        });
    }

    private static void sleep(int ticks) {
        int[] left = {ticks};
        STEPS.add(mc -> --left[0] <= 0);
    }

    private static void await(String name, int timeoutTicks, Check check, java.util.function.Supplier<String> detail) {
        int[] waited = {0};
        STEPS.add(mc -> {
            if (check.test(mc)) {
                pass(name + " (" + waited[0] + " ticks)");
                return true;
            }
            if (++waited[0] >= timeoutTicks) {
                fail(name, "timed out: " + detail.get());
                return true;
            }
            return false;
        });
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass(name);
        } else {
            fail(name, detail);
        }
    }

    private static void pass(String name) {
        passes++;
        log("PASS %s", name);
    }

    private static void fail(String name, String detail) {
        failures++;
        log("FAIL %s: %s", name, detail);
    }

    private static void log(String format, Object... args) {
        TaczVR.LOGGER.info("[SELFTEST] " + String.format(Locale.ROOT, format, args));
    }
}
