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
import com.taczvr.client.interact.AttachmentModule;
import com.taczvr.content.ModContent;
import com.taczvr.content.NightVisionItem;
import com.taczvr.vr.VrPass;
import com.taczvr.vr.visor.VisorClientEvents;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.client.model.GunModelConstant;
import me.phoenixra.atumvr.api.input.profile.VRInteractionProfileType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.vmstudio.visor.api.client.events.input.ActionButtonVREvent;
import org.vmstudio.visor.api.client.input.action.ActionBinding;
import org.vmstudio.visor.api.client.input.action.framework.VRActionButton;
import java.util.Map;import com.tacz.guns.api.TimelessAPI;
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

    private static final ResourceLocation ACOG = new ResourceLocation("tacz:scope_acog_ta31");
    private static final ResourceLocation GLOCK = new ResourceLocation("tacz:glock_17");
    // stand-ins for Visor's grip actions, the events carry them
    private static final VRActionButton GRIP_MAIN = fakeButton("hotbar_main");
    private static final VRActionButton GRIP_OFF = fakeButton("hotbar_offhand");
    private static Vec3 rigMain = Vec3.ZERO;
    private static Quaternionfc rigRot = NORTH;
    private static int framesBefore;
    @Nullable
    private static VrPass passDuringScope;
    private static boolean scopeEye = false;
    private static final VRActionButton LEFT_TRIGGER = fakeButton("mouse_left_offhand");
    private static boolean gogglesWereOn;

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
            check("visor: the grip modules were handed over", GripDriver.modules().size() == 4 && ClientSetup.modulesRegistered, "modules=" + GripDriver.modules().size());
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
        leftTriggerOneGun();
        reload();
        swing();
        visorIds();
        gripMagazine();
        attachmentsAndScope();
        nightVision();
        dualPistols();
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
     * With one gun, Visor's left trigger isn't a click into the game (Visor's two-handed mode would make it fire the
     * gun in the right hand).
     */
    private static void leftTriggerOneGun() {
        int[] ammo = {0};
        run(mc -> {
            ammo[0] = clientAmmo(mc);
            bulletPos = null;
            captureBullet = true;
            check("left trigger: with one gun its click is skipped", leftTrigger(true), "");
        });
        sleep(10);
        run(mc -> {
            leftTrigger(false);
            captureBullet = false;
            check("left trigger: and the gun doesn't fire", bulletPos == null && clientAmmo(mc) == ammo[0], "ammo " + clientAmmo(mc) + " was " + ammo[0]);
        });
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

    /**
     * The Visor action ids we listen for are still the ones the installed Visor uses.
     */
    private static void visorIds() {
        run(mc -> {
            check("visor: grip is still Visor's hotbar action", "hotbar_main".equals(visorId("game.GameActionHotBar", "ID_MAIN"))
                    && "hotbar_offhand".equals(visorId("game.GameActionHotBar", "ID_OFFHAND")), "");
            check("visor: the left trigger is still the off-hand left mouse action",
                    "mouse_left_offhand".equals(visorId("ActionLeftMouse", "ID_OFFHAND")), "");
        });
    }

    @Nullable
    private static String visorId(String className, String field) {
        for (String base : new String[]{"org.vmstudio.visor.core.client.input.actions.", "org.vmstudio.visor.core.client.input.actions.common."}) {
            try {
                return (String) Class.forName(base + className).getField(field).get(null);
            } catch (ReflectiveOperationException e) {
                // try the next package
            }
        }
        log("visor action class %s not found", className);
        return null;

    }

    /**
     * The grip through Visor's grip action: nothing to grab leaves it to Visor's hotbar, at the magazine it pulls the
     * magazine, at the belt it takes a new one which seats in the magazine well, all without Visor's hotbar opening.
     */
    private static void gripMagazine() {
        int[] inventoryBefore = {0};
        run(mc -> {
            rig(eye().add(0.1, -0.2, -0.3), EAST, idleOff());
            server(sp -> {
                IGun iGun = IGun.getIGunOrNull(sp.getMainHandItem());
                iGun.setCurrentAmmoCount(sp.getMainHandItem(), 30);
                iGun.setBulletInBarrel(sp.getMainHandItem(), true);
                sp.inventoryMenu.broadcastChanges();
                return null;
            });
        });
        sleep(10);
        run(mc -> {
            check("grip: nothing to grab, Visor keeps its grip (hotbar)", !grip(InteractionHand.OFF_HAND, true), "");
            check("grip: and its release", !grip(InteractionHand.OFF_HAND, false), "");
            setOff(VrGunController.lastPose().magazine);
        });
        sleep(2);
        run(mc -> {
            inventoryBefore[0] = serverAmmo()[1];
            check("grip: the magazine module has the off-hand at the magazine",
                    GripDriver.activeModule(InteractionHand.OFF_HAND) instanceof com.taczvr.client.interact.MagazineModule,
                    String.valueOf(GripDriver.activeModule(InteractionHand.OFF_HAND)));
            check("grip: pressing it pulls the magazine, Visor's hotbar stays shut", grip(InteractionHand.OFF_HAND, true), "");
            check("grip: magazine out", MagazineHandler.isMagazineOut(), "");
        });
        run(mc -> check("grip: the release is ours too", grip(InteractionHand.OFF_HAND, false), ""));
        await("grip: the server emptied the gun, rounds back in the inventory", 20,
                mc -> serverAmmo()[0] == 0 && serverAmmo()[1] == inventoryBefore[0] + 30,
                () -> java.util.Arrays.toString(serverAmmo()) + " before=" + inventoryBefore[0]);
        run(mc -> setOff(beltPos()));
        sleep(2);
        run(mc -> {
            check("grip: at the belt it takes a new magazine", grip(InteractionHand.OFF_HAND, true) && MagazineHandler.isHoldingMagazine(), "");
            Vector3d well = GunPoseSolver.boneWorld(VrGunController.lastPose(), GunModelConstant.MAG_NORMAL_NODE);
            setOff(new Vector3d(well).add(0.0, -0.12, 0.0));
        });
        sleep(4);
        run(mc -> {
            check("grip: still holding it while grip is down", MagazineHandler.isHoldingMagazine(), "");
            setOff(GunPoseSolver.boneWorld(VrGunController.lastPose(), GunModelConstant.MAG_NORMAL_NODE));
        });
        await("grip: magazine seated in the well", 10, mc -> !MagazineHandler.isMagazineOut(), () -> "");
        run(mc -> grip(InteractionHand.OFF_HAND, false));
        await("grip: the server loaded 30 rounds from the inventory", 20,
                mc -> serverAmmo()[0] == 30 && serverAmmo()[1] == inventoryBefore[0], () -> java.util.Arrays.toString(serverAmmo()));
        run(mc -> setOff(idleOff()));
        sleep(3);
    }

    /**
     * An attachment in the off-hand snaps on by itself, looking through the scope shows our own zoomed picture
     * (Visor has no spyglass pass), and holding the grip on it takes it off again.
     */
    private static void attachmentsAndScope() {
        run(mc -> server(sp -> {
            sp.setItemInHand(InteractionHand.OFF_HAND, AttachmentItemBuilder.create().setId(ACOG).build());
            sp.inventoryMenu.broadcastChanges();
            return null;
        }));
        await("attach: ACOG in the off-hand", 40, mc -> IAttachment.getIAttachmentOrNull(mc.player.getOffhandItem()) != null, () -> "");
        run(mc -> setOff(new Vector3d(AttachmentModule.slotPosition(VrGunController.lastPose(), AttachmentType.SCOPE)).add(-0.07, 0.09, 0.03)));
        await("attach: it snaps on at its spot, no button", 20, mc -> ACOG.toString().equals(serverScope()), VisorSelfTest::serverScope);
        run(mc -> {
            rig(eye().add(0.13, -0.17, -0.42), NORTH, idleOff());
            // something to look at through the scope: a red pillar 40 blocks north
            mc.player.connection.sendCommand("fill 0 -60 -40 1 -52 -40 minecraft:red_wool");
        });
        sleep(5);
        // aim down the scope: sight line through the eye
        run(mc -> {
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            Vec3 delta = eye().add(0.0, 0.0, -0.1).subtract(pose.origin.x, pose.origin.y, pose.origin.z);
            rig(rigMain.add(delta), NORTH, idleOff());
            framesBefore = ScopeCamera.frames;
        });
        await("scope: aiming through it switches the magnified view on", 30, mc -> ScopeView.isViewing(), () -> "");
        await("scope: our own camera renders the zoomed picture", 20, mc -> ScopeCamera.frames > framesBefore + 3, () -> "frames=" + ScopeCamera.frames);
        run(mc -> {
            check("scope: the eyepiece gets that picture", VrClient.scopeTexture() >= 0, "texture " + VrClient.scopeTexture());
            check("scope: rendered as a scope pass, your own gun stays out of it", passDuringScope == VrPass.SCOPE, String.valueOf(passDuringScope));
            saveScopePicture(mc);
            scopeEye = true;
        });
        sleep(3);
        run(mc -> {
            screenshot(mc, "taczvr_visor_scope.png");
            scopeEye = false;
            rig(eye().add(0.1, -0.2, -0.3), EAST, idleOff());
        });
        await("scope: lowering the gun stops it", 30, mc -> !ScopeView.isViewing() && VrClient.scopeTexture() < 0, () -> "");
        run(mc -> setOff(new Vector3d(AttachmentModule.slotPosition(VrGunController.lastPose(), AttachmentType.SCOPE)).add(-0.06, 0.1, 0.0)));
        sleep(2);
        run(mc -> {
            check("detach: the attachment module has the empty hand at the scope",
                    GripDriver.activeModule(InteractionHand.OFF_HAND) instanceof AttachmentModule, String.valueOf(GripDriver.activeModule(InteractionHand.OFF_HAND)));
            check("detach: grip pressed on it", grip(InteractionHand.OFF_HAND, true), "");
        });
        await("detach: holding grip takes it off into the hand", 40,
                mc -> "tacz:empty".equals(serverScope()) && IAttachment.getIAttachmentOrNull(mc.player.getOffhandItem()) != null, VisorSelfTest::serverScope);
        run(mc -> {
            grip(InteractionHand.OFF_HAND, false);
            setOff(idleOff());
            server(sp -> {
                sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                return null;
            });
        });
        sleep(5);
    }

    /**
     * The goggles switch with the grip at them.
     */
    private static void nightVision() {
        run(mc -> server(sp -> {
            sp.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModContent.NIGHT_VISION_GOGGLES.get()));
            return null;
        }));
        await("goggles: on your head", 20, mc -> mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof NightVisionItem, () -> "");
        run(mc -> {
            VrPart head = VrClient.localTickPose().getHead();
            setOff(head.getPos().add(head.getDir().scale(0.08)).add(0.0, 0.05, 0.0));
        });
        sleep(2);
        run(mc -> {
            gogglesWereOn = server(sp -> NightVisionItem.isOn(sp.getItemBySlot(EquipmentSlot.HEAD)));
            check("goggles: grip at them switches them", grip(InteractionHand.OFF_HAND, true), "");
        });
        run(mc -> grip(InteractionHand.OFF_HAND, false));
        await("goggles: switched over", 20, mc -> server(sp -> NightVisionItem.isOn(sp.getItemBySlot(EquipmentSlot.HEAD))) != gogglesWereOn, () -> "");
        run(mc -> {
            setOff(idleOff());
            server(sp -> {
                sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                return null;
            });
        });
        sleep(3);
    }

    /**
     * A pistol in each hand: Visor's left trigger fires the left pistol from its muzzle, and its click is skipped so the
     * right gun doesn't fire.
     */
    private static void dualPistols() {
        int[] mainAmmo = {0};
        run(mc -> server(sp -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(GLOCK).setAmmoCount(17).setAmmoInBarrel(true).build());
            sp.setItemInHand(InteractionHand.OFF_HAND, GunItemBuilder.create().setId(GLOCK).setAmmoCount(17).setAmmoInBarrel(true).build());
            sp.inventoryMenu.broadcastChanges();
            return null;
        }));
        await("dual: a pistol in each hand", 40, mc -> OffhandGun.holds(mc.player), () -> "");
        run(mc -> rig(eye().add(0.15, -0.2, -0.35), NORTH, eye().add(-0.15, -0.2, -0.35)));
        sleep(20);
        run(mc -> {
            mainAmmo[0] = clientAmmo(mc);
            GunPoseSolver.Pose pose = OffhandGun.lastPose();
            expectedMuzzle = new Vec3(pose.muzzle.x, pose.muzzle.y, pose.muzzle.z);
            expectedDir = pose.bulletDirection(25.0);
            bulletPos = null;
            bulletVel = null;
            captureBullet = true;
            // what Visor does when the left trigger goes down
            int skippedBefore = VisorClientEvents.leftTriggerSkipped;
            boolean skipped = leftTrigger(true);
            check("dual: the left trigger counts as down", VrClient.offHandTriggerDown(), "");
            check("dual: its click was skipped, no left click into the game", skipped && VisorClientEvents.leftTriggerSkipped == skippedBefore + 1, "");
        });
        await("dual: the left trigger fires the left pistol", 20, mc -> bulletPos != null, () -> "no bullet");
        run(mc -> {
            captureBullet = false;
            leftTrigger(false);
            if (bulletPos == null || bulletVel == null) {
                return;
            }
            double fromMuzzle = bulletPos.distanceTo(expectedMuzzle);
            double off = Math.toDegrees(Math.acos(Math.min(1.0, bulletVel.normalize().dot(expectedDir))));
            check("dual: bullet starts at the left pistol's muzzle", fromMuzzle < 0.2, String.format(Locale.ROOT, "%.3f", fromMuzzle));
            check("dual: and flies where it points", off < 5.0, String.format(Locale.ROOT, "%.1f deg", off));
        });
        sleep(5);
        run(mc -> check("dual: let go, the left trigger is up", !VrClient.offHandTriggerDown(), ""));
        run(mc -> check("dual: the right pistol didn't fire", clientAmmo(mc) == mainAmmo[0], "ammo " + clientAmmo(mc) + " was " + mainAmmo[0]));
        run(mc -> server(sp -> {
            sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            return null;
        }));
        sleep(5);
    }

    /**
     * What Visor sends when its grip action is pressed or let go, through Visor's own event bus.
     *
     * @return whether the press was taken from Visor
     */
    private static boolean grip(InteractionHand hand, boolean press) {
        VRActionButton button = hand == InteractionHand.MAIN_HAND ? GRIP_MAIN : GRIP_OFF;
        ActionButtonVREvent event = new ActionButtonVREvent(button, press);
        VisorAPI.eventBus().callEvent(event);
        return event.isCanceled();
    }

    /**
     * What Visor sends when its left trigger goes down or up.
     *
     * @return whether the click was skipped
     */
    private static boolean leftTrigger(boolean press) {
        ActionButtonVREvent event = new ActionButtonVREvent(LEFT_TRIGGER, press);
        VisorAPI.eventBus().callEvent(event);
        return event.isCanceled();
    }

    private static VRActionButton fakeButton(String id) {
        return new VRActionButton(null, id) {
            @Override
            protected void onPress() {
            }

            @Override
            protected void onRelease() {
            }

            @Override
            public @NotNull Map<VRInteractionProfileType, ActionBinding> getDefaultBindings() {
                return Map.of();
            }
        };
    }

    /**
     * Watches the scope render and Visor's left trigger click, from our side.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        if (ScopeCamera.isRendering()) {
            passDuringScope = VrClient.currentPass();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GunPoseSolver.Pose pose = VrGunController.lastPose();
        int texture = VrClient.scopeTexture();
        // what the eye sees with the scope picture on the eyepiece (the flat view has no VR pass to draw it)
        if (scopeEye && pose != null && texture >= 0 && mc.player != null) {
            ScopeView.drawEyepiece(event.getPoseStack(), event.getCamera().getPosition(), pose, texture);
        }
    }


    /**
     * How much of a picture the red pillar fills.
     */
    private static double redShare(com.mojang.blaze3d.platform.NativeImage image) {
        int red = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int abgr = image.getPixelRGBA(x, y);
                int r = abgr & 0xFF;
                int g = (abgr >> 8) & 0xFF;
                int b = (abgr >> 16) & 0xFF;
                if (r > 120 && g < 70 && b < 70) {
                    red++;
                }
            }
        }
        return (double) red / (image.getWidth() * image.getHeight());
    }

    private static void saveScopePicture(Minecraft mc) {
        try (com.mojang.blaze3d.platform.NativeImage image = Screenshot.takeScreenshot(ScopeCamera.target());
             com.mojang.blaze3d.platform.NativeImage view = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            double scoped = redShare(image);
            double eye = redShare(view);
            log("red pillar: %.4f of the scope picture, %.4f of the normal view", scoped, eye);
            check("scope: the picture is zoomed in, the far pillar is much bigger in it", scoped > 0.02 && scoped > eye * 10.0,
                    String.format(Locale.ROOT, "scope %.4f, eye %.4f", scoped, eye));
            // the screenshot fills in alpha, read the texture as it is
            int alpha;
            try (com.mojang.blaze3d.platform.NativeImage raw = new com.mojang.blaze3d.platform.NativeImage(
                    ScopeCamera.target().width, ScopeCamera.target().height, false)) {
                com.mojang.blaze3d.systems.RenderSystem.bindTexture(ScopeCamera.target().getColorTextureId());
                raw.downloadTexture(0, false);
                alpha = (raw.getPixelRGBA(5, 5) >>> 24) & 0xFF;
            }
            check("scope: the picture is opaque, the eyepiece shader doesn't skip the sky", alpha == 255, "alpha " + alpha);
            java.nio.file.Path out = mc.gameDirectory.toPath().resolve("screenshots").resolve("taczvr_visor_scope_view.png");
            java.nio.file.Files.createDirectories(out.getParent());
            image.writeToFile(out);
            log("scope picture saved to %s", out);
        } catch (Exception e) {
            log("could not save the scope picture: %s", e);
        }
    }

    private static int[] serverAmmo() {
        return server(sp -> {
            ItemStack gun = sp.getMainHandItem();
            IGun iGun = IGun.getIGunOrNull(gun);
            int inventory = 0;
            for (ItemStack stack : sp.getInventory().items) {
                com.tacz.guns.api.item.IAmmo ammo = com.tacz.guns.api.item.IAmmo.getIAmmoOrNull(stack);
                if (ammo != null && iGun != null && ammo.isAmmoOfGun(gun, stack)) {
                    inventory += stack.getCount();
                }
            }
            return new int[]{iGun == null ? -1 : iGun.getCurrentAmmoCount(gun), inventory};
        });
    }

    private static String serverScope() {
        return server(sp -> {
            IGun iGun = IGun.getIGunOrNull(sp.getMainHandItem());
            return iGun == null ? "none" : iGun.getAttachmentId(sp.getMainHandItem(), AttachmentType.SCOPE).toString();
        });
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
        rigMain = main;
        rigRot = rot;
        VrPose pose = VrPose.of(part(eye(), NORTH), part(main, rot), off == null ? null : part(off, NORTH), false, false);
        VrClient.testPose = pose;
        VrCommon.testPose = pose;
    }

    private static void setOff(Vec3 off) {
        rig(rigMain, rigRot, off);
    }

    private static void setOff(Vector3d off) {
        setOff(new Vec3(off.x, off.y, off.z));
    }

    private static Vec3 idleOff() {
        return eye().add(-0.35, -0.35, -0.3);
    }

    private static Vec3 beltPos() {
        return eye().add(-0.15, -0.75, 0.05);
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
