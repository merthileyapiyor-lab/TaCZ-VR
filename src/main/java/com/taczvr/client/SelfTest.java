package com.taczvr.client;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.taczvr.TaczVR;
import com.taczvr.TaczVRConfig;
import com.taczvr.VrCommon;
import com.taczvr.content.GrappleEntity;
import com.taczvr.content.GrenadeEntity;
import com.taczvr.content.ModContent;
import com.taczvr.content.NightVisionItem;
import com.taczvr.client.interact.NightVisionModule;
import com.taczvr.server.RadioState;
import com.taczvr.server.ZombieShop;
import com.taczvr.network.ShopActionPacket;
import com.taczvr.content.MedkitItem;
import com.taczvr.server.Tactical;
import com.taczvr.compat.RadioFilter;
import com.taczvr.compat.RadioSelfTest;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.RegistryObject;
import com.taczvr.network.GameCommandPacket;
import com.taczvr.network.Net;
import com.taczvr.network.HighFivePacket;
import com.taczvr.server.GameManager;
import com.taczvr.server.MuzzleLight;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import com.taczvr.server.ServerAimStore;
import com.taczvr.server.ServerAssist;
import com.taczvr.server.ShieldBlock;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import com.taczvr.client.interact.AttachmentModule;
import com.taczvr.client.interact.MagazineModule;
import com.taczvr.mixin.client.BedrockModelAccessor;
import com.taczvr.mixin.client.MuzzleFlashRenderAccessor;
import com.taczvr.network.HandoffPacket;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.client.gameplay.IClientPlayerGunOperator;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import com.tacz.guns.client.model.GunModelConstant;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.entity.EntityKineticBullet;
import com.tacz.guns.resource.index.CommonGunIndex;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.vivecraft.api.data.FBTMode;
import com.taczvr.vr.VrPart;
import com.taczvr.vr.VrPose;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.client.VivecraftVRMod;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction;
import org.vivecraft.client_vr.settings.VRSettings;
import org.vivecraft.common.network.VrPlayerState;
import org.vivecraft.client_vr.render.helpers.VRArmHelper;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Development check, only runs with -Dtaczvr.selftest=true (./gradlew runClient -Pselftest).
 * <p>
 * Verifies every mixin target got patched, opens a flat test world and then plays the local player as a fake VR
 * player: the real per tick code runs on a scripted controller pose, buttons are pressed the way Vivecraft presses
 * them and the interact modules get the calls Vivecraft would make. Results are checked on the integrated server.
 * Screenshots go to run/screenshots, every result line starts with "[SELFTEST]".
 */
public final class SelfTest {
    private static final UUID FAKE_PLAYER = UUID.randomUUID();
    private static final String[] GUNS = {"tacz:ak47", "tacz:glock_17", "tacz:m4a1"};
    private static final ResourceLocation AK = new ResourceLocation("tacz:ak47");
    private static final ResourceLocation ACOG = new ResourceLocation("tacz:scope_acog_ta31");
    private static final ResourceLocation ELCAN = new ResourceLocation("tacz:scope_elcan_4x");
    private static final ResourceLocation LASER = new ResourceLocation("tacz:laser_peq15");
    private static final ResourceLocation GLOCK = new ResourceLocation("tacz:glock_17");
    private static int drawnBefore;
    private static int shotsBefore;
    private static final Quaternionf NORTH = new Quaternionf();
    private static final Quaternionf EAST = new Quaternionf().rotationY((float) Math.toRadians(-90.0));

    private static boolean started = false;
    private static int failures = 0;
    private static int passes = 0;
    private static int worldTicks = -1;
    private static int previewView = -1;
    private static boolean scopeEye = false;

    private static final Deque<Step> STEPS = new ArrayDeque<>();

    // the fake VR rig, in world space
    private static Vec3 rigMain = Vec3.ZERO;
    private static Quaternionfc rigRot = NORTH;
    @Nullable
    private static Vec3 rigOff = null;
    private static Vec3 roomHand = Vec3.ZERO;

    // values carried between steps
    private static int inventoryBefore;
    private static int hapticsBefore;
    private static int skippedBefore;
    private static Vec3 expectedMuzzle = Vec3.ZERO;
    private static Vec3 expectedDir = Vec3.ZERO;
    private static final MagazineModule MAGAZINE = new MagazineModule();
    private static final AttachmentModule ATTACHMENTS = new AttachmentModule();
    @Nullable
    private static Pig pig;
    private static float pigHealth;
    @Nullable
    private static Pig target;
    private static int hitsBefore;
    private static int killsBefore;
    private static int shieldBefore;
    private static int lasersBefore;
    private static int echoesBefore;
    private static int flashesBefore;
    private static int slapsBefore;
    private static int highFivesBefore;
    private static Vec3 slapAt = Vec3.ZERO;
    private static volatile boolean testLeftHanded = false;
    private static int framesBefore;
    private static int puffsBefore;
    private static int stabsBefore;
    private static int injectionsBefore;
    private static float knifeFront;
    private static int pressesBefore;
    private static int radioBefore;
    private static int modelsBefore;
    private static int ammoBefore;
    private static int whizBefore;
    private static boolean nightVisionWasOn;
    private static int leftBefore;
    @Nullable
    private static RemotePlayer lefty;

    private static final UUID REMOTE_ID = UUID.nameUUIDFromBytes("taczvr_selftest_remote".getBytes());
    private static final int REMOTE_ENTITY_ID = -4242;
    @Nullable
    private static RemotePlayer remote;
    private static boolean remoteTwoHands;
    private static int fittedBefore;
    private static VRSettings.PlayerModelType savedModelType = VRSettings.PlayerModelType.VANILLA;

    private static volatile boolean captureBullet = false;
    @Nullable
    private static volatile Vec3 bulletPos = null;
    @Nullable
    private static volatile Vec3 bulletVel = null;

    @FunctionalInterface
    private interface Step {
        /**
         * @return true once the step is finished
         */
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

    private record GunState(int ammo, boolean barrel, String scope, int inventoryAmmo, String offHand, boolean hasGun) {
    }

    private SelfTest() {
    }

    public static void register() {
        if (Boolean.getBoolean("taczvr.selftest")) {
            MinecraftForge.EVENT_BUS.register(SelfTest.class);
        }
    }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post event) {
        if (started || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        started = true;
        patched("com.tacz.guns.entity.shooter.LivingEntityShoot");
        patched("com.tacz.guns.entity.EntityKineticBullet");
        patched("com.tacz.guns.client.event.CameraSetupEvent");
        patched("com.tacz.guns.client.input.AimKey");
        patched("com.tacz.guns.client.event.RenderCrosshairEvent");
        patched("org.vivecraft.client_vr.gameplay.trackers.SwingTracker");
        patched("net.minecraft.client.renderer.ItemInHandRenderer");
        patched("com.tacz.guns.client.event.TickAnimationEvent");
        patched("com.tacz.guns.item.ModernKineticGunItem");
        patched("org.vivecraft.client_vr.VRData");
        patched("org.vivecraft.client_vr.provider.VRRenderer");
        patched("net.minecraft.client.renderer.GameRenderer");
        patched("org.vivecraft.client_vr.render.helpers.VRArmHelper");
        patched("org.vivecraft.client.render.VRPlayerModel");
        patched("org.vivecraft.client.render.VRPlayerModel_WithArms");
        try {
            boolean accessor = BedrockModelAccessor.class.isAssignableFrom(BedrockModel.class);
            long stamp = MuzzleFlashRenderAccessor.taczvr$getShootTimeStamp();
            check("accessors (BedrockModel, MuzzleFlashRender stamp=" + stamp + ")", accessor, "");
        } catch (Throwable t) {
            fail("accessors", t.toString());
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
        pushRemoteState();
        if (worldTicks < 20 || STEPS.isEmpty()) {
            return;
        }
        Step step = STEPS.peek();
        boolean done;
        try {
            done = step.tick(mc);
        } catch (Throwable t) {
            failures++;
            TaczVR.LOGGER.error("[SELFTEST] FAIL step threw", t);
            done = true;
        }
        if (done) {
            STEPS.poll();
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
            mc.player.connection.sendCommand("weather clear");
            mc.player.connection.sendCommand("gamerule doDaylightCycle false");
            mc.player.connection.sendCommand("gamerule doMobSpawning false");
            mc.player.connection.sendCommand("tp @s 0 -60 0 180 0");
            mc.player.connection.sendCommand("gamemode survival");
            mc.player.connection.sendCommand("effect give @s minecraft:resistance 600 4 true");
        });
        sleep(30);
        run(mc -> {
            grabMouse(mc);
            mc.options.hideGui = true;
            mc.options.fov().set(100);
            for (String gun : GUNS) {
                poseMath(gun);
            }
            scopeAndSlots();
            check("Vivecraft asked for our interact modules", ClientSetup.modulesRegistered, "handler never called");
            check("no gun held: Vivecraft's own hands stay", !VrGunRenderer.hidesFirstPersonHand(InteractionHand.MAIN_HAND), "");
        });
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
        await("client holds the AK", 40, mc -> clientAmmo(mc) == 30, () -> "ammo=" + clientAmmo(Minecraft.getInstance()));
        run(mc -> {
            VrCommon.testForceVr = true;
            restPose();
        });
        sleep(20);

        // -Ptestonly=models runs just that part, for quick looks while working on it
        if ("models".equals(System.getProperty("taczvr.selftest.only"))) {
            run(mc -> {
                VrCommon.testForceVr = false;
                VrClient.testPose = null;
            });
            models3d();
            finish();
            return;
        }
        if ("zombie".equals(System.getProperty("taczvr.selftest.only"))) {
            run(mc -> {
                VrCommon.testForceVr = false;
                VrClient.testPose = null;
            });
            zombieDowned();
            bulletWhiz();
            finish();
            return;
        }
        if ("hook".equals(System.getProperty("taczvr.selftest.only"))) {
            run(mc -> {
                VrCommon.testForceVr = false;
                VrClient.testPose = null;
            });
            grapplingHook();
            finish();
            return;
        }
        if ("hint".equals(System.getProperty("taczvr.selftest.only"))) {
            attachmentHint();
            finish();
            return;
        }
        if ("lr".equals(System.getProperty("taczvr.selftest.only"))) {
            lesRaisins();
            finish();
            return;
        }
        if ("deagle".equals(System.getProperty("taczvr.selftest.only"))) {
            pistolScope();
            finish();
            return;
        }
        if ("update".equals(System.getProperty("taczvr.selftest.only"))) {
            updateCheck();
            finish();
            return;
        }
        firstPersonHands();
        buttonReload();
        trigger();
        twoHandedTrigger();
        meleeThrust();
        manualMagazine();
        attachmentsAndScope();
        handoff();
        assist();
        gameRound();
        shield();
        grenade();
        echoAndLight();
        dualPistols();
        remotePlayer();
        leftHanded();
        creativeTab();
        flashbang();
        smokeGrenade();
        knife();
        medkit();
        nightVision();
        grapplingHook();
        radio();
        run(mc -> {
            VrCommon.testForceVr = true;
            restPose();
        });
        pistolScope();
        run(mc -> {
            VrClient.testPose = null;
            VrCommon.testForceVr = false;
        });
        bulletWhiz();
        attachmentHint();
        lesRaisins();
        models3d();
        aimWithoutServerVrMod();
        updateCheck();
        previews();
    }

    /**
     * A server whose VR mod doesn't know you're in VR (a server without Vivecraft, for example) still fires from your
     * muzzle: the aim our client sends is enough.
     */
    private static void aimWithoutServerVrMod() {
        run(mc -> server(sp -> {
            sp.getInventory().setItem(0, GunItemBuilder.create().setId(AK).setAmmoCount(30).setAmmoInBarrel(true).build());
            sp.getInventory().selected = 0;
            sp.connection.send(new ClientboundSetCarriedItemPacket(0));
            sp.inventoryMenu.broadcastChanges();
            return null;
        }));
        run(mc -> {
            // the server's VR mod says no, the client is in VR
            VrCommon.testForceVr = false;
            rig(eye().add(0.1, -0.2, -0.3), new Quaternionf(EAST).rotateX((float) Math.toRadians(15.0)), idleOff());
        });
        await("server without VR mod: AK in hand", 40, mc -> clientAmmo(mc) == 30, () -> "ammo=" + clientAmmo(Minecraft.getInstance()));
        sleep(30);
        run(mc -> {
            check("server without VR mod: it doesn't know you're in VR", !server(sp -> VrCommon.isVRPlayer(sp)), "");
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            expectedMuzzle = new Vec3(pose.muzzle.x, pose.muzzle.y, pose.muzzle.z);
            expectedDir = pose.bulletDirection(25.0);
            bulletPos = null;
            bulletVel = null;
            captureBullet = true;
            mc.setWindowActive(true);
            grabMouse(mc);
            VRInputAction.setKeyBindState(mc.options.keyAttack, true);
        });
        sleep(2);
        run(mc -> mc.options.keyAttack.setDown(false));
        await("server without VR mod: a bullet was fired", 20, mc -> bulletPos != null, () -> "none");
        run(mc -> {
            captureBullet = false;
            if (bulletPos == null || bulletVel == null) {
                return;
            }
            double fromMuzzle = bulletPos.distanceTo(expectedMuzzle);
            double offAim = Math.toDegrees(Math.acos(Math.min(1.0, bulletVel.normalize().dot(expectedDir))));
            check("server without VR mod: bullet still starts at the muzzle", fromMuzzle < 0.15, String.format(Locale.ROOT, "%.3f blocks off", fromMuzzle));
            check("server without VR mod: and flies where the gun points", offAim < 12.0, String.format(Locale.ROOT, "%.1f deg off", offAim));
            VrClient.testPose = null;
        });
        sleep(10);
    }

    /**
     * The update check against a small local server: it finds the newer release, shows Update TaCZ VR with Later
     * and Quit Game, and Quit Game downloads the jar and swaps it in (in a test folder, without really quitting).
     * A locked jar is swapped by a helper once the game has closed, run/taczvr-update-test/locked shows it afterwards.
     */
    private static void updateCheck() {
        com.sun.net.httpserver.HttpServer[] http = {null};
        String[] base = {null};
        UpdateChecker.Release[] release = {null};
        byte[][] served = {null};
        boolean[] stopped = {false};
        java.nio.file.Path[] dir = {null};
        java.io.RandomAccessFile[] lock = {null};
        Process[] sleeper = {null};
        run(mc -> {
            check("update: 1.3.10 is newer than 1.3.9", UpdateChecker.compareVersions("1.3.10", "1.3.9") > 0, "");
            check("update: v1.4.0 is newer than 1.3.8", UpdateChecker.compareVersions("v1.4.0", "1.3.8") > 0, "");
            check("update: 1.3.8 is not newer than itself", UpdateChecker.compareVersions("1.3.8", "v1.3.8") == 0, "");
            served[0] = fakeJar("modId = \"taczvr\"");
            byte[] bad = fakeJar("modId = \"something_else\"");
            http[0] = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
            base[0] = "http://127.0.0.1:" + http[0].getAddress().getPort();
            String jarName = "taczvr-1.20.1-9.9.9.jar";
            String latest = "{\"tag_name\":\"v9.9.9\",\"body\":\"## What's new\\r\\n- **Longer** grappling hook\\r\\n- The `update` menu\","
                    + "\"assets\":[{\"name\":\"taczvr-1.20.1-9.9.9-sources.zip\",\"size\":3,\"browser_download_url\":\"" + base[0] + "/nope\"},"
                    + "{\"name\":\"" + jarName + "\",\"size\":" + served[0].length + ",\"browser_download_url\":\"" + base[0] + "/" + jarName + "\"}]}";
            String same = "{\"tag_name\":\"1.3.8\",\"body\":\"\",\"assets\":[{\"name\":\"" + jarName + "\",\"size\":1,\"browser_download_url\":\"x\"}]}";
            serve(http[0], "/latest", latest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            serve(http[0], "/same", same.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            serve(http[0], "/" + jarName, served[0]);
            serve(http[0], "/bad.jar", bad);
            http[0].start();
            release[0] = UpdateChecker.check(base[0] + "/latest", "1.3.8");
            check("update: finds 9.9.9 and its jar, not the sources", release[0] != null && "9.9.9".equals(release[0].version())
                    && jarName.equals(release[0].jarName()), String.valueOf(release[0]));
            check("update: nothing to do when it's our version", UpdateChecker.check(base[0] + "/same", "1.3.8") == null, "");
            try {
                UpdateChecker.check(base[0] + "/missing", "1.3.8");
                fail("update: a missing release is an error", "no exception");
            } catch (java.io.IOException e) {
                pass("update: a missing release is an error (" + e.getMessage() + ")");
            }
            if (Boolean.getBoolean("taczvr.update.live")) {
                UpdateChecker.Release live = UpdateChecker.check(UpdateChecker.RELEASES_API, "1.0.0");
                check("update: GitHub has this version with its jar", live != null && live.version().equals(UpdateChecker.currentVersion())
                        && live.jarName().equals("taczvr-1.20.1-" + UpdateChecker.currentVersion() + ".jar") && live.jarSize() > 100000,
                        String.valueOf(live));
                check("update: and nothing newer than this version", UpdateChecker.check(UpdateChecker.RELEASES_API, UpdateChecker.currentVersion()) == null, "");
            }
            mc.setScreen(new TitleScreen());
            UpdateChecker.newer = release[0];
        });
        await("update: Update TaCZ VR comes up on the title screen", 40, mc -> mc.screen instanceof UpdateScreen,
                () -> String.valueOf(Minecraft.getInstance().screen));
        sleep(5);
        run(mc -> {
            UpdateScreen screen = (UpdateScreen) mc.screen;
            check("update: its title", "Update TaCZ VR".equals(screen.getTitle().getString()), screen.getTitle().getString());
            check("update: Later and Quit Game", "Later".equals(screen.laterButton().getMessage().getString())
                    && "Quit Game".equals(screen.quitButton().getMessage().getString()),
                    screen.laterButton().getMessage().getString() + " / " + screen.quitButton().getMessage().getString());
            check("update: release notes without markdown", screen.noteLines().size() == 3, "lines=" + screen.noteLines().size());
            screenshot(mc, "taczvr_update_screen.png");
        });
        sleep(3);
        run(mc -> ((UpdateScreen) mc.screen).laterButton().onPress());
        await("update: Later goes back to the title screen", 10, mc -> mc.screen instanceof TitleScreen, () -> String.valueOf(Minecraft.getInstance().screen));
        sleep(10);
        run(mc -> {
            check("update: offered once per game, it doesn't come back", mc.screen instanceof TitleScreen, String.valueOf(mc.screen));
            dir[0] = mc.gameDirectory.toPath().resolve("taczvr-update-test");
            deleteTree(dir[0]);
            java.nio.file.Files.createDirectories(dir[0].resolve("locked"));
            java.nio.file.Path old = dir[0].resolve("taczvr-1.20.1-1.3.8.jar");
            java.nio.file.Files.write(old, fakeJar("modId = \"taczvr\" old"));
            UpdateScreen.testJar = old;
            UpdateScreen.stopGame = () -> stopped[0] = true;
            mc.setScreen(new UpdateScreen(mc.screen, release[0], "1.3.8"));
        });
        sleep(3);
        run(mc -> ((UpdateScreen) mc.screen).quitButton().onPress());
        await("update: Quit Game downloads, swaps the jar and quits", 200, mc -> stopped[0],
                () -> "error=" + (Minecraft.getInstance().screen instanceof UpdateScreen s && s.error() != null ? s.error().getString() : "none"));
        run(mc -> {
            java.nio.file.Path now = dir[0].resolve("taczvr-1.20.1-9.9.9.jar");
            check("update: the old jar is gone", !java.nio.file.Files.exists(dir[0].resolve("taczvr-1.20.1-1.3.8.jar")), "");
            check("update: the new jar is in place, byte for byte", java.nio.file.Files.exists(now)
                    && java.util.Arrays.equals(java.nio.file.Files.readAllBytes(now), served[0]), "");
            check("update: no half download left", !java.nio.file.Files.exists(dir[0].resolve("taczvr-1.20.1-9.9.9.jar.part")), "");

            // a download that isn't TaCZ VR is refused and the old jar stays
            java.nio.file.Path keep = dir[0].resolve("taczvr-1.20.1-1.3.8.jar");
            java.nio.file.Files.write(keep, fakeJar("modId = \"taczvr\" old"));
            java.nio.file.Files.delete(now);
            try {
                UpdateChecker.install(new UpdateChecker.Release("9.9.9", "", "taczvr-1.20.1-9.9.9.jar", base[0] + "/bad.jar", -1), keep, p -> {
                });
                fail("update: a jar that isn't TaCZ VR is refused", "installed it");
            } catch (java.io.IOException e) {
                check("update: a jar that isn't TaCZ VR is refused (" + e.getMessage() + ")", java.nio.file.Files.exists(keep)
                        && !java.nio.file.Files.exists(now) && !java.nio.file.Files.exists(dir[0].resolve("taczvr-1.20.1-9.9.9.jar.part")), "");
            }

            // Windows keeps a running jar locked: then a helper swaps it once the game has closed
            java.nio.file.Path locked = dir[0].resolve("locked").resolve("taczvr-1.20.1-1.3.8.jar");
            java.nio.file.Files.write(locked, fakeJar("modId = \"taczvr\" old"));
            lock[0] = new java.io.RandomAccessFile(locked.toFile(), "r");
            boolean swapped = UpdateChecker.install(release[0], locked, p -> {
            });
            check("update: a locked jar waits for the game to close", !swapped && java.nio.file.Files.exists(locked)
                    && java.nio.file.Files.exists(dir[0].resolve("locked").resolve("taczvr-1.20.1-9.9.9.jar.part")), "swapped=" + swapped);

            // the helper itself, waiting for a short process instead of the game
            java.nio.file.Path helperDir = dir[0].resolve("helper");
            java.nio.file.Files.createDirectories(helperDir);
            java.nio.file.Path helperOld = helperDir.resolve("taczvr-1.20.1-1.3.8.jar");
            java.nio.file.Path helperPart = helperDir.resolve("taczvr-1.20.1-9.9.9.jar.part");
            java.nio.file.Files.write(helperOld, fakeJar("old"));
            java.nio.file.Files.write(helperPart, served[0]);
            sleeper[0] = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? new ProcessBuilder("ping", "-n", "4", "127.0.0.1").redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                    : new ProcessBuilder("sleep", "3").start();
            UpdateChecker.swapAfterExit(sleeper[0].pid(), helperOld, helperPart, helperDir.resolve("taczvr-1.20.1-9.9.9.jar"));
        });
        sleep(20);
        run(mc -> check("update: the helper waits while the game still runs",
                java.nio.file.Files.exists(dir[0].resolve("helper").resolve("taczvr-1.20.1-1.3.8.jar")), ""));
        await("update: once it's closed, the helper swaps the jars", 400, mc -> {
            java.nio.file.Path helperDir = dir[0].resolve("helper");
            return !java.nio.file.Files.exists(helperDir.resolve("taczvr-1.20.1-1.3.8.jar"))
                    && java.nio.file.Files.exists(helperDir.resolve("taczvr-1.20.1-9.9.9.jar"))
                    && !java.nio.file.Files.exists(helperDir.resolve("taczvr-1.20.1-9.9.9.jar.part"));
        }, () -> "sleeper alive=" + sleeper[0].isAlive());
        run(mc -> {
            // the locked one stays locked until this client exits, then its helper swaps it
            http[0].stop(0);
            UpdateScreen.testJar = null;
            UpdateScreen.stopGame = () -> Minecraft.getInstance().stop();
            UpdateChecker.newer = null;
            mc.setScreen(null);
            grabMouse(mc);
            log("update: after exit, %s should hold only the 9.9.9 jar", dir[0].resolve("locked"));
        });
        sleep(5);
    }

    private static byte[] fakeJar(String modsToml) throws java.io.IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes)) {
            zip.putNextEntry(new java.util.zip.ZipEntry("META-INF/mods.toml"));
            zip.write(modsToml.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("padding.bin"));
            zip.write(new byte[50000]);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static void serve(com.sun.net.httpserver.HttpServer http, String path, byte[] body) {
        http.createContext(path, exchange -> {
            if (!exchange.getRequestURI().getPath().equals(path)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    private static void deleteTree(java.nio.file.Path root) throws java.io.IOException {
        if (!java.nio.file.Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                java.nio.file.Files.delete(path);
            }
        }
    }

    /**
     * The op game menu: a round where the first to fall loses, and nobody dies.
     */
    private static void gameRound() {
        run(mc -> mc.setScreen(new PauseScreen(true)));
        sleep(3);
        run(mc -> {
            check("game: menu button for the host", hasButton(mc, "taczvr.game.button"), "");
            mc.setScreen(new GameMenu.GameScreen(mc.screen));
        });
        sleep(3);
        run(mc -> {
            check("game: all five modes in the menu", hasButton(mc, "taczvr.game.start") && hasButton(mc, "taczvr.game.start.last")
                    && hasButton(mc, "taczvr.game.start.teams") && hasButton(mc, "taczvr.game.start.lives")
                    && hasButton(mc, "taczvr.game.start.zombies"), "");
            screenshot(mc, "taczvr_game_menu.png");
        });
        run(mc -> {
            mc.setScreen(null);
            Net.CHANNEL.sendToServer(new GameCommandPacket(GameManager.FIRST_FALL));
        });
        await("game: round started", 20, mc -> server(sp -> GameManager.running()), () -> "");
        run(mc -> {
            float health = server(sp -> {
                sp.removeAllEffects();
                sp.setHealth(20.0F);
                sp.hurt(sp.damageSources().generic(), 6.0F);
                return sp.getHealth();
            });
            check("game: nobody gets hurt during the countdown", health >= 20.0F, "health " + health);
        });
        sleep(75);
        run(mc -> {
            Object[] r = server(sp -> {
                sp.hurt(sp.damageSources().generic(), 1000.0F);
                return new Object[]{sp.isAlive() && !sp.isDeadOrDying(), sp.getHealth(), GameManager.running(),
                        IGun.getIGunOrNull(sp.getMainHandItem()) != null};
            });
            check("game: 0 health doesn't kill", (Boolean) r[0], "dead");
            check("game: the round ends when someone falls", !(Boolean) r[2], "still running");
            check("game: nothing lost", (Boolean) r[3], "gun gone");
            check("game: fallen player healed", (Float) r[1] >= 20.0F, "health " + r[1]);
        });
        // past the short invulnerability after being hit
        sleep(25);
        run(mc -> server(sp -> sp.hurt(sp.damageSources().generic(), 3.0F)));
        run(mc -> check("game: normal damage again after the round", server(sp -> sp.getHealth()) < 20.0F, ""));
        zombieWaves();
        lastStanding();
        teamMatch();
        threeLives();
    }

    // fake players are invulnerable and not in the player list, so they join through GameManager.start and fall
    // through GameManager.fall; the real player falls the normal way, by damage
    private static FakePlayer gamer(ServerPlayer sp, String name) {
        FakePlayer fake = FakePlayerFactory.get(sp.serverLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
        fake.setGameMode(GameType.SURVIVAL);
        fake.setPos(sp.getX() + 3.0, sp.getY(), sp.getZ());
        return fake;
    }

    private static FakePlayer fake(ServerPlayer sp, String name) {
        return FakePlayerFactory.get(sp.serverLevel(), new GameProfile(UUID.nameUUIDFromBytes(name.getBytes()), name));
    }

    private static void startGame(int mode, String... fakes) {
        server(sp -> {
            sp.removeAllEffects();
            sp.setHealth(20.0F);
            List<ServerPlayer> players = new java.util.ArrayList<>();
            players.add(sp);
            for (String name : fakes) {
                players.add(gamer(sp, name));
            }
            GameManager.start(sp, mode, players);
            return null;
        });
    }

    private static void lastStanding() {
        run(mc -> startGame(GameManager.LAST_STANDING, "GamerA", "GamerB"));
        sleep(65);
        run(mc -> {
            Object[] r = server(sp -> {
                ServerPlayer a = gamer(sp, "GamerA");
                GameManager.fall(a);
                boolean onAfterOne = GameManager.running();
                boolean aOut = GameManager.isOut(a) && a.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
                sp.hurt(sp.damageSources().generic(), 1000.0F);
                return new Object[]{onAfterOne, aOut, GameManager.running(), sp.isAlive(), sp.gameMode.getGameModeForPlayer(),
                        a.gameMode.getGameModeForPlayer()};
            });
            check("last standing: goes on after the first one falls", (Boolean) r[0], "ended");
            check("last standing: the fallen one watches as spectator", (Boolean) r[1], "");
            check("last standing: ends when one is left", !(Boolean) r[2], "still running");
            check("last standing: falling doesn't kill", (Boolean) r[3], "dead");
            check("last standing: everyone gets their game mode back", r[4] == GameType.SURVIVAL && r[5] == GameType.SURVIVAL,
                    r[4] + " / " + r[5]);
        });
        // the refusal needs a second player
        run(mc -> {
            startGame(GameManager.TEAMS);
            check("teams: refused alone", !server(sp -> GameManager.running()), "");
        });
    }

    private static void teamMatch() {
        run(mc -> startGame(GameManager.TEAMS, "GamerA", "GamerB", "GamerC"));
        sleep(3);
        run(mc -> {
            Object[] r = server(sp -> {
                String mine = GameManager.teamOf(sp);
                int red = 0;
                int blue = 0;
                ServerPlayer mate = null;
                List<ServerPlayer> enemies = new java.util.ArrayList<>();
                for (String name : new String[]{"GamerA", "GamerB", "GamerC"}) {
                    ServerPlayer fake = gamer(sp, name);
                    String team = GameManager.teamOf(fake);
                    if (team != null && team.equals(mine)) {
                        mate = fake;
                    } else {
                        enemies.add(fake);
                    }
                }
                for (ServerPlayer p : new ServerPlayer[]{sp, gamer(sp, "GamerA"), gamer(sp, "GamerB"), gamer(sp, "GamerC")}) {
                    String team = GameManager.teamOf(p);
                    if ("taczvr_red".equals(team)) {
                        red++;
                    } else if ("taczvr_blue".equals(team)) {
                        blue++;
                    }
                }
                boolean scoreboard = sp.getTeam() != null && sp.getTeam().getName().equals(mine);
                boolean mateSafe = mate != null && !sp.canHarmPlayer(mate);
                boolean enemyHurts = !enemies.isEmpty() && sp.canHarmPlayer(enemies.get(0));
                return new Object[]{red, blue, scoreboard, mateSafe, enemyHurts};
            });
            check("teams: 2 red, 2 blue", (Integer) r[0] == 2 && (Integer) r[1] == 2, r[0] + " red, " + r[1] + " blue");
            check("teams: on the scoreboard team (coloured name)", (Boolean) r[2], "");
            check("teams: no friendly fire", (Boolean) r[3], "");
            check("teams: enemies can be hit", (Boolean) r[4], "");
            mc.options.hideGui = false;
        });
        sleep(30);
        run(mc -> screenshot(mc, "taczvr_game_teams.png"));
        sleep(40);
        run(mc -> {
            Object[] r = server(sp -> {
                String mine = GameManager.teamOf(sp);
                ServerPlayer mate = null;
                List<ServerPlayer> enemies = new java.util.ArrayList<>();
                for (String name : new String[]{"GamerA", "GamerB", "GamerC"}) {
                    ServerPlayer fake = gamer(sp, name);
                    if (mine != null && mine.equals(GameManager.teamOf(fake))) {
                        mate = fake;
                    } else {
                        enemies.add(fake);
                    }
                }
                // a teammate's shot does nothing, an enemy's does
                sp.hurt(sp.damageSources().playerAttack(mate), 4.0F);
                float afterMate = sp.getHealth();
                sp.hurt(sp.damageSources().playerAttack(enemies.get(0)), 4.0F);
                float afterEnemy = sp.getHealth();
                GameManager.fall(mate);
                boolean onWithMateDown = GameManager.running();
                GameManager.fall(enemies.get(0));
                boolean onWithOneEnemyDown = GameManager.running();
                GameManager.fall(enemies.get(1));
                boolean teamsGone = sp.getScoreboard().getPlayerTeam("taczvr_red") == null
                        && sp.getScoreboard().getPlayerTeam("taczvr_blue") == null && sp.getTeam() == null;
                return new Object[]{afterMate, afterEnemy, onWithMateDown, onWithOneEnemyDown, GameManager.running(), teamsGone};
            });
            check("teams: teammate's hit does no damage", (Float) r[0] >= 20.0F, "health " + r[0]);
            check("teams: enemy's hit does", (Float) r[1] < 20.0F, "health " + r[1]);
            check("teams: goes on while both teams have players", (Boolean) r[2] && (Boolean) r[3], r[2] + " " + r[3]);
            check("teams: ends when a team is out", !(Boolean) r[4], "still running");
            check("teams: scoreboard teams cleaned up", (Boolean) r[5], "");
        });
        sleep(20);
        run(mc -> {
            screenshot(mc, "taczvr_game_teams_won.png");
            mc.options.hideGui = true;
        });
    }

    private static void threeLives() {
        run(mc -> startGame(GameManager.LIVES, "GamerA"));
        sleep(65);
        run(mc -> {
            Object[] r = server(sp -> {
                sp.hurt(sp.damageSources().generic(), 1000.0F);
                return new Object[]{GameManager.livesLeft(sp), GameManager.running(), sp.getHealth(), sp.gameMode.getGameModeForPlayer()};
            });
            check("lives: falling costs a life", (Integer) r[0] == 2, "lives " + r[0]);
            check("lives: the round goes on", (Boolean) r[1], "ended");
            check("lives: back on your feet, healed, still playing", (Float) r[2] >= 20.0F && r[3] == GameType.SURVIVAL, r[2] + " " + r[3]);
        });
        // past the hurt cooldown, still inside the protection after losing a life
        sleep(15);
        run(mc -> check("lives: short protection after losing a life",
                server(sp -> {
                    sp.hurt(sp.damageSources().generic(), 5.0F);
                    return sp.getHealth();
                }) >= 20.0F, ""));
        sleep(55);
        run(mc -> check("lives: protection wears off",
                server(sp -> {
                    sp.hurt(sp.damageSources().generic(), 3.0F);
                    return sp.getHealth();
                }) < 20.0F, ""));
        run(mc -> {
            Object[] r = server(sp -> {
                ServerPlayer a = gamer(sp, "GamerA");
                GameManager.fall(a);
                GameManager.fall(a);
                int lives = GameManager.livesLeft(a);
                boolean on = GameManager.running();
                GameManager.fall(a);
                return new Object[]{lives, on, GameManager.running(), a.gameMode.getGameModeForPlayer()};
            });
            check("lives: others count down too", (Integer) r[0] == 1 && (Boolean) r[1], "lives " + r[0]);
            check("lives: out of lives, the last one with lives wins", !(Boolean) r[2], "still running");
            check("lives: game mode back after the round", r[3] == GameType.SURVIVAL, String.valueOf(r[3]));
        });
        run(mc -> server(sp -> {
            sp.setHealth(20.0F);
            sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 12000, 4, false, false));
            return null;
        }));
    }

    private static int countWaveZombies(ServerPlayer sp) {
        return sp.serverLevel().getEntitiesOfClass(Zombie.class, sp.getBoundingBox().inflate(64.0), z -> z.getTags().contains("taczvr_wave")).size();
    }

    /**
     * Zombie waves: refused on Peaceful, waves grow, the round ends when everyone has fallen and cleans up.
     */
    private static void zombieWaves() {
        run(mc -> Net.CHANNEL.sendToServer(new GameCommandPacket(GameManager.ZOMBIES)));
        sleep(5);
        run(mc -> {
            check("zombies: refused on Peaceful", !server(sp -> GameManager.running()), "");
            server(sp -> {
                sp.server.setDifficulty(Difficulty.EASY, true);
                sp.setHealth(20.0F);
                return null;
            });
            Net.CHANNEL.sendToServer(new GameCommandPacket(GameManager.ZOMBIES));
        });
        await("zombies: wave 1 comes after the countdown", 90, mc -> server(sp -> GameManager.wave() == 1 && countWaveZombies(sp) == 4),
                () -> server(sp -> "wave " + GameManager.wave() + ", zombies " + countWaveZombies(sp)));
        run(mc -> screenshot(mc, "taczvr_zombies_wave1.png"));
        // no friendly fire against zombies: another player's hit, your own grenade, nothing hurts
        run(mc -> {
            float[] r = server(sp -> {
                sp.setHealth(20.0F);
                FakePlayer friend = gamer(sp, "ZombieFriend");
                sp.hurt(sp.damageSources().playerAttack(friend), 6.0F);
                float afterFriend = sp.getHealth();
                sp.hurt(sp.damageSources().explosion(null, sp), 6.0F);
                float afterOwnBlast = sp.getHealth();
                Zombie zombie = sp.serverLevel().getEntitiesOfClass(Zombie.class, sp.getBoundingBox().inflate(64.0)).get(0);
                sp.hurt(sp.damageSources().mobAttack(zombie), 3.0F);
                return new float[]{afterFriend, afterOwnBlast, sp.getHealth()};
            });
            check("zombies: a friend's hit does nothing", r[0] >= 20.0F, "health " + r[0]);
            check("zombies: your own grenade does nothing", r[1] >= 20.0F, "health " + r[1]);
            check("zombies: the zombies still hurt", r[2] < 20.0F, "health " + r[2]);
        });
        run(mc -> server(sp -> {
            for (Zombie zombie : sp.serverLevel().getEntitiesOfClass(Zombie.class, sp.getBoundingBox().inflate(64.0))) {
                zombie.kill();
            }
            return null;
        }));
        sleep(3);
        run(mc -> {
            String state = server(sp -> GameManager.describe());
            log("zombies after the kill: %s", state);
        });
        // the break: wave bonus, the shop opens by itself
        await("shop: opens by itself after the wave", 20, mc -> ShopScreen.isOpen() && mc.screen instanceof ShopScreen, () -> "");
        run(mc -> {
            int points = server(sp -> ZombieShop.points(sp));
            check("shop: wave bonus paid", points == ZombieShop.WAVE_POINTS, points + " points");
            mc.options.hideGui = false;
            server(sp -> {
                ZombieShop.add(sp, 1000);
                return null;
            });
        });
        sleep(5);
        run(mc -> screenshot(mc, "taczvr_zombie_shop.png"));
        run(mc -> {
            // the AK, then too little left for the machine gun
            Net.CHANNEL.sendToServer(new ShopActionPacket(indexOf("ak47")));
            Net.CHANNEL.sendToServer(new ShopActionPacket(indexOf("m249")));
        });
        sleep(5);
        run(mc -> {
            Object[] r = server(sp -> {
                int bought = 0;
                ItemStack ak = ItemStack.EMPTY;
                for (ItemStack stack : sp.getInventory().items) {
                    if (ZombieShop.isBought(stack)) {
                        bought++;
                        ak = stack;
                    }
                }
                // your own scope on a bought gun comes back at the end
                IGun iGun = IGun.getIGunOrNull(ak);
                if (iGun != null) {
                    iGun.installAttachment(ak, AttachmentItemBuilder.create().setId(ACOG).build());
                }
                return new Object[]{bought, ZombieShop.points(sp), iGun != null && iGun.getGunId(ak).equals(AK)};
            });
            check("shop: bought the AK, marked as bought", (Integer) r[0] == 1 && (Boolean) r[2], r[0] + " bought");
            check("shop: price paid, the machine gun refused", (Integer) r[1] == ZombieShop.WAVE_POINTS + 1000 - 700, r[1] + " points");
            mc.options.hideGui = true;
            // everyone ready: the next wave comes at once
            Net.CHANNEL.sendToServer(new ShopActionPacket(ShopActionPacket.READY));
        });
        await("zombies: wave 2 is bigger", 20, mc -> server(sp -> GameManager.wave() == 2 && countWaveZombies(sp) == 6),
                () -> server(sp -> "zombies " + countWaveZombies(sp) + ", " + GameManager.describe()));
        await("shop: closes when the wave comes", 10, mc -> !ShopScreen.isOpen() && !(mc.screen instanceof ShopScreen), () -> "");
        run(mc -> {
            Object[] r = server(sp -> {
                sp.hurt(sp.damageSources().generic(), 1000.0F);
                boolean anyBought = false;
                boolean acog = false;
                for (ItemStack stack : sp.getInventory().items) {
                    anyBought |= ZombieShop.isBought(stack);
                    acog |= stack.getItem() instanceof IAttachment attachment && attachment.getAttachmentId(stack).equals(ACOG);
                }
                return new Object[]{GameManager.running(), sp.isAlive(), sp.gameMode.getGameModeForPlayer(), countWaveZombies(sp),
                        IGun.getIGunOrNull(sp.getMainHandItem()) != null, anyBought, acog,
                        sp.getScoreboard().getObjective("taczvr_points") == null};
            });
            check("zombies: the round ends when everyone has fallen", !(Boolean) r[0], "still running");
            check("zombies: falling doesn't kill", (Boolean) r[1], "dead");
            check("zombies: back to survival after the round", r[2] == GameType.SURVIVAL, String.valueOf(r[2]));
            check("zombies: leftover zombies removed", (Integer) r[3] == 0, r[3] + " left");
            check("zombies: nothing of yours lost", (Boolean) r[4], "gun gone");
            check("shop: what was bought is taken back", !(Boolean) r[5], "");
            check("shop: your own scope from the bought gun is given back", (Boolean) r[6], "");
            check("shop: points board gone", (Boolean) r[7], "");
        });
        zombieDowned();
        run(mc -> server(sp -> {
            sp.server.setDifficulty(Difficulty.PEACEFUL, true);
            sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 12000, 4, false, false));
            return null;
        }));
    }

    private static int indexOf(String key) {
        for (int i = 0; i < ZombieShop.OFFERS.size(); i++) {
            if (ZombieShop.OFFERS.get(i).key().equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Zombie waves with a friend: down on the ground instead of out, got back up by crouching next to you or a
     * syringe, out when nobody comes in time. And the boss of every fifth wave.
     */
    private static void zombieDowned() {
        run(mc -> server(sp -> {
            sp.server.setDifficulty(Difficulty.EASY, true);
            // zombie hits can't get through, the tests down you with void damage
            sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 12000, 4, false, false));
            List<ServerPlayer> players = new java.util.ArrayList<>(List.of(sp, gamer(sp, "Buddy")));
            GameManager.start(sp, GameManager.ZOMBIES, players);
            return null;
        }));
        sleep(70);
        run(mc -> {
            Object[] r = server(sp -> {
                sp.hurt(sp.damageSources().fellOutOfWorld(), 1000.0F);
                boolean downed = GameManager.isDowned(sp);
                float health = sp.getHealth();
                sp.hurt(sp.damageSources().fellOutOfWorld(), 3.0F);
                Zombie zombie = sp.serverLevel().getEntitiesOfClass(Zombie.class, sp.getBoundingBox().inflate(64.0)).get(0);
                zombie.setTarget(sp);
                return new Object[]{downed, GameManager.running(), sp.gameMode.getGameModeForPlayer(), health, sp.getHealth(),
                        zombie.getSensing().hasLineOfSight(sp)};
            });
            check("downed: falls to the ground, not out", (Boolean) r[0] && (Boolean) r[1] && r[2] == GameType.SURVIVAL, r[0] + " " + r[2]);
            check("downed: nothing hurts down there", (Float) r[4] >= (Float) r[3], r[3] + " -> " + r[4]);
            check("downed: zombies don't see you", !(Boolean) r[5], "");
        });
        sleep(3);
        run(mc -> {
            log("downed poses: server %s (forced %s), client %s (forced %s)", server(sp -> sp.getPose() + ""),
                    server(sp -> sp.getForcedPose() + ""), mc.player.getPose(), mc.player.getForcedPose());
            boolean crawling = server(sp -> sp.getPose() == net.minecraft.world.entity.Pose.SWIMMING);
            check("downed: crawling (for you and for others)", crawling && mc.player.getPose() == net.minecraft.world.entity.Pose.SWIMMING,
                    mc.player.getPose().toString());
            boolean targetDropped = server(sp -> sp.serverLevel().getEntitiesOfClass(Zombie.class, sp.getBoundingBox().inflate(64.0))
                    .stream().noneMatch(z -> z.getTarget() == sp));
            check("downed: zombies leave you alone", targetDropped, "");
            mc.options.hideGui = false;
            // the friend crouches next to you
            server(sp -> {
                FakePlayer buddy = fake(sp, "Buddy");
                buddy.setPos(sp.getX() + 1.0, sp.getY(), sp.getZ());
                buddy.setShiftKeyDown(true);
                return null;
            });
        });
        sleep(20);
        run(mc -> screenshot(mc, "taczvr_downed.png"));
        await("downed: a friend crouching next to you gets you up", 80, mc -> !server(sp -> GameManager.isDowned(sp)), () -> "");
        run(mc -> {
            Object[] r = server(sp -> new Object[]{sp.getHealth(), ZombieShop.points(fake(sp, "Buddy"))});
            check("downed: back up with some health, the friend earns points", (Float) r[0] >= 8.0F && (Integer) r[1] == ZombieShop.REVIVE_POINTS,
                    r[0] + " / " + r[1]);
            server(sp -> {
                fake(sp, "Buddy").setShiftKeyDown(false);
                return null;
            });
        });
        sleep(5);
        run(mc -> check("downed: standing again", mc.player.getPose() != net.minecraft.world.entity.Pose.SWIMMING, mc.player.getPose().toString()));
        // past the protection after getting up: down again, and a syringe gets you up
        sleep(65);
        run(mc -> {
            boolean[] r = server(sp -> {
                sp.hurt(sp.damageSources().fellOutOfWorld(), 1000.0F);
                boolean downed = GameManager.isDowned(sp);
                MedkitItem.heal(sp, fake(sp, "Buddy"));
                return new boolean[]{downed, GameManager.isDowned(sp)};
            });
            check("downed: a friend's syringe gets you up at once", r[0] && !r[1], r[0] + " " + r[1]);
        });
        sleep(65);
        // nobody comes: out
        run(mc -> server(sp -> {
            sp.hurt(sp.damageSources().fellOutOfWorld(), 1000.0F);
            GameManager.testBleed(sp, 3);
            return null;
        }));
        sleep(6);
        run(mc -> {
            Object[] r = server(sp -> new Object[]{GameManager.isDowned(sp), GameManager.isOut(sp), GameManager.running(),
                    sp.gameMode.getGameModeForPlayer()});
            check("downed: nobody in time, out", !(Boolean) r[0] && (Boolean) r[1] && (Boolean) r[2] && r[3] == GameType.SPECTATOR,
                    r[0] + " " + r[1] + " " + r[2] + " " + r[3]);
            mc.options.hideGui = true;
        });
        // the boss wave: straight to wave 5
        run(mc -> server(sp -> {
            GameManager.testWave(4);
            for (Zombie zombie : sp.serverLevel().getEntitiesOfClass(Zombie.class, sp.getBoundingBox().inflate(96.0))) {
                zombie.kill();
            }
            return null;
        }));
        sleep(3);
        run(mc -> server(sp -> {
            GameManager.shopAction(fake(sp, "Buddy"), ShopActionPacket.READY);
            return null;
        }));
        await("boss: every fifth wave", 20, mc -> server(sp -> GameManager.wave() == 5 && GameManager.bossBar() != null), () -> "");
        run(mc -> {
            Object[] r = server(sp -> {
                Zombie big = sp.serverLevel().getEntitiesOfClass(Zombie.class, sp.getBoundingBox().inflate(96.0),
                        z -> z.getTags().contains("taczvr_boss")).stream().findFirst().orElse(null);
                if (big == null) {
                    return new Object[]{false, 0.0F, 0};
                }
                float health = big.getMaxHealth();
                int before = ZombieShop.points(fake(sp, "Buddy"));
                big.hurt(sp.damageSources().playerAttack(fake(sp, "Buddy")), 10000.0F);
                return new Object[]{big.hasCustomName(), health, ZombieShop.points(fake(sp, "Buddy")) - before};
            });
            check("boss: a big one with a name and a boss bar", (Boolean) r[0] && (Float) r[1] >= 200.0F, r[0] + " " + r[1]);
            check("boss: worth a lot of points", (Integer) r[2] == ZombieShop.BOSS_POINTS, r[2] + " points");
        });
        await("boss: bar gone once it's dead", 10, mc -> server(sp -> GameManager.bossBar() == null), () -> "");
        run(mc -> {
            boolean over = server(sp -> {
                GameManager.fall(fake(sp, "Buddy"));
                return !GameManager.running() && sp.gameMode.getGameModeForPlayer() == GameType.SURVIVAL;
            });
            check("downed: last one standing falls, game over, everyone back", over, "");
        });
        run(mc -> server(sp -> {
            sp.server.setDifficulty(Difficulty.PEACEFUL, true);
            sp.removeAllEffects();
            sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 12000, 4, false, false));
            return null;
        }));
        sleep(5);
    }

    /**
     * Someone else's bullet flying past your head whizzes, your own don't.
     */
    private static void bulletWhiz() {
        run(mc -> {
            whizBefore = BulletWhiz.whizzes;
            server(sp -> {
                ItemStack gun = sp.getMainHandItem();
                IGun iGun = IGun.getIGunOrNull(gun);
                GunData data = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).orElseThrow().getGunData();
                target = testPig(sp, sp.position().add(8.0, 0.0, -1.0), 90.0F);
                EntityKineticBullet bullet = new EntityKineticBullet(sp.serverLevel(), sp, gun, data.getAmmoId(),
                        iGun.getGunId(gun), iGun.getGunId(gun), false, data, data.getBulletData());
                bullet.setOwner(target);
                // along the x axis, a metre in front of your face, a little up against the drop
                bullet.setPos(sp.getEyePosition().add(8.0, 0.0, -1.0));
                bullet.shoot(-1.0, 0.06, 0.0, 4.0F, 0.0F);
                sp.serverLevel().addFreshEntity(bullet);
                return null;
            });
        });
        for (int i = 0; i < 6; i++) {
            run(mc -> {
                List<EntityKineticBullet> bullets = mc.level.getEntitiesOfClass(EntityKineticBullet.class, mc.player.getBoundingBox().inflate(64.0));
                int onServer = server(sp -> sp.serverLevel().getEntitiesOfClass(EntityKineticBullet.class, sp.getBoundingBox().inflate(64.0)).size());
                log("whiz tick: client bullets %d %s, server bullets %d", bullets.size(),
                        bullets.isEmpty() ? "" : v(bullets.get(0).position()) + " owner " + bullets.get(0).getOwner(), onServer);
            });
        }
        await("whiz: a bullet past your head whizzes", 40, mc -> BulletWhiz.whizzes > whizBefore, () -> "");
        run(mc -> server(sp -> {
            target.discard();
            return null;
        }));
    }

    /**
     * Echo indoors, dry outside, and the muzzle flash lighting up the dark.
     */
    private static void echoAndLight() {
        // the grenades and the team match's hits push the player around, back to the middle of the room to be
        run(mc -> {
            log("echo: player at %s", v(mc.player.position()));
            mc.player.connection.sendCommand("tp @s 0 -60 0");
        });
        // the gun was just handed back, TACZ won't fire while it's still being drawn
        sleep(25);
        run(mc -> {
            restPose();
            echoesBefore = GunEcho.echoes;
            flashesBefore = MuzzleLight.flashCount();
            ammoBefore = clientAmmo(mc);
        });
        pullTrigger(1);
        sleep(12);
        run(mc -> {
            check("echo: the shot out in the open was fired", clientAmmo(mc) < ammoBefore, "ammo " + ammoBefore + " -> " + clientAmmo(mc));
            check("echo: none out in the open", GunEcho.echoes == echoesBefore, (GunEcho.echoes - echoesBefore) + " echoes");
            check("muzzle light: none in daylight", MuzzleLight.flashCount() == flashesBefore, "");
            mc.player.connection.sendCommand("fill -4 -61 -4 4 -55 4 minecraft:stone hollow");
            mc.player.connection.sendCommand("time set 18000");
        });
        sleep(10);
        run(mc -> {
            echoesBefore = GunEcho.echoes;
            flashesBefore = MuzzleLight.flashCount();
        });
        pullTrigger(1);
        await("muzzle light: the flash lights up the dark room", 20, mc -> MuzzleLight.flashCount() > flashesBefore
                && server(sp -> lightBlocksAround(sp)) > 0, () -> "flashes " + (MuzzleLight.flashCount() - flashesBefore));
        sleep(10);
        run(mc -> {
            check("echo: the room echoes the shot", GunEcho.echoes > echoesBefore,
                    String.format(Locale.ROOT, "enclosed %.2f at %s", GunEcho.lastEnclosed, v(mc.player.position())));
            check("muzzle light: gone again right after", server(sp -> lightBlocksAround(sp)) == 0, "light left behind");
            mc.player.connection.sendCommand("fill -4 -61 -4 4 -55 4 minecraft:air");
            mc.player.connection.sendCommand("fill -4 -61 -4 4 -61 4 minecraft:grass_block");
            mc.player.connection.sendCommand("time set 6000");
        });
        sleep(10);
    }

    private static int[] offhandState(ServerPlayer sp) {
        ItemStack off = sp.getOffhandItem();
        ItemStack main = sp.getMainHandItem();
        IGun offGun = IGun.getIGunOrNull(off);
        IGun mainGun = IGun.getIGunOrNull(main);
        return new int[]{offGun == null ? -1 : offGun.getCurrentAmmoCount(off), offGun != null && offGun.hasBulletInBarrel(off) ? 1 : 0,
                mainGun == null ? -1 : mainGun.getCurrentAmmoCount(main)};
    }

    /**
     * A pistol in each hand: the left one sits in the left controller and fires with the left trigger.
     */
    private static void dualPistols() {
        run(mc -> server(sp -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(GLOCK).setAmmoCount(17).setAmmoInBarrel(true).build());
            sp.setItemInHand(InteractionHand.OFF_HAND, GunItemBuilder.create().setId(GLOCK).setAmmoCount(5).setAmmoInBarrel(true).build());
            sp.inventoryMenu.broadcastChanges();
            return null;
        }));
        await("dual: a pistol in each hand", 30, mc -> OffhandGun.holds(mc.player), () -> "");
        run(mc -> {
            VrCommon.testForceVr = true;
            drawnBefore = VrGunRenderer.offhandGunsDrawn;
            rig(eye().add(0.2, -0.2, -0.4), NORTH, eye().add(-0.2, -0.2, -0.4));
        });
        sleep(8);
        run(mc -> {
            check("dual: left pistol drawn in the left hand", VrGunRenderer.offhandGunsDrawn > drawnBefore, "");
            check("dual: Vivecraft's left hand skipped, a hand is on the pistol", VrGunRenderer.hidesFirstPersonHand(InteractionHand.OFF_HAND), "");
            check("dual: TACZ's own left hand item draw skipped", VrGunRenderer.rendersHeldGun(mc.player, mc.player.getOffhandItem()), "");
            screenshot(mc, "taczvr_fp_dual.png");
        });
        // left trigger held for a while: a pistol fires once per pull
        run(mc -> {
            GunPoseSolver.Pose pose = OffhandGun.lastPose();
            expectedMuzzle = new Vec3(pose.muzzle.x, pose.muzzle.y, pose.muzzle.z);
            expectedDir = pose.bulletDirection(25.0);
            shotsBefore = OffhandGun.shotsSent;
            bulletPos = null;
            bulletVel = null;
            captureBullet = true;
            mc.setWindowActive(true);
            VRInputAction.setKeyBindState(VivecraftVRMod.INSTANCE.keyTeleport, true);
        });
        sleep(10);
        run(mc -> VivecraftVRMod.INSTANCE.keyTeleport.setDown(false));
        await("dual: left trigger fires the left pistol", 20, mc -> bulletPos != null, () -> "no bullet");
        sleep(3);
        run(mc -> {
            captureBullet = false;
            int[] s = server(SelfTest::offhandState);
            double fromMuzzle = bulletPos.distanceTo(expectedMuzzle);
            double off = Math.toDegrees(Math.acos(Math.min(1.0, bulletVel.normalize().dot(expectedDir))));
            check("dual: bullet starts at the left pistol's muzzle", fromMuzzle < 0.2, String.format(Locale.ROOT, "%.3f", fromMuzzle));
            check("dual: and flies where it points", off < 5.0, String.format(Locale.ROOT, "%.1f deg", off));
            check("dual: one shot per pull (semi-auto)", OffhandGun.shotsSent == shotsBefore + 1, (OffhandGun.shotsSent - shotsBefore) + " shots");
            check("dual: the left pistol's round was used", s[0] == 4 && s[1] == 1, "ammo " + s[0] + " barrel " + s[1]);
            check("dual: the right pistol untouched", s[2] == 17, "main ammo " + s[2]);
        });
        // empty: clicks, no shot
        run(mc -> server(sp -> {
            IGun gun = IGun.getIGunOrNull(sp.getOffhandItem());
            gun.setCurrentAmmoCount(sp.getOffhandItem(), 0);
            gun.setBulletInBarrel(sp.getOffhandItem(), false);
            sp.inventoryMenu.broadcastChanges();
            return null;
        }));
        sleep(5);
        run(mc -> {
            shotsBefore = OffhandGun.shotsSent;
            VRInputAction.setKeyBindState(VivecraftVRMod.INSTANCE.keyTeleport, true);
        });
        sleep(3);
        run(mc -> {
            VivecraftVRMod.INSTANCE.keyTeleport.setDown(false);
            check("dual: an empty pistol doesn't fire", OffhandGun.shotsSent == shotsBefore, "");
        });
        // A reloads both
        run(mc -> server(sp -> {
            sp.getInventory().add(AmmoItemBuilder.create().setId(new ResourceLocation("tacz:9mm")).setCount(40).build());
            return null;
        }));
        run(mc -> VRInputAction.setKeyBindState(mc.options.keyUse, true));
        run(mc -> mc.options.keyUse.setDown(false));
        await("dual: A reloads the left pistol too", 60, mc -> {
            int[] s = server(SelfTest::offhandState);
            return s[0] >= 16 && s[1] == 1;
        }, () -> {
            int[] s = server(SelfTest::offhandState);
            return "ammo " + s[0] + " barrel " + s[1];
        });
        run(mc -> server(sp -> {
            sp.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).setAmmoCount(30).setAmmoInBarrel(true).build());
            sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            sp.inventoryMenu.broadcastChanges();
            return null;
        }));
        run(mc -> restPose());
        sleep(10);
    }

    private static int lightBlocksAround(ServerPlayer sp) {
        int found = 0;
        BlockPos center = sp.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(3, 3, 3))) {
            if (sp.serverLevel().getBlockState(pos).is(Blocks.LIGHT)) {
                found++;
            }
        }
        return found;
    }

    /**
     * A shield held in front stops bullets without "using" it.
     */
    private static void shield() {
        run(mc -> server(sp -> {
            sp.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            sp.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            Pig spawned = EntityType.PIG.create(sp.serverLevel());
            spawned.moveTo(sp.getX(), sp.getY(), sp.getZ() - 5.0, 0.0F, 0.0F);
            spawned.setNoAi(true);
            spawned.setNoGravity(true);
            spawned.setInvulnerable(true);
            sp.serverLevel().addFreshEntity(spawned);
            target = spawned;
            return null;
        }));
        // shield raised in front of the chest, towards the shooter in the north
        shieldShot(new Vec3(0.0, 1.2, -0.45));
        run(mc -> {
            float health = server(sp -> sp.getHealth());
            check("shield: raised shield stops the bullet", ShieldBlock.blockedCount() > shieldBefore && health >= 20.0F,
                    "blocked " + (ShieldBlock.blockedCount() - shieldBefore) + ", health " + health);
        });
        // shield down at the side
        shieldShot(new Vec3(-0.45, 0.8, 0.2));
        run(mc -> {
            float health = server(sp -> sp.getHealth());
            check("shield: lowered shield lets it through", ShieldBlock.blockedCount() == shieldBefore && health < 20.0F,
                    "blocked " + (ShieldBlock.blockedCount() - shieldBefore) + ", health " + health);
        });
        run(mc -> {
            VrCommon.testPose = null;
            server(sp -> {
                sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                sp.setHealth(20.0F);
                target.discard();
                return null;
            });
        });
    }

    private static void shieldShot(Vec3 shieldOffset) {
        run(mc -> {
            Vec3 base = mc.player.position();
            VrCommon.testPose = new FakePose(base.add(0.25, 1.1, -0.3), NORTH, base.add(shieldOffset), base.add(0.0, 1.62, 0.0), NORTH);
            shieldBefore = ShieldBlock.blockedCount();
            server(sp -> {
                sp.setHealth(20.0F);
                ItemStack gun = sp.getMainHandItem();
                IGun iGun = IGun.getIGunOrNull(gun);
                GunData data = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).orElseThrow().getGunData();
                // TACZ needs a shooter holding the gun to build the bullet, then the pig fires it
                EntityKineticBullet bullet = new EntityKineticBullet(sp.serverLevel(), sp, gun, data.getAmmoId(),
                        iGun.getGunId(gun), iGun.getGunId(gun), false, data, data.getBulletData());
                bullet.setOwner(target);
                Vec3 from = target.getEyePosition();
                Vec3 chest = sp.position().add(0.0, 1.25, 0.0);
                Vec3 dir = chest.subtract(from).normalize();
                bullet.setPos(from);
                bullet.shoot(dir.x, dir.y, dir.z, 5.0F, 0.0F);
                sp.serverLevel().addFreshEntity(bullet);
                return null;
            });
        });
        sleep(6);
    }

    /**
     * Hand grenades: thrown with the hand's swing in VR, the look direction otherwise, and held too long they go off.
     */
    private static void grenade() {
        run(mc -> check("grenade: item and recipe exist", server(sp -> sp.server.getRecipeManager()
                .byKey(new ResourceLocation("taczvr:grenade")).isPresent()) && ModContent.GRENADE.isPresent(), ""));
        // VR throw: hold A (pin pulled, fuse burning), swing the hand east and up, let go
        run(mc -> {
            VrCommon.testForceVr = true;
            Vec3 base = mc.player.position();
            VrCommon.testPose = new FakePose(base.add(0.25, 1.3, -0.2), NORTH, base.add(-0.3, 1.0, 0.0), base.add(0.0, 1.62, 0.0), NORTH);
            VrCommon.testHandVelocity = new Vec3(0.6, 0.3, 0.0);
            server(sp -> {
                sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 12000, 4, false, false));
                sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.GRENADE.get(), 4));
                sp.inventoryMenu.broadcastChanges();
                return null;
            });
        });
        await("grenade: in the hand", 20, mc -> mc.player.getMainHandItem().is(ModContent.GRENADE.get()), () -> "");
        run(mc -> VRInputAction.setKeyBindState(mc.options.keyUse, true));
        sleep(10);
        run(mc -> mc.options.keyUse.setDown(false));
        await("grenade: thrown when A is let go", 10, mc -> server(sp -> lastGrenade(sp) != null), () -> "none");
        run(mc -> {
            Object[] r = server(sp -> {
                GrenadeEntity thrown = lastGrenade(sp);
                return new Object[]{thrown.getDeltaMovement(), thrown.getFuse(), sp.getMainHandItem().getCount()};
            });
            Vec3 velocity = (Vec3) r[0];
            // it has flown for a tick or two, gravity bends it a little
            double off = Math.toDegrees(Math.acos(Math.min(1.0, velocity.normalize().dot(new Vec3(0.6, 0.3, 0.0).normalize()))));
            log("grenade throw: velocity=%s fuse=%s", v(velocity), r[1]);
            check("grenade: flies the way the hand swung", off < 5.0 && velocity.length() > 0.9,
                    String.format(Locale.ROOT, "%.1f deg, speed %.2f", off, velocity.length()));
            check("grenade: fuse burning since the pin was pulled", (Integer) r[1] > 50 && (Integer) r[1] < 80, "fuse " + r[1]);
            check("grenade: one used up", (Integer) r[2] == 3, "left " + r[2]);
        });
        sleep(4);
        run(mc -> screenshot(mc, "taczvr_grenade_throw.png"));
        await("grenade: goes off when the fuse runs out", 100, mc -> server(sp -> lastGrenade(sp) == null), () -> "still there");
        // held too long: goes off in the hand, a pig next to you gets hurt
        run(mc -> {
            server(sp -> {
                Pig spawned = EntityType.PIG.create(sp.serverLevel());
                spawned.moveTo(sp.getX() + 1.5, sp.getY(), sp.getZ(), 0.0F, 0.0F);
                spawned.setNoAi(true);
                sp.serverLevel().addFreshEntity(spawned);
                target = spawned;
                pigHealth = spawned.getHealth();
                return null;
            });
            VRInputAction.setKeyBindState(mc.options.keyUse, true);
        });
        await("grenade: cooked too long, it goes off in the hand", 110, mc -> server(sp -> !target.isAlive() || target.getHealth() < pigHealth),
                () -> "pig health " + server(sp -> target.getHealth()));
        run(mc -> {
            mc.options.keyUse.setDown(false);
            check("grenade: the one in the hand was used up", server(sp -> sp.getMainHandItem().getCount()) <= 2, "");
        });
        sleep(3);
        // holding A on after the blast pulled the next pin: throw it away and clean up
        run(mc -> server(sp -> {
            for (GrenadeEntity left : sp.serverLevel().getEntitiesOfClass(GrenadeEntity.class, sp.getBoundingBox().inflate(64.0))) {
                left.discard();
            }
            if (target.isAlive()) {
                target.discard();
            }
            sp.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.GRENADE.get(), 2));
            sp.inventoryMenu.broadcastChanges();
            return null;
        }));
        // flat screen: thrown the way you look
        run(mc -> VrCommon.testForceVr = false);
        sleep(3);
        run(mc -> VRInputAction.setKeyBindState(mc.options.keyUse, true));
        sleep(5);
        run(mc -> mc.options.keyUse.setDown(false));
        await("grenade: flat throw", 10, mc -> server(sp -> lastGrenade(sp) != null), () -> "none");
        run(mc -> {
            Vec3 velocity = server(sp -> {
                GrenadeEntity thrown = lastGrenade(sp);
                Vec3 v = thrown == null ? null : thrown.getDeltaMovement();
                if (thrown != null) {
                    thrown.discard();
                }
                return v;
            });
            double off = velocity == null ? 180.0 : Math.toDegrees(Math.acos(Math.min(1.0,
                    new Vec3(velocity.x, 0.0, velocity.z).normalize().dot(mc.player.getLookAngle().multiply(1.0, 0.0, 1.0).normalize()))));
            check("grenade: without VR it goes where you look", off < 5.0, String.format(Locale.ROOT, "%.1f deg", off));
        });
        run(mc -> {
            VrCommon.testForceVr = true;
            VrCommon.testPose = null;
            VrCommon.testHandVelocity = null;
            server(sp -> {
                sp.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).setAmmoCount(30).setAmmoInBarrel(true).build());
                return null;
            });
        });
        sleep(10);
    }

    @Nullable
    private static GrenadeEntity lastGrenade(ServerPlayer sp) {
        List<GrenadeEntity> grenades = sp.serverLevel().getEntitiesOfClass(GrenadeEntity.class, sp.getBoundingBox().inflate(64.0));
        return grenades.isEmpty() ? null : grenades.get(grenades.size() - 1);
    }

    private static void pullTrigger(int ticks) {
        run(mc -> {
            // Vivecraft reports the window active while in VR, TACZ only shoots in an active window
            mc.setWindowActive(true);
            grabMouse(mc);
            VRInputAction.setKeyBindState(mc.options.keyAttack, true);
        });
        sleep(ticks);
        run(mc -> mc.options.keyAttack.setDown(false));
    }

    /**
     * Fires one shot and records where the server's bullet went.
     */
    private static void captureShot(String name) {
        run(mc -> {
            bulletPos = null;
            bulletVel = null;
            captureBullet = true;
        });
        pullTrigger(2);
        await(name + ": shot fired", 20, mc -> bulletPos != null, () -> "none");
        run(mc -> captureBullet = false);
    }

    /**
     * Angle between the shot and the target's upper chest, where aim assist aims.
     */
    private static double degreesToTarget() {
        Vec3 pos = bulletPos;
        Vec3 vel = bulletVel;
        return pos == null || vel == null ? 180.0 : degreesTo(pos, vel);
    }

    /**
     * Same for the aim the server received from the gun (no random spread in it).
     */
    private static double aimDegreesToTarget() {
        ServerAimStore.Aim aim = server(ServerAimStore::getAim);
        return aim == null ? 180.0 : degreesTo(aim.origin(), aim.direction());
    }

    private static double degreesTo(Vec3 from, Vec3 dir) {
        Vec3 at = server(sp -> {
            AABB box = target.getBoundingBox();
            return new Vec3((box.minX + box.maxX) / 2.0, box.minY + box.getYsize() * 0.72, (box.minZ + box.maxZ) / 2.0);
        });
        Vec3 to = at.subtract(from).normalize();
        return Math.toDegrees(Math.acos(Math.min(1.0, to.dot(dir.normalize()))));
    }

    /**
     * The assist menu: only for listed players, infinite ammo, aim assist and glowing targets.
     */
    private static void assist() {
        run(mc -> {
            check("assist: nobody listed, no menu", !ClientAssist.allowed(), "");
            mc.setScreen(new PauseScreen(true));
        });
        sleep(2);
        run(mc -> {
            check("assist: no button in their pause screen", !hasAssistButton(mc), "");
            mc.setScreen(null);
            TaczVRConfig.COMMON.assistPlayers.set(List.of(mc.player.getGameProfile().getName()));
            server(sp -> {
                ServerAssist.sendAllowed(sp);
                return null;
            });
        });
        await("assist: the listed player gets the menu", 20, mc -> ClientAssist.allowed(), () -> "");
        run(mc -> mc.setScreen(new PauseScreen(true)));
        sleep(3);
        run(mc -> {
            check("assist: button in the pause screen", hasAssistButton(mc), "");
            screenshot(mc, "taczvr_assist_pause.png");
            mc.setScreen(new AssistScreen(mc.screen));
        });
        sleep(3);
        run(mc -> screenshot(mc, "taczvr_assist_menu.png"));
        run(mc -> mc.setScreen(null));
        // infinite ammo
        run(mc -> {
            ClientAssist.setInfiniteAmmo(true);
            server(sp -> setGun(sp, 3, true));
        });
        await("infinite ammo: the server keeps the gun full", 20, mc -> serverGun().ammo == 30, () -> serverGun().toString());
        pullTrigger(8);
        sleep(3);
        run(mc -> {
            check("infinite ammo: still full after firing", serverGun().ammo == 30, serverGun().toString());
            ClientAssist.setInfiniteAmmo(false);
        });
        sleep(3);
        run(mc -> check("infinite ammo: off again", !server(ServerAssist::infiniteAmmo), ""));
        // aim assist: a target 6 blocks ahead, the gun held 5 degrees to the right of it
        run(mc -> server(sp -> {
            Pig spawned = EntityType.PIG.create(sp.serverLevel());
            Vec3 at = sp.position().add(0.0, 0.86, -6.0);
            spawned.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            spawned.setNoAi(true);
            spawned.setNoGravity(true);
            spawned.setInvulnerable(true);
            sp.serverLevel().addFreshEntity(spawned);
            target = spawned;
            return null;
        }));
        run(mc -> {
            Vec3 grip = eye().add(0.13, -0.17, -0.42);
            Vec3 center = server(sp -> target.getBoundingBox().getCenter());
            Vector3f toTarget = new Vector3f((float) (center.x - grip.x), (float) (center.y - grip.y), (float) (center.z - grip.z))
                    .normalize().rotateY((float) Math.toRadians(-5.0));
            rig(grip, new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, -1.0F), toTarget), idleOff());
            ClientAssist.setAimAssist(false);
        });
        sleep(4);
        captureShot("aim assist off");
        run(mc -> {
            double aim = aimDegreesToTarget();
            log("aim assist off: aim %.2f deg, bullet %.2f deg from the target", aim, degreesToTarget());
            check("aim assist off: the gun's aim stays where it points", aim > 3.0, String.format(Locale.ROOT, "%.2f deg", aim));
            ClientAssist.setAimAssist(true);
        });
        sleep(3);
        captureShot("aim assist on");
        run(mc -> {
            double aim = aimDegreesToTarget();
            double bullet = degreesToTarget();
            log("aim assist on: aim %.2f deg, bullet %.2f deg from the target", aim, bullet);
            check("aim assist on: aim bent onto the target", aim < 0.5, String.format(Locale.ROOT, "%.2f deg", aim));
            check("aim assist on: bullet flies straight at it, no spread", bullet < 1.0, String.format(Locale.ROOT, "%.2f deg", bullet));
            ClientAssist.setAimAssist(false);
        });
        // lock-on: a target 60 blocks away that walks off sideways while the bullet flies
        homingShot(false);
        run(mc -> check("lock-on control: without assist the moving target is missed", server(sp -> target.getHealth()) >= 100.0F,
                "health " + server(sp -> target.getHealth())));
        homingShot(true);
        run(mc -> check("lock-on: the bullet follows the moving target and hits", server(sp -> target.getHealth()) < 100.0F,
                "health " + server(sp -> target.getHealth())));
        // players come first: a player off to the side beats a mob right in the line of fire
        run(mc -> {
            String[] picked = server(sp -> {
                Vec3 from = sp.getEyePosition();
                Vec3 north = new Vec3(0.0, 0.0, -1.0);
                FakePlayer other = FakePlayerFactory.get(sp.serverLevel(),
                        new GameProfile(UUID.nameUUIDFromBytes("taczvr_selftest_target".getBytes()), "LockTarget"));
                other.setGameMode(GameType.SURVIVAL);
                // 40 degrees to the right, 12 blocks out
                other.setPos(sp.getX() + 12.0 * Math.sin(Math.toRadians(40)), sp.getY(), sp.getZ() - 12.0 * Math.cos(Math.toRadians(40)));
                target.setPos(sp.getX(), sp.getY() + 0.86, sp.getZ() - 6.0);
                LivingEntity withPlayer = ServerAssist.lockTarget(sp, from, north, List.of(other), List.of(target));
                other.setPos(sp.getX() + 12.0, sp.getY(), sp.getZ() + 3.0);
                LivingEntity behind = ServerAssist.lockTarget(sp, from, north, List.of(other), List.of(target));
                other.setPos(sp.getX() + 12.0 * Math.sin(Math.toRadians(40)), sp.getY(), sp.getZ() - 12.0 * Math.cos(Math.toRadians(40)));
                other.setGameMode(GameType.CREATIVE);
                LivingEntity creative = ServerAssist.lockTarget(sp, from, north, List.of(other), List.of(target));
                return new String[]{name(withPlayer), name(behind), name(creative)};
            });
            check("lock-on: nearest player in front wins over a mob", picked[0].equals("LockTarget"), picked[0]);
            check("lock-on: a player behind you is ignored, the mob is used", picked[1].equals("pig"), picked[1]);
            check("lock-on: creative players are ignored", picked[2].equals("pig"), picked[2]);
        });
        // glowing targets
        run(mc -> {
            Entity seen = mc.level.getEntity(target.getId());
            check("glow: off by default", seen != null && !mc.shouldEntityAppearGlowing(seen), "");
            ClientAssist.setGlow(true);
            check("glow: target outlined", seen != null && mc.shouldEntityAppearGlowing(seen), "");
            check("glow: not yourself", !mc.shouldEntityAppearGlowing(mc.player), "");
        });
        sleep(4);
        run(mc -> screenshot(mc, "taczvr_assist_glow.png"));
        run(mc -> {
            ClientAssist.setGlow(false);
            TaczVRConfig.COMMON.assistPlayers.set(List.of());
            server(sp -> {
                target.discard();
                ServerAssist.sendAllowed(sp);
                return null;
            });
            restPose();
        });
        await("assist: menu gone once taken off the list", 20, mc -> !ClientAssist.allowed(), () -> "");
    }

    private static String name(@Nullable LivingEntity entity) {
        return entity == null ? "none" : entity instanceof Pig ? "pig" : entity.getName().getString();
    }

    /**
     * Puts the target 60 blocks ahead, fires at it and moves it sideways while the bullet is on its way.
     */
    private static void homingShot(boolean assist) {
        run(mc -> {
            server(sp -> {
                target.setInvulnerable(false);
                target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
                target.setHealth(100.0F);
                target.setPos(sp.getX(), sp.getY() + 0.86, sp.getZ() - 60.0);
                return null;
            });
            Vec3 grip = eye().add(0.13, -0.17, -0.42);
            Vec3 center = server(sp -> target.getBoundingBox().getCenter());
            Vector3f toTarget = new Vector3f((float) (center.x - grip.x), (float) (center.y - grip.y), (float) (center.z - grip.z))
                    .normalize();
            rig(grip, new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, -1.0F), toTarget), idleOff());
            ClientAssist.setAimAssist(assist);
        });
        sleep(6);
        pullTrigger(1);
        // a big side step right away (a straight shot at where it stood must miss), then keeps walking
        for (int i = 0; i < 8; i++) {
            double step = i == 0 ? 3.0 : 0.5;
            run(mc -> server(sp -> {
                target.setPos(target.getX() + step, target.getY(), target.getZ());
                return null;
            }));
        }
        sleep(10);
    }

    private static boolean hasAssistButton(Minecraft mc) {
        return hasButton(mc, "taczvr.assist.button");
    }

    private static boolean hasButton(Minecraft mc, String key) {
        String text = Component.translatable(key).getString();
        return mc.screen != null && mc.screen.children().stream()
                .anyMatch(child -> child instanceof Button button && button.getMessage().getString().equals(text));
    }

    /**
     * What other players see: a VR player (fed to Vivecraft like its network data would be) holding a gun. Its model
     * arm must reach from the shoulder to the hand on the grip, without swallowing the gun.
     */
    private static void remotePlayer() {
        run(mc -> {
            VrClient.testPose = null;
            VrCommon.testForceVr = false;
            mc.options.fov().set(70);
            savedModelType = ClientDataHolderVR.getInstance().vrSettings.playerModelType;
            RemotePlayer other = new RemotePlayer(mc.level, new GameProfile(REMOTE_ID, "VRFriend"));
            // 2m in front of the camera, facing east: the camera sees its right side, where the gun is
            Vec3 at = mc.player.position().add(0.0, 0.0, -2.0);
            other.moveTo(at.x, at.y, at.z, -90.0F, 0.0F);
            other.setYHeadRot(-90.0F);
            other.yBodyRot = -90.0F;
            other.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).build());
            other.setId(REMOTE_ENTITY_ID);
            mc.level.addPlayer(REMOTE_ENTITY_ID, other);
            remote = other;
            remoteTwoHands = false;
        });
        sleep(10);
        for (VRSettings.PlayerModelType type : new VRSettings.PlayerModelType[]{
                VRSettings.PlayerModelType.VANILLA, VRSettings.PlayerModelType.SPLIT_ARMS}) {
            String name = type.name().toLowerCase(Locale.ROOT);
            for (boolean twoHands : new boolean[]{false, true}) {
                run(mc -> {
                    ClientDataHolderVR.getInstance().vrSettings.playerModelType = type;
                    remoteTwoHands = twoHands;
                });
                sleep(6);
                run(mc -> fittedBefore = GunArmFitter.fitted);
                sleep(3);
                run(mc -> {
                    String label = "remote VR player, " + name + " model" + (twoHands ? ", two hands" : "");
                    check(label + ": gun drawn in its hand", VrGunRenderer.rendersHeldGun(remote, remote.getMainHandItem()), "");
                    check(label + ": model arm kept and fitted to the grip", GunArmFitter.fitted > fittedBefore,
                            "fitter didn't run");
                    check(label + ": no life-size hand sticking out of its fist", !VrGunRenderer.drawsGunHands(remote), "");
                    screenshot(mc, "taczvr_remote_" + name + (twoHands ? "_twohand" : "") + ".png");
                });
            }
        }
        // high five: slap the remote player's hand
        run(mc -> {
            VrPose theirs = VrCommon.getPose(remote);
            slapAt = theirs == null || theirs.getMainHand() == null ? remote.position().add(0.4, 1.25, 0.2) : theirs.getMainHand().getPos();
            slapsBefore = HighFive.slaps;
            highFivesBefore = HighFivePacket.received;
            rig(slapAt.add(0.0, 0.0, 0.35), NORTH, idleOff());
        });
        sleep(3);
        run(mc -> rig(slapAt, NORTH, idleOff()));
        sleep(4);
        run(mc -> {
            check("high five: the slap is felt", HighFive.slaps > slapsBefore, "");
            check("high five: the server played it for everyone", HighFivePacket.received > highFivesBefore, "");
            // a slow touch isn't a high five
            slapsBefore = HighFive.slaps;
            rig(slapAt.add(0.0, 0.0, 0.4), NORTH, idleOff());
        });
        sleep(20);
        for (int i = 0; i < 8; i++) {
            run(mc -> rig(rigMain.add(0.0, 0.0, -0.05), NORTH, idleOff()));
        }
        sleep(2);
        run(mc -> {
            check("high five: a slow touch isn't one", HighFive.slaps == slapsBefore, "");
            VrClient.testPose = null;
        });
        // dual pistols on another player, and their left pistol's shot
        run(mc -> {
            drawnBefore = VrGunRenderer.offhandGunsDrawn;
            remote.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(GLOCK).build());
            remote.setItemInHand(InteractionHand.OFF_HAND, GunItemBuilder.create().setId(GLOCK).build());
        });
        sleep(4);
        run(mc -> {
            check("dual (others): their left pistol is drawn", VrGunRenderer.offhandGunsDrawn > drawnBefore, "");
            RemoteGunEffects.offhandFired(REMOTE_ENTITY_ID);
            check("dual (others): their left pistol's shot flashes", RemoteGunEffects.offhandFlashStart(remote) >= 0, "");
            screenshot(mc, "taczvr_remote_dual.png");
        });
        run(mc -> {
            remote.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).build());
            remote.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        });
        // their laser attachment's beam
        run(mc -> {
            lasersBefore = VrGunRenderer.lasersDrawn;
            remote.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(new ResourceLocation("tacz:m4a1"))
                    .putAttachment(AttachmentType.LASER, LASER).build());
        });
        sleep(4);
        run(mc -> {
            check("laser: their laser beam is drawn for others", VrGunRenderer.lasersDrawn > lasersBefore, "");
            screenshot(mc, "taczvr_remote_laser.png");
        });
        run(mc -> remote.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).build()));
        // client side lock-on picks this player even with the gun pointing well past it
        run(mc -> {
            ClientAssist.setAllowed(true);
            ClientAssist.setAimAssist(true);
            Vec3 muzzle = eye();
            // pointing 40 degrees left of the remote player (it stands 2 blocks north)
            Vec3 dir = new Vec3(-Math.sin(Math.toRadians(40)), 0.0, -Math.cos(Math.toRadians(40)));
            Vec3 aimed = ClientAssist.assistAim(mc, mc.player, muzzle, dir);
            AABB box = remote.getBoundingBox();
            Vec3 chest = new Vec3((box.minX + box.maxX) / 2.0, box.minY + box.getYsize() * 0.72, (box.minZ + box.maxZ) / 2.0);
            double off = Math.toDegrees(Math.acos(Math.min(1.0, aimed.dot(chest.subtract(muzzle).normalize()))));
            check("lock-on (client): aims at the nearby player", off < 1.0, String.format(Locale.ROOT, "%.2f deg", off));
            ClientAssist.setAimAssist(false);
            ClientAssist.setAllowed(false);
        });
        // their shots, reloads and manual magazine changes
        run(mc -> {
            ClientDataHolderVR.getInstance().vrSettings.playerModelType = VRSettings.PlayerModelType.VANILLA;
            remoteTwoHands = false;
            RemoteGunEffects.testEvent(remote, remote.getMainHandItem(), false);
            check("remote: their shot shows a muzzle flash", RemoteGunEffects.muzzleFlashStart(remote) >= 0, "");
        });
        run(mc -> screenshot(mc, "taczvr_remote_fire.png"));
        sleep(20);
        run(mc -> {
            RemoteGunEffects.testEvent(remote, remote.getMainHandItem(), true);
            check("remote: their reload animation plays", RemoteGunEffects.isReloading(remote), "");
        });
        sleep(14);
        run(mc -> screenshot(mc, "taczvr_remote_reload.png"));
        await("remote: reload animation ends", 80, mc -> !RemoteGunEffects.isReloading(remote), () -> "");
        run(mc -> {
            RemoteGunEffects.setMagazine(REMOTE_ENTITY_ID, true, true);
            check("remote: their magazine is out, a new one in their hand",
                    RemoteGunEffects.magazineOut(remote) && RemoteGunEffects.holdingMagazine(remote), "");
        });
        sleep(4);
        run(mc -> screenshot(mc, "taczvr_remote_magazine.png"));
        run(mc -> {
            RemoteGunEffects.setMagazine(REMOTE_ENTITY_ID, false, false);
            ClientDataHolderVR.getInstance().vrSettings.playerModelType = savedModelType;
            mc.level.removeEntity(REMOTE_ENTITY_ID, Entity.RemovalReason.DISCARDED);
            remote = null;
            mc.options.fov().set(100);
        });
        sleep(5);
    }

    private static Pig testPig(ServerPlayer sp, Vec3 at, float yaw) {
        Pig spawned = EntityType.PIG.create(sp.serverLevel());
        spawned.moveTo(at.x, at.y, at.z, yaw, 0.0F);
        spawned.setYBodyRot(yaw);
        spawned.setYHeadRot(yaw);
        spawned.setNoAi(true);
        spawned.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        spawned.setHealth(100.0F);
        sp.serverLevel().addFreshEntity(spawned);
        return spawned;
    }

    private static void give(InteractionHand hand, ItemStack stack) {
        server(sp -> {
            sp.setItemInHand(hand, stack);
            sp.inventoryMenu.broadcastChanges();
            return null;
        });
    }

    /**
     * Everything of this mod in its own creative tab, with the logo as its icon.
     */
    private static void creativeTab() {
        run(mc -> {
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(new ResourceLocation("taczvr:main"));
            check("tab: registered with the logo icon", tab != null && tab.getIconItem().is(ModContent.LOGO.get()), "");
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), true, mc.level.registryAccess());
            List<Item> items = tab == null ? List.of() : tab.getDisplayItems().stream().map(ItemStack::getItem).toList();
            check("tab: holds all 7 items", items.containsAll(List.of(ModContent.GRENADE.get(), ModContent.FLASHBANG.get(),
                    ModContent.SMOKE_GRENADE.get(), ModContent.COMBAT_KNIFE.get(), ModContent.MEDKIT.get(),
                    ModContent.NIGHT_VISION_GOGGLES.get(), ModContent.GRAPPLING_HOOK.get())) && !items.contains(ModContent.LOGO.get()),
                    items.toString());
            boolean recipes = server(sp -> {
                for (String id : new String[]{"flashbang", "smoke_grenade", "combat_knife", "medkit", "night_vision_goggles", "grappling_hook"}) {
                    if (sp.server.getRecipeManager().byKey(new ResourceLocation("taczvr", id)).isEmpty()) {
                        return false;
                    }
                }
                return true;
            });
            check("tab: every new item has a recipe", recipes, "");
            mc.player.connection.sendCommand("gamemode creative");
        });
        await("tab: creative for the inventory", 20, mc -> mc.gameMode.hasInfiniteItems(), () -> "");
        run(mc -> {
            CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.get(new ResourceLocation("taczvr:main"));
            mc.options.hideGui = false;
            CreativeModeInventoryScreen screen = new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(), true);
            mc.setScreen(screen);
            Method select = CreativeModeInventoryScreen.class.getDeclaredMethod("selectTab", CreativeModeTab.class);
            select.setAccessible(true);
            select.invoke(screen, tab);
        });
        sleep(5);
        run(mc -> screenshot(mc, "taczvr_creative_tab.png"));
        run(mc -> {
            // like Escape does, so the inventory menu is the open one again
            mc.screen.onClose();
            mc.options.hideGui = true;
            mc.player.connection.sendCommand("gamemode survival");
        });
        await("tab: back to survival", 20, mc -> !mc.gameMode.hasInfiniteItems(), () -> "");
    }

    /**
     * A flashbang in front of you whites out the screen (in VR each eye's view), less when you look away, and stuns
     * mobs so they lose sight of you.
     */
    private static void flashbang() {
        run(mc -> {
            mc.player.connection.sendCommand("tp @s 0 -60 0 180 0");
            VrCommon.testForceVr = false;
            VrClient.testPose = null;
            mc.options.hideGui = false;
            flashesBefore = ScreenEffects.flashesSeen;
            framesBefore = ScreenEffects.flashFramesFlat;
        });
        sleep(5);
        run(mc -> server(sp -> {
            target = testPig(sp, sp.position().add(0.0, 0.0, -5.0), 0.0F);
            GrenadeEntity bang = new GrenadeEntity(sp.serverLevel(), sp);
            bang.setItem(new ItemStack(ModContent.FLASHBANG.get()));
            bang.setPos(sp.getEyePosition().add(0.0, -0.3, -3.0));
            bang.setDeltaMovement(Vec3.ZERO);
            bang.setFuse(2);
            sp.serverLevel().addFreshEntity(bang);
            return null;
        }));
        await("flashbang: goes off and blinds you", 20, mc -> ScreenEffects.flashesSeen > flashesBefore, () -> "");
        sleep(2);
        run(mc -> {
            check("flashbang: the screen is white up close", ScreenEffects.flashAlpha(0.0F) > 0.8F, "alpha " + ScreenEffects.flashAlpha(0.0F));
            check("flashbang: drawn over the flat screen", ScreenEffects.flashFramesFlat > framesBefore, "");
            screenshot(mc, "taczvr_flashbang.png");
            Object[] r = server(sp -> new Object[]{Tactical.isStunned(target), target.getSensing().hasLineOfSight(sp),
                    Tactical.flashStrength(sp, sp.getEyePosition().add(0.0, 0.0, -3.0)),
                    Tactical.flashStrength(sp, sp.getEyePosition().add(0.0, 0.0, 3.0)),
                    Tactical.flashStrength(sp, sp.getEyePosition().add(0.0, 0.0, -30.0))});
            check("flashbang: the pig next to it is stunned and sees nothing", (Boolean) r[0] && !(Boolean) r[1], r[0] + " " + r[1]);
            check("flashbang: weaker behind you", (Float) r[3] < (Float) r[2] * 0.5F, r[2] + " vs " + r[3]);
            check("flashbang: nothing far away", (Float) r[4] == 0.0F, String.valueOf(r[4]));
        });
        // in VR over each eye's view
        await("flashbang: fades", 140, mc -> ScreenEffects.flashAlpha(0.0F) == 0.0F, () -> "");
        run(mc -> {
            restPose();
            framesBefore = ScreenEffects.flashFramesVr;
            ScreenEffects.flash(1.0F);
        });
        sleep(3);
        run(mc -> {
            check("flashbang: in VR drawn over the view", ScreenEffects.flashFramesVr > framesBefore, "");
            screenshot(mc, "taczvr_flashbang_vr.png");
            VrClient.testPose = null;
            mc.options.hideGui = true;
        });
        await("flashbang: stun wears off", 100, mc -> server(sp -> !Tactical.isStunned(target)), () -> "");
        run(mc -> server(sp -> {
            target.discard();
            return null;
        }));
        await("flashbang: fades again", 140, mc -> ScreenEffects.flashAlpha(0.0F) == 0.0F, () -> "");
    }

    /**
     * Smoke hides you from mobs behind it and puffs out a cloud for a while.
     */
    private static void smokeGrenade() {
        run(mc -> {
            boolean seenBefore = server(sp -> {
                target = testPig(sp, sp.position().add(0.0, 0.0, -12.0), 0.0F);
                boolean seen = target.getSensing().hasLineOfSight(sp);
                GrenadeEntity smoke = new GrenadeEntity(sp.serverLevel(), sp);
                smoke.setItem(new ItemStack(ModContent.SMOKE_GRENADE.get()));
                smoke.setPos(sp.position().add(0.0, 0.2, -6.0));
                smoke.setDeltaMovement(Vec3.ZERO);
                smoke.setFuse(2);
                sp.serverLevel().addFreshEntity(smoke);
                return seen;
            });
            check("smoke: the pig sees you before", seenBefore, "");
            puffsBefore = Tactical.smokePuffs;
        });
        sleep(70);
        run(mc -> {
            Object[] r = server(sp -> new Object[]{Tactical.clouds(), target.getSensing().hasLineOfSight(sp)});
            check("smoke: the cloud is out", (Integer) r[0] == 1 && Tactical.smokePuffs > puffsBefore, r[0] + " clouds");
            check("smoke: the pig can't see you through it", !(Boolean) r[1], "");
            screenshot(mc, "taczvr_smoke.png");
        });
        run(mc -> server(sp -> {
            for (GrenadeEntity left : sp.serverLevel().getEntitiesOfClass(GrenadeEntity.class, sp.getBoundingBox().inflate(64.0))) {
                left.discard();
            }
            target.discard();
            return null;
        }));
        sleep(3);
        run(mc -> check("smoke: gone with the grenade", Tactical.clouds() == 0, ""));
    }

    /**
     * Stab forward with the knife in VR, from behind it does much more.
     */
    private static void knife() {
        run(mc -> {
            VrCommon.testForceVr = true;
            restPose();
            give(InteractionHand.MAIN_HAND, new ItemStack(ModContent.COMBAT_KNIFE.get()));
        });
        sleep(25);
        for (boolean behind : new boolean[]{false, true}) {
            run(mc -> {
                // in front of the hand, facing you or turned away
                Vec3 hand = eye().add(0.13, -0.95, -0.42);
                server(sp -> {
                    target = testPig(sp, new Vec3(hand.x, sp.getY(), hand.z - 0.8), behind ? 180.0F : 0.0F);
                    return null;
                });
                stabsBefore = HandTools.stabsSent;
                rig(hand.add(0.0, 0.0, 0.3), NORTH, idleOff());
            });
            sleep(3);
            run(mc -> rig(rigMain.add(0.0, 0.0, -0.25), NORTH, idleOff()));
            run(mc -> rig(rigMain.add(0.0, 0.0, -0.25), NORTH, idleOff()));
            sleep(4);
            run(mc -> {
                float lost = 100.0F - server(sp -> target.getHealth());
                check("knife: a stab forward hits" + (behind ? " (from behind)" : ""), HandTools.stabsSent > stabsBefore && lost > 0.0F,
                        "sent " + (HandTools.stabsSent - stabsBefore) + ", lost " + lost);
                if (behind) {
                    check("knife: from behind it does x2.5", lost > knifeFront * 2.0F, knifeFront + " vs " + lost);
                } else {
                    knifeFront = lost;
                }
                server(sp -> {
                    target.discard();
                    return null;
                });
                restPose();
            });
            sleep(25);
        }
        // a slow push isn't a stab
        run(mc -> {
            Vec3 hand = eye().add(0.13, -0.95, -0.42);
            server(sp -> {
                target = testPig(sp, new Vec3(hand.x, sp.getY(), hand.z - 0.8), 0.0F);
                return null;
            });
            stabsBefore = HandTools.stabsSent;
            rig(hand.add(0.0, 0.0, 0.3), NORTH, idleOff());
        });
        sleep(3);
        for (int i = 0; i < 16; i++) {
            run(mc -> rig(rigMain.add(0.0, 0.0, -0.04), NORTH, idleOff()));
        }
        run(mc -> {
            check("knife: a slow push isn't a stab", HandTools.stabsSent == stabsBefore, "");
            server(sp -> {
                target.discard();
                return null;
            });
            restPose();
        });
    }

    /**
     * The syringe: into your own forearm in VR, into a friend, or the normal way.
     */
    private static void medkit() {
        run(mc -> {
            give(InteractionHand.MAIN_HAND, new ItemStack(ModContent.MEDKIT.get(), 4));
            server(sp -> {
                sp.removeAllEffects();
                sp.setHealth(8.0F);
                return null;
            });
            injectionsBefore = HandTools.injectionsSent;
        });
        sleep(5);
        // the needle on the forearm behind the left controller
        run(mc -> {
            Vec3 off = eye().add(-0.2, -0.4, -0.3);
            rig(off.add(0.0, 0.0, 0.24), NORTH, off);
        });
        sleep(5);
        run(mc -> {
            Object[] r = server(sp -> new Object[]{sp.getHealth(), sp.getMainHandItem().getCount(), sp.hasEffect(MobEffects.REGENERATION)});
            check("medkit: pushed into your forearm, it heals you", HandTools.injectionsSent > injectionsBefore && (Float) r[0] >= 15.0F,
                    "health " + r[0]);
            check("medkit: one used, regeneration on", (Integer) r[1] == 3 && (Boolean) r[2], r[1] + " left");
            restPose();
        });
        run(mc -> {
            Object[] r = server(sp -> {
                FakePlayer friend = gamer(sp, "MedicFriend");
                friend.setHealth(5.0F);
                ItemStack syringe = sp.getMainHandItem();
                syringe.interactLivingEntity(sp, friend, InteractionHand.MAIN_HAND);
                float friendHealth = friend.getHealth();
                sp.setHealth(10.0F);
                syringe.finishUsingItem(sp.level(), sp);
                return new Object[]{friendHealth, sp.getHealth(), syringe.getCount()};
            });
            check("medkit: used on a friend it heals them", (Float) r[0] >= 13.0F, "friend " + r[0]);
            check("medkit: held use heals you (flat)", (Float) r[1] >= 18.0F && (Integer) r[2] == 1, r[1] + ", left " + r[2]);
        });
    }

    /**
     * Night vision goggles: night vision and a green picture while on, off with the key or by touching them in VR.
     */
    private static void nightVision() {
        run(mc -> {
            mc.player.connection.sendCommand("fill -5 -61 -5 5 -55 5 minecraft:stone hollow");
            mc.player.connection.sendCommand("time set 18000");
            give(InteractionHand.MAIN_HAND, new ItemStack(ModContent.NIGHT_VISION_GOGGLES.get()));
            framesBefore = ScreenEffects.nightVisionFrames;
        });
        await("night vision: goggles in the hand", 20, mc -> mc.player.getMainHandItem().is(ModContent.NIGHT_VISION_GOGGLES.get()), () -> "");
        // put on the way a player does: use with them in the hand
        run(mc -> VRInputAction.setKeyBindState(mc.options.keyUse, true));
        sleep(2);
        run(mc -> {
            mc.options.keyUse.setDown(false);
            give(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).setAmmoCount(30).build());
        });
        await("night vision: on when put on", 40, mc -> server(sp -> sp.hasEffect(MobEffects.NIGHT_VISION)) && ScreenEffects.nightVisionOn(),
                () -> "client head " + Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.HEAD) + ", server head "
                        + server(sp -> sp.getItemBySlot(EquipmentSlot.HEAD) + " effect " + sp.hasEffect(MobEffects.NIGHT_VISION)));
        sleep(5);
        run(mc -> {
            check("night vision: green picture drawn", ScreenEffects.nightVisionFrames > framesBefore, "");
            screenshot(mc, "taczvr_night_vision.png");
            pressesBefore = ClientKeys.presses;
            KeyMapping.click(ClientKeys.NIGHT_VISION.getKey());
        });
        await("night vision: the key turns it off", 20, mc -> !server(sp -> sp.hasEffect(MobEffects.NIGHT_VISION)) && !ScreenEffects.nightVisionOn(),
                () -> "presses " + (ClientKeys.presses - pressesBefore) + ", key " + ClientKeys.NIGHT_VISION.getKey()
                        + ", client head " + Minecraft.getInstance().player.getItemBySlot(EquipmentSlot.HEAD).getTag()
                        + ", server " + server(sp -> sp.getItemBySlot(EquipmentSlot.HEAD).getTag() + " effect " + sp.hasEffect(MobEffects.NIGHT_VISION)));
        sleep(3);
        run(mc -> {
            screenshot(mc, "taczvr_night_vision_off.png");
            // in VR: a hand on the goggles and grip
            NightVisionModule module = new NightVisionModule();
            Vec3 goggles = eye().add(0.0, 0.05, -0.08);
            boolean away = module.isActive(mc.player, InteractionHand.OFF_HAND, eye().add(-0.35, -0.5, -0.3));
            boolean at = module.isActive(mc.player, InteractionHand.OFF_HAND, goggles.add(0.05, 0.03, 0.0));
            check("night vision: grip only at the goggles", at && !away, at + " " + away);
            nightVisionWasOn = server(sp -> NightVisionItem.isOn(sp.getItemBySlot(EquipmentSlot.HEAD)));
            if (at) {
                module.onPress(mc.player, InteractionHand.OFF_HAND);
            }
        });
        await("night vision: touching them in VR switches them", 20,
                mc -> server(sp -> NightVisionItem.isOn(sp.getItemBySlot(EquipmentSlot.HEAD))) != nightVisionWasOn, () -> "");
        run(mc -> {
            server(sp -> {
                sp.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                return null;
            });
        });
        await("night vision: gone when taken off", 30, mc -> !server(sp -> sp.hasEffect(MobEffects.NIGHT_VISION)), () -> "");
        run(mc -> {
            mc.player.connection.sendCommand("fill -5 -61 -5 5 -55 5 minecraft:air");
            mc.player.connection.sendCommand("fill -5 -61 -5 5 -61 5 minecraft:grass_block");
            mc.player.connection.sendCommand("time set 6000");
        });
        sleep(5);
    }

    /**
     * The grappling hook: fire it at a wall, get pulled up to it and hang, let go with use.
     */
    private static void grapplingHook() {
        run(mc -> {
            VrCommon.testForceVr = false;
            VrClient.testPose = null;
            mc.player.connection.sendCommand("fill -4 -60 -16 4 -44 -16 minecraft:stone");
            mc.player.connection.sendCommand("tp @s 0 -60 0 180 -25");
            give(InteractionHand.MAIN_HAND, new ItemStack(ModContent.GRAPPLING_HOOK.get()));
        });
        sleep(10);
        run(mc -> {
            mc.options.hideGui = false;
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
            VRInputAction.setKeyBindState(mc.options.keyUse, true);
        });
        sleep(2);
        run(mc -> mc.options.keyUse.setDown(false));
        // no waiting for it to fly: it holds right after the click
        await("hook: grabs the wall at once", 3, mc -> GrappleClient.anchor() != null && server(sp -> {
            GrappleEntity hook = GrappleEntity.of(sp);
            return hook != null && hook.isAnchored();
        }), () -> "");
        run(mc -> framesBefore = GrappleClient.ropesDrawn);
        sleep(1);
        run(mc -> {
            check("hook: rope drawn from the hand to the hook", GrappleClient.ropesDrawn > framesBefore, "");
            screenshot(mc, "taczvr_grappling_rope.png");
        });
        await("hook: pulls you up to it", 60, mc -> mc.player.getZ() < -12.0 && mc.player.getY() > -56.0,
                () -> v(Minecraft.getInstance().player.position()));
        sleep(10);
        run(mc -> {
            Vec3 at = mc.player.position();
            check("hook: you hang there, no fall damage building up", mc.player.getY() > -56.0 && mc.player.fallDistance < 0.5F
                    && GrappleClient.pulls > 0, v(at) + " fall " + mc.player.fallDistance);
            mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        });
        sleep(3);
        run(mc -> {
            screenshot(mc, "taczvr_grappling_hook.png");
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            VRInputAction.setKeyBindState(mc.options.keyUse, true);
        });
        sleep(2);
        run(mc -> mc.options.keyUse.setDown(false));
        await("hook: use again lets go", 20, mc -> server(sp -> GrappleEntity.of(sp) == null) && GrappleClient.anchor() == null, () -> "");
        // far: a wall 150 blocks away, beyond what the game sends of entities to you
        run(mc -> {
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            // the near wall would be in the way
            mc.player.connection.sendCommand("fill -4 -60 -16 4 -44 -16 minecraft:air");
            mc.player.connection.sendCommand("fill -3 -60 -150 3 -25 -150 minecraft:stone");
            mc.player.connection.sendCommand("tp @s 0 -60 0 180 -5");
        });
        sleep(20);
        run(mc -> VRInputAction.setKeyBindState(mc.options.keyUse, true));
        sleep(2);
        run(mc -> mc.options.keyUse.setDown(false));
        await("hook: grabs a wall 150 blocks away at once", 3, mc -> GrappleClient.anchor() != null, () -> "");
        await("hook: and pulls you all the way", 300, mc -> mc.player.getZ() < -140.0, () -> v(Minecraft.getInstance().player.position()));
        run(mc -> {
            check("hook: no fall damage building up on the way", mc.player.fallDistance < 0.5F && server(sp -> sp.fallDistance) < 0.5F,
                    mc.player.fallDistance + " / " + server(sp -> sp.fallDistance));
            VRInputAction.setKeyBindState(mc.options.keyUse, true);
        });
        sleep(2);
        run(mc -> {
            mc.options.keyUse.setDown(false);
            mc.player.connection.sendCommand("tp @s 0 -60 0 180 0");
            mc.player.connection.sendCommand("fill -3 -60 -150 3 -25 -150 minecraft:air");
        });
        await("hook: let go far away too", 20, mc -> server(sp -> GrappleEntity.of(sp) == null) && GrappleClient.anchor() == null, () -> "");
        // in VR it flies where the hand points
        run(mc -> {
            VrCommon.testForceVr = true;
            Vec3 base = mc.player.position();
            VrCommon.testPose = new FakePose(base.add(0.25, 1.3, -0.2), EAST, null, base.add(0.0, 1.62, 0.0), NORTH);
            server(sp -> {
                sp.getCooldowns().removeCooldown(ModContent.GRAPPLING_HOOK.get());
                sp.getMainHandItem().use(sp.level(), sp, InteractionHand.MAIN_HAND);
                return null;
            });
            Vec3 velocity = server(sp -> {
                GrappleEntity hook = GrappleEntity.of(sp);
                Vec3 v = hook == null ? Vec3.ZERO : hook.getDeltaMovement();
                if (hook != null) {
                    hook.discard();
                }
                sp.stopUsingItem();
                return v;
            });
            check("hook: in VR it flies where the hand points", velocity.normalize().dot(new Vec3(1.0, 0.0, 0.0)) > 0.95, v(velocity));
            VrCommon.testPose = null;
            mc.options.hideGui = true;
            mc.player.connection.sendCommand("fill -4 -60 -16 4 -44 -16 minecraft:air");
            mc.player.connection.sendCommand("tp @s 0 -60 0 180 0");
        });
        sleep(20);
        run(mc -> give(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).setAmmoCount(30).setAmmoInBarrel(true).build()));
        sleep(5);
    }

    /**
     * The radio: talking into it (held up, or at the mouth in VR) sends your voice, radio-filtered, to everyone far
     * away who carries a radio, through Simple Voice Chat.
     */
    private static void radio() {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("voicechat")) {
            run(mc -> log("radio: Simple Voice Chat not installed, skipped"));
            return;
        }
        run(mc -> {
            check("radio: Simple Voice Chat loaded our plugin", RadioState.voiceChatLoaded && RadioSelfTest.pluginLoaded(), "");
            RadioFilter filter = new RadioFilter();
            filter.apply(RadioSelfTest.tone(100.0));
            double low = RadioFilter.rms(filter.apply(RadioSelfTest.tone(100.0)));
            filter.reset();
            filter.apply(RadioSelfTest.tone(1000.0));
            double mid = RadioFilter.rms(filter.apply(RadioSelfTest.tone(1000.0)));
            check("radio: the filter keeps the voice and cuts the rumble", mid > low * 3.0,
                    String.format(Locale.ROOT, "1 kHz %.0f, 100 Hz %.0f", mid, low));
            server(sp -> {
                sp.getInventory().setItem(8, new ItemStack(ModContent.RADIO.get()));
                sp.inventoryMenu.broadcastChanges();
                FakePlayer friend = fake(sp, "RadioFriend");
                friend.setPos(sp.getX() + 20.0, sp.getY(), sp.getZ());
                friend.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModContent.RADIO.get()));
                friend.startUsingItem(InteractionHand.MAIN_HAND);
                RadioState.TEST_PLAYERS.add(friend);
                return null;
            });
        });
        await("radio: holding it up puts you on air", 20, mc -> server(sp -> RadioState.isTransmitting(fake(sp, "RadioFriend").getUUID())),
                () -> "");
        run(mc -> {
            Object[] r = server(sp -> {
                FakePlayer friend = fake(sp, "RadioFriend");
                friend.setPos(sp.getX() + 20.0, sp.getY(), sp.getZ());
                boolean hearsFar = RadioState.receivers(friend.getUUID()).contains(sp.getUUID());
                boolean onAir = RadioState.isTransmitting(friend.getUUID());
                return new Object[]{hearsFar, onAir};
            });
            check("radio: someone far away with a radio hears you", (Boolean) r[0] && (Boolean) r[1], r[0] + " " + r[1]);
            radioBefore = RadioSelfTest.clientSounds();
            // voice frames, through the real voice chat to this client
            int sent = server(sp -> RadioSelfTest.sendTone(fake(sp, "RadioFriend").getUUID(), 6));
            check("radio: the voice goes out through voice chat", sent == 1, "sent to " + sent);
        });
        await("radio: and arrives here", 60, mc -> RadioSelfTest.clientSounds() > radioBefore, () -> "");
        run(mc -> server(sp -> {
            fake(sp, "RadioFriend").setPos(sp.getX() + 3.0, sp.getY(), sp.getZ());
            return null;
        }));
        sleep(2);
        run(mc -> {
            boolean near = server(sp -> RadioState.receivers(fake(sp, "RadioFriend").getUUID()).contains(sp.getUUID()));
            check("radio: not from someone right next to you (you hear them anyway)", !near, "");
            server(sp -> {
                FakePlayer friend = fake(sp, "RadioFriend");
                friend.stopUsingItem();
                friend.setPos(sp.getX() + 20.0, sp.getY(), sp.getZ());
                return null;
            });
        });
        sleep(2);
        run(mc -> {
            check("radio: put down, off air", !server(sp -> RadioState.isTransmitting(fake(sp, "RadioFriend").getUUID())), "");
            // VR: the hand with the radio at the mouth
            VrCommon.testForceVr = true;
            Vec3 head = mc.player.getEyePosition();
            VrCommon.testPose = new FakePose(head.add(0.0, -0.1, -0.08), NORTH, null, head, NORTH);
        });
        sleep(2);
        run(mc -> {
            boolean atMouth = server(sp -> RadioState.isTransmitting(fake(sp, "RadioFriend").getUUID()));
            Vec3 head = mc.player.getEyePosition();
            VrCommon.testPose = new FakePose(head.add(0.3, -0.5, -0.3), NORTH, null, head, NORTH);
            check("radio: in VR, the radio at your mouth puts you on air", atMouth, "");
        });
        sleep(2);
        run(mc -> {
            check("radio: in VR, away from the mouth, off air", !server(sp -> RadioState.isTransmitting(fake(sp, "RadioFriend").getUUID())), "");
            VrCommon.testPose = null;
            VrCommon.testForceVr = false;
            server(sp -> {
                RadioState.TEST_PLAYERS.clear();
                sp.getInventory().setItem(8, ItemStack.EMPTY);
                sp.inventoryMenu.broadcastChanges();
                return null;
            });
        });
        sleep(3);
    }

    /**
     * How deep a model is front to back, in blocks: a flat item picture is 1/16.
     */
    private static float depth(BakedModel model) {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        RandomSource random = RandomSource.create(0L);
        List<Direction> sides = new java.util.ArrayList<>(List.of(Direction.values()));
        sides.add(null);
        for (Direction side : sides) {
            for (BakedQuad quad : model.getQuads(null, side, random)) {
                int[] data = quad.getVertices();
                int stride = data.length / 4;
                for (int v = 0; v < 4; v++) {
                    float z = Float.intBitsToFloat(data[v * stride + 2]);
                    min = Math.min(min, z);
                    max = Math.max(max, z);
                }
            }
        }
        return max - min;
    }

    /**
     * Every item of this mod except the goggles is a real 3D model in the hands and the world, and keeps its flat
     * picture in the inventory.
     */
    private static void models3d() {
        run(mc -> {
            for (RegistryObject<Item> item : List.of(ModContent.GRENADE, ModContent.FLASHBANG, ModContent.SMOKE_GRENADE,
                    ModContent.COMBAT_KNIFE, ModContent.MEDKIT, ModContent.GRAPPLING_HOOK, ModContent.RADIO)) {
                ItemStack stack = new ItemStack(item.get());
                BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
                String name = item.getId().getPath();
                boolean missing = model == mc.getModelManager().getMissingModel();
                float hand = depth(model.applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, new PoseStack(), false));
                float gui = depth(model.applyTransform(ItemDisplayContext.GUI, new PoseStack(), false));
                check("3D: " + name + " is 3D in the hand", !missing && hand > 0.1F, String.format(Locale.ROOT, "depth %.3f", hand));
                check("3D: " + name + " keeps its flat inventory picture", gui < 0.07F, String.format(Locale.ROOT, "depth %.3f", gui));
            }
            ItemStack syringe = new ItemStack(ModContent.MEDKIT.get());
            check("3D: the syringe isn't a bow in VR", !org.vivecraft.client_vr.gameplay.trackers.BowTracker.isBow(syringe), "");
            // on stands, on the ground, and a grenade lying there
            mc.player.connection.sendCommand("tp @s 0 -60 0 180 10");
            String[] ids = {"grenade", "flashbang", "smoke_grenade", "combat_knife", "medkit", "grappling_hook", "radio"};
            for (int i = 0; i < ids.length; i++) {
                double x = -3.0 + i;
                mc.player.connection.sendCommand(String.format(Locale.ROOT,
                        "summon armor_stand %.1f -60 -4 {ShowArms:1b,NoBasePlate:1b,NoGravity:1b,Rotation:[0f,0f],HandItems:[{id:\"taczvr:%s\",Count:1b},{}]}",
                        x + 0.5, ids[i]));
                mc.player.connection.sendCommand(String.format(Locale.ROOT,
                        "summon item %.1f -60 -2.2 {Item:{id:\"taczvr:%s\",Count:1b},PickupDelay:32767,NoGravity:1b}", x + 0.5, ids[i]));
            }
            server(sp -> {
                GrenadeEntity lying = new GrenadeEntity(sp.serverLevel(), sp);
                lying.setItem(new ItemStack(ModContent.GRENADE.get()));
                lying.setPos(0.5, -59.95, -1.2);
                lying.setDeltaMovement(Vec3.ZERO);
                lying.setFuse(400);
                sp.serverLevel().addFreshEntity(lying);
                return null;
            });
            modelsBefore = GrenadeRenderer.drawn;
        });
        sleep(30);
        run(mc -> {
            check("3D: a grenade on the ground is drawn as its model", GrenadeRenderer.drawn > modelsBefore, "");
            screenshot(mc, "taczvr_3d_items.png");
            // close up from the side: a grenade in the right hand, the radio in the left
            mc.player.connection.sendCommand("summon armor_stand 0.5 -60 -1.6 {ShowArms:1b,NoBasePlate:1b,NoGravity:1b,Rotation:[-90f,0f],"
                    + "HandItems:[{id:\"taczvr:grenade\",Count:1b},{id:\"taczvr:radio\",Count:1b}]}");
            mc.player.connection.sendCommand("tp @s 0 -60 0 180 20");
        });
        sleep(10);
        run(mc -> screenshot(mc, "taczvr_3d_stand_close.png"));
        // in your own hand (not first after a TACZ gun, TACZ hides the hand while putting the gun away)
        for (RegistryObject<Item> item : List.of(ModContent.MEDKIT, ModContent.COMBAT_KNIFE, ModContent.GRENADE, ModContent.RADIO,
                ModContent.GRAPPLING_HOOK)) {
            run(mc -> {
                mc.options.hideGui = false;
                give(InteractionHand.MAIN_HAND, new ItemStack(item.get()));
            });
            sleep(20);
            run(mc -> screenshot(mc, "taczvr_3d_hand_" + item.getId().getPath() + ".png"));
        }
        run(mc -> {
            mc.options.hideGui = true;
            mc.player.connection.sendCommand("kill @e[type=armor_stand]");
            mc.player.connection.sendCommand("kill @e[type=item]");
            server(sp -> {
                for (GrenadeEntity left : sp.serverLevel().getEntitiesOfClass(GrenadeEntity.class, sp.getBoundingBox().inflate(64.0))) {
                    left.discard();
                }
                return null;
            });
            mc.player.connection.sendCommand("tp @s 0 -60 0 180 0");
            give(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).setAmmoCount(30).setAmmoInBarrel(true).build());
        });
        sleep(5);
    }

    /**
     * Sends Vivecraft the remote player's tracked pose, relative to its position like the network data.
     */
    private static void pushRemoteState() {
        RemotePlayer other = remote;
        if (other == null) {
            return;
        }
        Vec3 base = other.position();
        // right hand in front of the chest, off-hand hanging or on the handguard
        Vec3 main = base.add(0.4, 1.25, 0.2);
        Vec3 off = base.add(0.15, 0.95, -0.3);
        if (remoteTwoHands) {
            GunPoseSolver.Pose pose = GunPoseSolver.solve(FAKE_PLAYER, other.getMainHandItem(),
                    new FakePose(main, EAST, null), Vec3.ZERO, 1.0F, 0.0F, 0.0F);
            if (pose != null) {
                Vector3d on = new Vector3d(pose.muzzle).sub(pose.grip).mul(0.55).add(pose.grip).add(0.0, 0.01, 0.0);
                off = new Vec3(on.x, on.y, on.z);
            }
        }
        VrPlayerState state = new VrPlayerState(false, netPose(new Vec3(0.0, 1.62, 0.0), EAST), false,
                netPose(main.subtract(base), EAST), false, netPose(off.subtract(base), EAST),
                FBTMode.ARMS_ONLY, null, null, null, null, null, null, null);
        ClientVRPlayers.getInstance().update(other.getUUID(), state, 1.0F, 1.0F);
    }

    private static org.vivecraft.common.network.Pose netPose(Vec3 pos, Quaternionfc rot) {
        return new org.vivecraft.common.network.Pose(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z),
                new Quaternionf(rot));
    }

    /**
     * Your own view: Vivecraft's hand at the controller is skipped, TACZ's hand is drawn on the grip instead.
     */
    private static void firstPersonHands() {
        run(mc -> {
            check("fake VR: tick pose solved", VrGunController.lastPose() != null, "no pose");
            check("vanilla held item draw skipped for the gun", VrGunRenderer.rendersHeldGun(mc.player, mc.player.getMainHandItem()), "");
            check("own view: Vivecraft main hand skipped", VrGunRenderer.hidesFirstPersonHand(InteractionHand.MAIN_HAND), "");
            check("own view: free off-hand still drawn", !VrGunRenderer.hidesFirstPersonHand(InteractionHand.OFF_HAND), "");
            check("own view: life-size hand drawn on your gun", VrGunRenderer.drawsGunHands(mc.player), "");
            skippedBefore = VrGunRenderer.handsSkipped;
            try {
                // outside VR this would crash on the missing VR data if our HEAD cancel didn't stop it
                VRArmHelper.renderVRHand_Main(new PoseStack(), 0.0F);
                check("Vivecraft renderVRHand_Main cancelled by the mixin", VrGunRenderer.handsSkipped == skippedBefore + 1,
                        "skip counter " + skippedBefore + " -> " + VrGunRenderer.handsSkipped);
            } catch (Throwable t) {
                fail("Vivecraft renderVRHand_Main cancelled by the mixin", "it ran: " + t);
            }
        });
        sleep(5);
        run(mc -> screenshot(mc, "taczvr_fp_onehand.png"));
        run(mc -> {
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            // off-hand halfway down the barrel line
            Vector3d off = new Vector3d(pose.muzzle).sub(pose.grip).mul(0.55).add(pose.grip).add(0.0, 0.01, 0.0);
            setOff(off);
        });
        sleep(3);
        run(mc -> {
            check("two hands: handguard grabbed", VrGunController.lastPose().twoHanded, "");
            check("two hands: Vivecraft off-hand skipped", VrGunRenderer.hidesFirstPersonHand(InteractionHand.OFF_HAND), "");
        });
        sleep(5);
        run(mc -> screenshot(mc, "taczvr_fp_twohand.png"));
        run(mc -> restPose());
        sleep(3);
        run(mc -> check("two hands: let go", !VrGunController.lastPose().twoHanded, ""));
        // left-handed in VR: the left arm holds the grip
        run(mc -> {
            leftBefore = GunHandRenderer.leftHandedDraws;
            testLeftHanded = true;
        });
        sleep(4);
        run(mc -> {
            check("left-handed VR: the left arm holds the grip", GunHandRenderer.leftHandedDraws > leftBefore, "");
            screenshot(mc, "taczvr_fp_lefthanded_vr.png");
        });
        run(mc -> testLeftHanded = false);
        sleep(2);
    }

    /**
     * Left-handed on a flat screen: TACZ always draws the first person gun on the right and hides the gun of
     * left-handed players in third person. Ours mirrors the first one and draws the second one.
     */
    private static void leftHanded() {
        run(mc -> {
            server(sp -> {
                sp.getInventory().setItem(sp.getInventory().selected, GunItemBuilder.create().setId(AK).setAmmoCount(30).build());
                sp.inventoryMenu.broadcastChanges();
                return null;
            });
            mc.options.hideGui = false;
            mc.options.mainHand().set(HumanoidArm.LEFT);
            mc.options.broadcastOptions();
        });
        await("left-handed: the server knows", 40, mc -> mc.player.getMainArm() == HumanoidArm.LEFT
                && IGun.getIGunOrNull(mc.player.getMainHandItem()) != null, () -> String.valueOf(Minecraft.getInstance().player.getMainArm()));
        sleep(10);
        run(mc -> leftBefore = LeftHanded.mirroredFrames);
        sleep(5);
        run(mc -> {
            check("left-handed: first person gun mirrored to the left", LeftHanded.mirroredFrames > leftBefore,
                    (LeftHanded.mirroredFrames - leftBefore) + " frames");
            screenshot(mc, "taczvr_lefthanded_fp.png");
            mc.options.mainHand().set(HumanoidArm.RIGHT);
            mc.options.broadcastOptions();
        });
        await("left-handed: back to right", 40, mc -> mc.player.getMainArm() == HumanoidArm.RIGHT, () -> "");
        sleep(5);
        run(mc -> leftBefore = LeftHanded.mirroredFrames);
        sleep(5);
        run(mc -> {
            check("right-handed: not mirrored", LeftHanded.mirroredFrames == leftBefore, "");
            screenshot(mc, "taczvr_righthanded_fp.png");
        });
        // someone else, left-handed and not in VR
        run(mc -> {
            RemotePlayer other = new RemotePlayer(mc.level, new GameProfile(UUID.nameUUIDFromBytes("taczvr_selftest_lefty".getBytes()), "Lefty"));
            Vec3 at = mc.player.position().add(0.0, 0.0, -2.5);
            other.moveTo(at.x, at.y, at.z, 30.0F, 0.0F);
            other.setYHeadRot(30.0F);
            other.yBodyRot = 30.0F;
            other.setMainArm(HumanoidArm.LEFT);
            other.setItemInHand(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).build());
            other.setId(REMOTE_ENTITY_ID - 1);
            mc.level.addPlayer(REMOTE_ENTITY_ID - 1, other);
            lefty = other;
            mc.options.hideGui = true;
        });
        sleep(5);
        run(mc -> leftBefore = LeftHanded.thirdPersonDrawn);
        sleep(4);
        run(mc -> {
            check("left-handed (others): their gun is drawn in the left hand", LeftHanded.thirdPersonDrawn > leftBefore, "");
            screenshot(mc, "taczvr_lefthanded_other.png");
            lefty.setMainArm(HumanoidArm.RIGHT);
        });
        sleep(3);
        run(mc -> leftBefore = LeftHanded.thirdPersonDrawn);
        sleep(4);
        run(mc -> {
            check("right-handed (others): left to TACZ", LeftHanded.thirdPersonDrawn == leftBefore, "");
            screenshot(mc, "taczvr_righthanded_other.png");
            mc.level.removeEntity(REMOTE_ENTITY_ID - 1, Entity.RemovalReason.DISCARDED);
            lefty = null;
        });
        sleep(3);
    }

    /**
     * A (Vivecraft's use key) reloads with TACZ's normal animated reload.
     */
    private static void buttonReload() {
        run(mc -> server(sp -> setGun(sp, 5, true)));
        await("reload: client sees 5 rounds", 40, mc -> clientAmmo(mc) == 5, () -> "");
        run(mc -> {
            inventoryBefore = serverGun().inventoryAmmo;
            // exactly what Vivecraft does when A is pressed
            VRInputAction.setKeyBindState(mc.options.keyUse, true);
        });
        run(mc -> mc.options.keyUse.setDown(false));
        run(mc -> check("A: click used up by us, not by vanilla", !mc.options.keyUse.consumeClick(), ""));
        await("A: TACZ reload started", 10, mc -> clientReloading(mc), () -> "");
        sleep(10);
        run(mc -> {
            check("reload: left hand drawn on the gun", VrGunRenderer.hidesFirstPersonHand(InteractionHand.OFF_HAND), "");
            screenshot(mc, "taczvr_fp_reload.png");
        });
        await("A: server refilled the magazine", 120, mc -> serverGun().ammo == 30, () -> serverGun().toString());
        run(mc -> {
            GunState s = serverGun();
            check("A: 25 rounds taken from the inventory", s.inventoryAmmo == inventoryBefore - 25,
                    inventoryBefore + " -> " + s.inventoryAmmo);
        });
        await("reload: animation finished", 80, mc -> !clientReloading(mc), () -> "");
        sleep(5);
    }

    /**
     * The trigger fires where the gun points, from its muzzle, not where the head looks.
     */
    private static void trigger() {
        run(mc -> rig(eye().add(0.1, -0.2, -0.3), new Quaternionf(EAST).rotateX((float) Math.toRadians(15.0)), idleOff()));
        sleep(4);
        run(mc -> {
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            expectedMuzzle = new Vec3(pose.muzzle.x, pose.muzzle.y, pose.muzzle.z);
            expectedDir = pose.bulletDirection(25.0);
            bulletPos = null;
            bulletVel = null;
            captureBullet = true;
            // Vivecraft reports the window active while in VR, TACZ only shoots in an active window
            mc.setWindowActive(true);
            grabMouse(mc);
            VRInputAction.setKeyBindState(mc.options.keyAttack, true);
        });
        sleep(2);
        run(mc -> mc.options.keyAttack.setDown(false));
        await("trigger: a bullet was fired", 20, mc -> bulletPos != null, () -> "none");
        run(mc -> {
            captureBullet = false;
            Vec3 pos = bulletPos;
            if (pos == null || bulletVel == null) {
                return;
            }
            Vec3 dir = bulletVel.normalize();
            double fromMuzzle = pos.distanceTo(expectedMuzzle);
            double offAim = Math.toDegrees(Math.acos(Math.min(1.0, dir.dot(expectedDir))));
            double offLook = Math.toDegrees(Math.acos(Math.min(1.0, dir.dot(mc.player.getLookAngle()))));
            log("bullet start=%s muzzle=%s dir=%s aim=%s", v(pos), v(expectedMuzzle), v(dir), v(expectedDir));
            check("trigger: bullet starts at the muzzle", fromMuzzle < 0.15, String.format(Locale.ROOT, "%.3f blocks off", fromMuzzle));
            check("trigger: bullet flies where the gun points", offAim < 12.0, String.format(Locale.ROOT, "%.1f deg off", offAim));
            check("trigger: not where the head looks", offLook > 60.0, String.format(Locale.ROOT, "%.1f deg from look", offLook));
        });
        sleep(20);
    }

    /**
     * Held with both hands (a CurseForge comment said the bullets then come from the crosshair): the shot still
     * leaves the muzzle, along the barrel that now points from the grip hand to the handguard hand.
     */
    private static void twoHandedTrigger() {
        run(mc -> rig(eye().add(0.1, -0.2, -0.3), new Quaternionf(EAST).rotateX((float) Math.toRadians(5.0)), idleOff()));
        sleep(4);
        run(mc -> {
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            // off-hand on the handguard, a bit above the barrel line: the barrel tilts up towards it
            setOff(new Vector3d(pose.muzzle).sub(pose.grip).mul(0.55).add(pose.grip).add(0.0, 0.06, 0.0));
        });
        sleep(4);
        run(mc -> {
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            check("two-handed shot: holding the handguard", pose.twoHanded, "");
            expectedMuzzle = new Vec3(pose.muzzle.x, pose.muzzle.y, pose.muzzle.z);
            expectedDir = pose.bulletDirection(25.0);
            Vec3 oneHanded = new Vec3(rigRot.transform(new Vector3f(0.0F, 0.0F, -1.0F)));
            check("two-handed shot: the barrel follows the off-hand, not the grip controller",
                    Math.toDegrees(Math.acos(Math.min(1.0, expectedDir.dot(oneHanded.normalize())))) > 2.0, "");
            bulletPos = null;
            bulletVel = null;
            captureBullet = true;
            mc.setWindowActive(true);
            grabMouse(mc);
            VRInputAction.setKeyBindState(mc.options.keyAttack, true);
        });
        sleep(2);
        run(mc -> mc.options.keyAttack.setDown(false));
        await("two-handed shot: a bullet was fired", 20, mc -> bulletPos != null, () -> "none");
        run(mc -> {
            captureBullet = false;
            if (bulletPos == null || bulletVel == null) {
                return;
            }
            Vec3 dir = bulletVel.normalize();
            double fromMuzzle = bulletPos.distanceTo(expectedMuzzle);
            double offAim = Math.toDegrees(Math.acos(Math.min(1.0, dir.dot(expectedDir))));
            double offLook = Math.toDegrees(Math.acos(Math.min(1.0, dir.dot(mc.player.getLookAngle()))));
            log("two-handed bullet start=%s muzzle=%s dir=%s aim=%s", v(bulletPos), v(expectedMuzzle), v(dir), v(expectedDir));
            check("two-handed shot: bullet starts at the muzzle", fromMuzzle < 0.15, String.format(Locale.ROOT, "%.3f blocks off", fromMuzzle));
            check("two-handed shot: bullet flies along the two-handed barrel", offAim < 3.0, String.format(Locale.ROOT, "%.1f deg off", offAim));
            check("two-handed shot: not from the crosshair", offLook > 60.0, String.format(Locale.ROOT, "%.1f deg from look", offLook));
        });
        run(mc -> restPose());
        sleep(20);
    }

    /**
     * A fast jab along the barrel is a melee hit, aimed along the gun.
     */
    private static void meleeThrust() {
        run(mc -> server(sp -> {
            Pig spawned = EntityType.PIG.create(sp.serverLevel());
            Vec3 at = sp.position().add(1.6, 0.86, 0.0);
            spawned.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            spawned.setNoAi(true);
            spawned.setNoGravity(true);
            sp.serverLevel().addFreshEntity(spawned);
            pig = spawned;
            pigHealth = spawned.getHealth();
            return null;
        }));
        run(mc -> {
            rig(eye().add(0.1, -0.2, -0.3), EAST, idleOff());
            room(new Vec3(0.0, 1.0, 0.0));
        });
        sleep(20);
        // slow moves are not a hit
        run(mc -> {
            hapticsBefore = VrClient.testHaptics;
            room(roomHand.add(0.05, 0.0, 0.0));
        });
        run(mc -> room(roomHand.add(0.05, 0.0, 0.0)));
        run(mc -> room(roomHand.add(0.05, 0.0, 0.0)));
        run(mc -> check("melee: slow hand move is not a jab", VrClient.testHaptics == hapticsBefore, ""));
        // control: without the VR aim the jab goes where the head looks (north) and misses the pig (east)
        run(mc -> TaczVRConfig.COMMON.serverVrAim.set(false));
        sleep(3);
        run(mc -> {
            hapticsBefore = VrClient.testHaptics;
            room(roomHand.add(0.2, 0.0, 0.0));
        });
        sleep(2);
        run(mc -> check("melee: fast jab detected", VrClient.testHaptics > hapticsBefore, ""));
        sleep(20);
        run(mc -> check("melee control: without VR aim the pig beside you is missed", pigHealthNow() >= pigHealth,
                "health " + pigHealthNow()));
        run(mc -> {
            TaczVRConfig.COMMON.serverVrAim.set(true);
            room(new Vec3(0.0, 1.0, 0.0));
        });
        sleep(30);
        run(mc -> room(roomHand.add(0.2, 0.0, 0.0)));
        await("melee: jab along the gun hits the pig", 30, mc -> pigHealthNow() < pigHealth, () -> "health " + pigHealthNow());
        // shoot it: the hit and the kill are felt
        run(mc -> {
            Vec3 target = server(sp -> pig.getBoundingBox().getCenter());
            Vec3 grip = eye().add(0.1, -0.2, -0.3);
            Vector3f toPig = new Vector3f((float) (target.x - grip.x), (float) (target.y - grip.y), (float) (target.z - grip.z)).normalize();
            rig(grip, new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, -1.0F), toPig), idleOff());
            hitsBefore = HitFeedback.hits;
            killsBefore = HitFeedback.kills;
            hapticsBefore = VrClient.testHaptics;
        });
        // tough enough to survive a hit, TACZ only reports the kill for a one shot kill
        run(mc -> server(sp -> {
            pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
            pig.setHealth(100.0F);
            return null;
        }));
        // TACZ blocks shooting for 0.7s after a melee hit
        sleep(20);
        pullTrigger(1);
        await("hit feedback: buzz and sound when a bullet hits", 20, mc -> HitFeedback.hits > hitsBefore,
                () -> "pig health " + pigHealthNow());
        run(mc -> server(sp -> {
            pig.setHealth(1.0F);
            return null;
        }));
        sleep(10);
        pullTrigger(1);
        await("hit feedback: kill feedback", 20, mc -> HitFeedback.kills > killsBefore, () -> "pig health " + pigHealthNow());
        run(mc -> check("hit feedback: controller buzzed", VrClient.testHaptics > hapticsBefore, ""));
        run(mc -> server(sp -> {
            if (pig != null) {
                pig.discard();
            }
            return null;
        }));
        run(mc -> restPose());
        sleep(10);
    }

    /**
     * Pull the magazine out with the off-hand, take one from the belt, push it in, rack the charging handle.
     */
    private static void manualMagazine() {
        run(mc -> server(sp -> setGun(sp, 30, true)));
        await("magazine: gun full", 40, mc -> clientAmmo(mc) == 30, () -> "");
        pullGrabInsert("");
        run(mc -> check("magazine: chambered round kept, no rack needed", !MagazineHandler.needsRack(), ""));
        // empty chamber: needs the charging handle after the new magazine
        run(mc -> server(sp -> setGun(sp, 30, false)));
        await("magazine: chamber emptied", 40, mc -> !clientBarrel(mc), () -> "");
        pullGrabInsert(" (empty chamber)");
        run(mc -> {
            check("magazine: rack needed", MagazineHandler.needsRack(), "");
            setOff(MagazineHandler.chargingHandle(VrGunController.lastPose()));
        });
        sleep(2);
        run(mc -> {
            check("rack: off-hand at the handle doesn't grab the handguard", !VrGunController.lastPose().twoHanded, "");
            check("rack: module takes grip at the charging handle", MAGAZINE.isActive(mc.player, InteractionHand.OFF_HAND, rigOff), "");
            check("rack: grip accepted", MAGAZINE.onPress(mc.player, InteractionHand.OFF_HAND), "");
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            setOff(new Vector3d(rigOff.x, rigOff.y, rigOff.z).sub(new Vector3d(pose.forward).mul(0.1)));
        });
        sleep(2);
        run(mc -> {
            check("rack: pulling back", MAGAZINE.onHoldTick(mc.player, InteractionHand.OFF_HAND), "");
            MAGAZINE.onRelease(mc.player, InteractionHand.OFF_HAND);
        });
        await("rack: round chambered", 20, mc -> {
            GunState s = serverGun();
            return s.barrel && s.ammo == 29;
        }, () -> serverGun().toString());
        run(mc -> restPose());
        sleep(5);
    }

    private static void pullGrabInsert(String label) {
        run(mc -> setOff(VrGunController.lastPose().magazine));
        sleep(2);
        run(mc -> {
            inventoryBefore = serverGun().inventoryAmmo;
            check("pull" + label + ": off-hand at the magazine doesn't grab the handguard", !VrGunController.lastPose().twoHanded, "");
            check("pull" + label + ": module takes grip at the magazine", MAGAZINE.isActive(mc.player, InteractionHand.OFF_HAND, rigOff), "");
            check("pull" + label + ": grip accepted", MAGAZINE.onPress(mc.player, InteractionHand.OFF_HAND), "");
            MAGAZINE.onRelease(mc.player, InteractionHand.OFF_HAND);
            check("pull" + label + ": magazine out", MagazineHandler.isMagazineOut(), "");
        });
        await("pull" + label + ": server emptied the gun, rounds back in the inventory", 20, mc -> {
            GunState s = serverGun();
            return s.ammo == 0 && s.inventoryAmmo == inventoryBefore + 30;
        }, () -> serverGun().toString() + " before=" + inventoryBefore);
        run(mc -> setOff(beltPos()));
        sleep(2);
        run(mc -> {
            check("grab" + label + ": module takes grip at the belt", MAGAZINE.isActive(mc.player, InteractionHand.OFF_HAND, rigOff), "");
            check("grab" + label + ": grip accepted", MAGAZINE.onPress(mc.player, InteractionHand.OFF_HAND), "");
            check("grab" + label + ": holding a magazine", MagazineHandler.isHoldingMagazine(), "");
            // just below the magazine well, not in yet
            Vector3d well = GunPoseSolver.boneWorld(VrGunController.lastPose(), GunModelConstant.MAG_NORMAL_NODE);
            setOff(new Vector3d(well).add(0.0, -0.12, 0.0));
        });
        sleep(3);
        run(mc -> {
            check("grab" + label + ": held magazine doesn't grab the handguard", !VrGunController.lastPose().twoHanded, "");
            check("grab" + label + ": still holding", MAGAZINE.onHoldTick(mc.player, InteractionHand.OFF_HAND), "");
            if (label.isEmpty()) {
                screenshot(mc, "taczvr_fp_magazine.png");
            }
        });
        run(mc -> setOff(GunPoseSolver.boneWorld(VrGunController.lastPose(), GunModelConstant.MAG_NORMAL_NODE)));
        await("insert" + label + ": magazine seated", 10, mc -> !MagazineHandler.isMagazineOut(), () -> "");
        run(mc -> {
            check("insert" + label + ": module lets go once seated", !MAGAZINE.onHoldTick(mc.player, InteractionHand.OFF_HAND), "");
            MAGAZINE.onRelease(mc.player, InteractionHand.OFF_HAND);
        });
        await("insert" + label + ": server loaded 30 rounds from the inventory", 20, mc -> {
            GunState s = serverGun();
            return s.ammo == 30 && s.inventoryAmmo == inventoryBefore;
        }, () -> serverGun().toString() + " expected inventory=" + inventoryBefore);
        run(mc -> setOff(idleOff()));
        sleep(3);
    }

    /**
     * Mount a scope from the off-hand, look through it, take it off again.
     */
    private static void attachmentsAndScope() {
        run(mc -> server(sp -> {
            sp.setItemInHand(InteractionHand.OFF_HAND, AttachmentItemBuilder.create().setId(ACOG).build());
            sp.inventoryMenu.broadcastChanges();
            return null;
        }));
        await("mount: ACOG in the off-hand", 40, mc -> IAttachment.getIAttachmentOrNull(mc.player.getOffhandItem()) != null, () -> "");
        run(mc -> {
            AttachmentModule.Guide guide = AttachmentModule.guide(mc.player, VrGunController.lastPose());
            check("mount: guide shows where the scope goes", guide != null && guide.fits(), String.valueOf(guide));
        });
        sleep(10);
        run(mc -> {
            check("mount: nothing happens with the hand away from the gun", serverGun().scope.equals("tacz:empty"),
                    serverGun().toString());
            screenshot(mc, "taczvr_fp_attach_guide.png");
        });
        run(mc -> {
            Vector3d slot = AttachmentModule.slotPosition(VrGunController.lastPose(), AttachmentType.SCOPE);
            check("mount: gun has a scope spot", slot != null, "");
            // the grip button isn't needed for mounting, Vivecraft keeps it (hotbar etc.)
            check("mount: grip stays free even at the spot", !ATTACHMENTS.isActive(mc.player, InteractionHand.OFF_HAND,
                    new Vec3(slot.x, slot.y, slot.z)), "");
            // a real controller can't get into the other one: 12cm off, above and to the left
            setOff(new Vector3d(slot).add(-0.07, 0.09, 0.03));
        });
        await("mount: snaps on when brought close, no button", 20, mc -> {
            GunState s = serverGun();
            return s.scope.equals(ACOG.toString()) && s.offHand.equals("empty");
        }, () -> serverGun().toString());
        run(mc -> check("mount: hand with a scope didn't grab the handguard", !VrGunController.lastPose().twoHanded, ""));
        // swapping scopes: the hand stays at the spot with the new one, it must not flip back and forth
        run(mc -> server(sp -> {
            sp.setItemInHand(InteractionHand.OFF_HAND, AttachmentItemBuilder.create().setId(ELCAN).build());
            sp.inventoryMenu.broadcastChanges();
            return null;
        }));
        sleep(15);
        run(mc -> check("swap: a scope arriving in a hand already at the spot waits", serverGun().scope.equals(ACOG.toString()),
                serverGun().toString()));
        run(mc -> setOff(idleOff()));
        sleep(3);
        run(mc -> setOff(new Vector3d(AttachmentModule.slotPosition(VrGunController.lastPose(), AttachmentType.SCOPE))
                .add(-0.07, 0.09, 0.03)));
        await("swap: new scope on, old one in the hand", 20, mc -> {
            GunState s = serverGun();
            return s.scope.equals(ELCAN.toString()) && s.offHand.equals(ACOG.toString());
        }, () -> serverGun().toString());
        sleep(15);
        run(mc -> check("swap: the old scope doesn't go straight back on", serverGun().scope.equals(ELCAN.toString()),
                serverGun().toString()));
        run(mc -> {
            setOff(idleOff());
            server(sp -> {
                sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                return null;
            });
        });
        sleep(5);
        run(mc -> check("mount: highlight gone with the hand away", AttachmentModule.hoverPoint() == null, ""));
        // aim down the scope: sight line through the eye
        run(mc -> {
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            Vec3 target = eye().add(0.0, 0.0, -0.1);
            Vec3 delta = target.subtract(pose.origin.x, pose.origin.y, pose.origin.z);
            rig(rigMain.add(delta), NORTH, idleOff());
        });
        await("ADS: TACZ aims when the sights reach the eye", 20,
                mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim(), () -> "");
        await("scope: magnified view switched on", 5, mc -> ScopeView.isViewing(), () -> "");
        run(mc -> {
            float fov = ScopeView.fovDegrees();
            log("scope fov=%.2f deg", fov);
            check("scope: field of view narrowed by the zoom", fov > 0.5F && fov < 20.0F, "fov " + fov);
            // what the eye sees: the scope picture (a stand-in texture here) must fill the eyepiece, not be hidden in it
            scopeEye = true;
        });
        sleep(3);
        run(mc -> {
            screenshot(mc, "taczvr_fp_scope.png");
            scopeEye = false;
        });
        run(mc -> restPose());
        await("ADS: stops when the gun is lowered", 20,
                mc -> !IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim() && !ScopeView.isViewing(), () -> "");
        run(mc -> setOff(new Vector3d(AttachmentModule.slotPosition(VrGunController.lastPose(), AttachmentType.SCOPE))
                .add(-0.06, 0.1, 0.0)));
        sleep(2);
        run(mc -> {
            GunPoseSolver.Pose pose = VrGunController.lastPose();
            check("remove: empty hand over the scope doesn't grab the handguard", !pose.twoHanded, "");
            check("remove: module takes grip on the scope", ATTACHMENTS.isActive(mc.player, InteractionHand.OFF_HAND, rigOff), "");
            check("remove: highlighted as remove", !AttachmentModule.hoverIsMount(), "");
            check("remove: grip accepted", ATTACHMENTS.onPress(mc.player, InteractionHand.OFF_HAND), "");
            int held = 0;
            while (ATTACHMENTS.onHoldTick(mc.player, InteractionHand.OFF_HAND) && held < 40) {
                held++;
            }
            check("remove: comes off after holding", held == 15, "released after " + (held + 1) + " ticks");
            ATTACHMENTS.onRelease(mc.player, InteractionHand.OFF_HAND);
        });
        await("remove: scope back in the off-hand", 20, mc -> {
            GunState s = serverGun();
            return s.scope.equals("tacz:empty") && s.offHand.equals(ELCAN.toString());
        }, () -> serverGun().toString());
        sleep(15);
        run(mc -> check("remove: taken off scope doesn't snap right back on", serverGun().scope.equals("tacz:empty"),
                serverGun().toString()));
        run(mc -> {
            setOff(idleOff());
            server(sp -> {
                sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                return null;
            });
        });
        sleep(5);
    }

    /**
     * Handing the gun to someone else and back.
     */
    private static void handoff() {
        run(mc -> {
            boolean[] r = server(sp -> {
                FakePlayer other = FakePlayerFactory.get(sp.serverLevel(),
                        new GameProfile(UUID.nameUUIDFromBytes("taczvr_selftest".getBytes()), "TaczVRTest"));
                other.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                boolean dirt = HandoffPacket.canHandOff(new ItemStack(Items.DIRT));
                boolean gave = HandoffPacket.transfer(sp, other);
                boolean arrived = IGun.getIGunOrNull(other.getMainHandItem()) != null && sp.getMainHandItem().isEmpty();
                boolean back = HandoffPacket.transfer(other, sp);
                boolean returned = IGun.getIGunOrNull(sp.getMainHandItem()) != null && other.getMainHandItem().isEmpty();
                return new boolean[]{dirt, gave, arrived, back, returned};
            });
            check("handoff: only gun items can be handed over", !r[0], "");
            check("handoff: gun handed to the other player", r[1] && r[2], "");
            check("handoff: and handed back", r[3] && r[4], "");
        });
        sleep(5);
    }

    private static void previews() {
        run(mc -> {
            VrClient.testPose = null;
            VrCommon.testForceVr = false;
            previewView = 0;
        });
        sleep(25);
        run(mc -> screenshot(mc, "taczvr_selftest_side.png"));
        run(mc -> previewView = 1);
        sleep(25);
        run(mc -> screenshot(mc, "taczvr_selftest_behind.png"));
        finish();
    }

    private static void finish() {
        run(mc -> {
            log("DONE, passed=%d failed=%d", passes, failures);
            if (Boolean.getBoolean("taczvr.selftest.exit")) {
                mc.stop();
            }
        });
    }

    // --- the fake VR rig ---

    private static Vec3 eye() {
        return Minecraft.getInstance().player.getEyePosition();
    }

    private static void rig(Vec3 main, Quaternionfc rot, @Nullable Vec3 off) {
        rigMain = main;
        rigRot = rot;
        rigOff = off;
        VrClient.testPose = new FakePose(main, rot, off, eye(), NORTH);
    }

    private static void setOff(Vector3d off) {
        rig(rigMain, rigRot, new Vec3(off.x, off.y, off.z));
    }

    private static void setOff(Vec3 off) {
        rig(rigMain, rigRot, off);
    }

    /**
     * Right hand in front of the chest, pointing where the head looks, left hand hanging free.
     */
    private static void restPose() {
        rig(eye().add(0.13, -0.17, -0.42), NORTH, idleOff());
    }

    private static Vec3 idleOff() {
        return eye().add(-0.35, -0.35, -0.3);
    }

    private static Vec3 beltPos() {
        return eye().add(-0.15, -0.75, 0.05);
    }

    private static void room(Vec3 hand) {
        roomHand = hand;
        VrClient.testRoomPose = new FakePose(hand, EAST, null, new Vec3(0.0, 1.6, 0.0), NORTH);
    }

    // --- state ---

    private static int clientAmmo(Minecraft mc) {
        ItemStack gun = mc.player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        return iGun == null ? -1 : iGun.getCurrentAmmoCount(gun);
    }

    private static boolean clientBarrel(Minecraft mc) {
        ItemStack gun = mc.player.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        return iGun != null && iGun.hasBulletInBarrel(gun);
    }

    private static boolean clientReloading(Minecraft mc) {
        return IGunOperator.fromLivingEntity(mc.player).getSynReloadState().getStateType().isReloading();
    }

    private static Void setGun(ServerPlayer sp, int ammo, boolean barrel) {
        ItemStack gun = sp.getMainHandItem();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun != null) {
            iGun.setCurrentAmmoCount(gun, ammo);
            iGun.setBulletInBarrel(gun, barrel);
            sp.inventoryMenu.broadcastChanges();
        }
        return null;
    }

    private static GunState serverGun() {
        return server(sp -> {
            ItemStack gun = sp.getMainHandItem();
            IGun iGun = IGun.getIGunOrNull(gun);
            int inventory = 0;
            for (ItemStack stack : sp.getInventory().items) {
                IAmmo ammo = IAmmo.getIAmmoOrNull(stack);
                if (ammo != null && iGun != null && ammo.isAmmoOfGun(gun, stack)) {
                    inventory += stack.getCount();
                }
            }
            ItemStack off = sp.getOffhandItem();
            IAttachment attachment = IAttachment.getIAttachmentOrNull(off);
            String offHand = off.isEmpty() ? "empty" : attachment != null ? attachment.getAttachmentId(off).toString()
                    : BuiltInRegistries.ITEM.getKey(off.getItem()).toString();
            if (iGun == null) {
                return new GunState(-1, false, "", inventory, offHand, false);
            }
            return new GunState(iGun.getCurrentAmmoCount(gun), iGun.hasBulletInBarrel(gun),
                    iGun.getAttachmentId(gun, AttachmentType.SCOPE).toString(), inventory, offHand, true);
        });
    }

    private static float pigHealthNow() {
        return server(sp -> pig == null ? -1.0F : pig.getHealth());
    }

    private static <T> T server(Function<ServerPlayer, T> function) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        UUID id = mc.player.getUUID();
        return server.submit(() -> function.apply(server.getPlayerList().getPlayer(id))).join();
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (captureBullet && !event.getLevel().isClientSide() && bulletPos == null
                && event.getEntity() instanceof EntityKineticBullet bullet) {
            bulletVel = bullet.getDeltaMovement();
            bulletPos = bullet.position();
        }
    }

    /**
     * Outside VR the window may not have focus; TACZ only shoots with a grabbed mouse, like it is in VR.
     */
    private static void grabMouse(Minecraft mc) {
        try {
            Field field = MouseHandler.class.getDeclaredField("mouseGrabbed");
            field.setAccessible(true);
            field.setBoolean(mc.mouseHandler, true);
        } catch (Throwable t) {
            log("could not mark the mouse grabbed: %s", t);
        }
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

    private static void await(String name, int timeoutTicks, Check check, Supplier<String> detail) {
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

    // --- previews, drawn by onRender at the end ---

    /**
     * Draws fake-VR guns in front of the camera: an AK held one-handed and one held two-handed, plus a scoped AK.
     * The controller gizmo shows red = controller +X (right), green = +Y (up), blue = pointing direction (-Z).
     */
    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || mc.level == null || mc.player == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        VrPose fake = VrClient.testPose;
        if (scopeEye && fake != null) {
            GunPoseSolver.Pose pose = GunPoseSolver.solve(mc.player, mc.player.getMainHandItem(), fake, Vec3.ZERO, 1.0F, 0.0F, 0.0F);
            if (pose != null) {
                ScopeView.drawEyepiece(poseStack, cam, pose, standInTexture(mc));
            }
        }
        if (previewView < 0) {
            return;
        }
        // camera looks north (-Z). view 0: guns point east (+X), seen from their left side. view 1: guns point north, seen from behind
        Quaternionf pointing = previewView == 0 ? new Quaternionf(EAST) : new Quaternionf();
        double[][] slots = previewView == 0
                ? new double[][]{{-0.45, -0.15, -1.1}, {0.25, -0.15, -1.1}, {-0.1, 0.3, -1.1}}
                : new double[][]{{-0.35, -0.15, -0.9}, {0.35, -0.15, -0.9}, {0.0, 0.25, -0.9}};
        for (int i = 0; i < 3; i++) {
            Vec3 controller = cam.add(slots[i][0], slots[i][1], slots[i][2]);
            ItemStack stack = i == 2 ? scopedAk() : GunItemBuilder.create().setId(AK).build();
            Vec3 off = null;
            if (i == 1) {
                // off-hand 35cm down the barrel and 5cm up: should switch to two-handed and tilt the barrel up
                Vector3f f = pointing.transform(new Vector3f(0, 0.05F, -0.35F));
                off = controller.add(f.x, f.y, f.z);
            }
            GunPoseSolver.Pose pose = GunPoseSolver.solve(UUID.nameUUIDFromBytes(new byte[]{(byte) i}), stack,
                    new FakePose(controller, pointing, off), Vec3.ZERO, 1.0F, 0.0F, 0.0F);
            if (pose == null) {
                continue;
            }
            VrGunRenderer.drawPose(mc, pose, stack, poseStack, cam, event.getPartialTick(), false, null, mc.player);
            Vector3f x = pointing.transform(new Vector3f(1, 0, 0));
            Vector3f y = pointing.transform(new Vector3f(0, 1, 0));
            Vector3f z = pointing.transform(new Vector3f(0, 0, -1));
            VrGunRenderer.drawLine(mc, poseStack, cam, controller, new Vec3(x.x, x.y, x.z), 0.1, 255, 0, 0);
            VrGunRenderer.drawLine(mc, poseStack, cam, controller, new Vec3(y.x, y.y, y.z), 0.1, 0, 255, 0);
            VrGunRenderer.drawLine(mc, poseStack, cam, controller, new Vec3(z.x, z.y, z.z), 0.1, 0, 80, 255);
            Vec3 bullet = pose.bulletDirection(25.0);
            VrGunRenderer.drawLine(mc, poseStack, cam, new Vec3(pose.muzzle.x, pose.muzzle.y, pose.muzzle.z), bullet, 1.5, 255, 255, 0);
            if (off != null) {
                VrGunRenderer.drawLine(mc, poseStack, cam, off, new Vec3(0, 1, 0), 0.05, 255, 0, 255);
            }
            if (i == 0) {
                // a loose magazine like the one held in the off-hand, and where the charging handle is grabbed
                Vector3f down = pointing.transform(new Vector3f(0.0F, -0.22F, 0.1F));
                MagazineHandler.drawMagazine(mc, pose, poseStack, cam, controller.add(down.x, down.y, down.z),
                        pointing, pose.scale);
                VrGunRenderer.drawMarker(mc, poseStack, cam, MagazineHandler.chargingHandle(pose), false);
            }
            if (i == 2) {
                // where attachments get mounted by hand
                for (AttachmentType type : AttachmentType.values()) {
                    Vector3d slot = type == AttachmentType.NONE ? null : AttachmentModule.slotPosition(pose, type);
                    if (slot != null) {
                        VrGunRenderer.drawMarker(mc, poseStack, cam, slot, true);
                    }
                }
                // the scope picture, with a stand-in texture since there's no VR scope camera here
                ScopeView.drawEyepiece(poseStack, cam, pose, standInTexture(mc));
            }
        }
    }

    private static ItemStack lrItem(String type, String tag, String id) {
        net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation("lrtactical", type));
        ItemStack stack = new ItemStack(item);
        stack.getOrCreateTag().putString(tag, "lrtactical:" + id);
        return stack;
    }

    /**
     * LesRaisins Tactical Equipments: its knives, grenades and medkits draw nothing in first person outside its own
     * hand rendering, so in VR we draw them at the controller. Flat screen stays LesRaisins' own.
     */
    private static void lesRaisins() {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("lrtactical")) {
            run(mc -> log("LesRaisins not installed, skipped"));
            return;
        }
        run(mc -> {
            VrCommon.testForceVr = false;
            VrClient.testPose = null;
            mc.options.hideGui = false;
            give(InteractionHand.MAIN_HAND, lrItem("melee", "MeleeWeaponId", "dagger"));
            give(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            framesBefore = LrTacticalVr.drawn;
        });
        sleep(20);
        run(mc -> {
            check("LesRaisins: a dagger is one of its items", LrTacticalVr.isLrItem(mc.player.getMainHandItem()), "");
            check("LesRaisins: flat screen left to LesRaisins", LrTacticalVr.drawn == framesBefore, "");
            screenshot(mc, "taczvr_lr_flat_dagger.png");
            restPose();
            framesBefore = LrTacticalVr.drawn;
        });
        sleep(5);
        run(mc -> {
            check("LesRaisins VR: the dagger is drawn in the hand", LrTacticalVr.drawn > framesBefore, "");
            // without the flat hand pass (and so LesRaisins' own first person) only our drawing shows: the
            // controllers half a metre ahead, pointing away (right hand) and to the right (left hand)
            mc.options.hideGui = true;
            give(InteractionHand.OFF_HAND, lrItem("throwable", "ThrowableId", "m67"));
            Vec3 e = eye();
            VrClient.testPose = new FakePose(e.add(0.12, -0.12, -0.5), NORTH, e.add(-0.22, -0.12, -0.55), e, NORTH);
        });
        sleep(5);
        run(mc -> screenshot(mc, "taczvr_lr_vr_dagger.png"));
        for (String[] item : new String[][]{{"melee", "MeleeWeaponId", "karambit"}, {"melee", "MeleeWeaponId", "baseball_bat"},
                {"consumable", "ConsumableId", "ai2"}, {"throwable", "ThrowableId", "flash_grenade"}}) {
            run(mc -> {
                give(InteractionHand.MAIN_HAND, lrItem(item[0], item[1], item[2]));
                Vec3 e = eye();
                // from the side this time: pointing east
                VrClient.testPose = new FakePose(e.add(-0.1, -0.1, -0.55), EAST, null, e, NORTH);
            });
            sleep(6);
            run(mc -> screenshot(mc, "taczvr_lr_vr_" + item[2] + ".png"));
        }
        // how someone else holds it, for comparison
        run(mc -> {
            VrClient.testPose = null;
            RemotePlayer other = new RemotePlayer(mc.level, new GameProfile(UUID.nameUUIDFromBytes("taczvr_selftest_lr".getBytes()), "Raisin"));
            Vec3 at = mc.player.position().add(0.0, 0.0, -2.2);
            other.moveTo(at.x, at.y, at.z, 30.0F, 0.0F);
            other.setYHeadRot(30.0F);
            other.yBodyRot = 30.0F;
            other.setItemInHand(InteractionHand.MAIN_HAND, lrItem("melee", "MeleeWeaponId", "dagger"));
            other.setItemInHand(InteractionHand.OFF_HAND, lrItem("throwable", "ThrowableId", "m67"));
            other.setId(REMOTE_ENTITY_ID - 2);
            mc.level.addPlayer(REMOTE_ENTITY_ID - 2, other);
            mc.options.hideGui = true;
        });
        sleep(10);
        run(mc -> {
            screenshot(mc, "taczvr_lr_other.png");
            mc.level.removeEntity(REMOTE_ENTITY_ID - 2, Entity.RemovalReason.DISCARDED);
            give(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).setAmmoCount(30).setAmmoInBarrel(true).build());
            give(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        });
        sleep(5);
    }

    private static String hintText(InteractionHand hand) {
        StringBuilder text = new StringBuilder();
        for (Component line : AttachmentHint.lines(hand)) {
            text.append(line.getString()).append(" | ");
        }
        return text.toString();
    }

    /**
     * Holding an attachment in VR, a label over the hand says which of your guns it fits.
     */
    private static void attachmentHint() {
        run(mc -> {
            server(sp -> {
                Inventory inv = sp.getInventory();
                inv.setItem(0, GunItemBuilder.create().setId(AK).setAmmoCount(30).build());
                inv.setItem(3, GunItemBuilder.create().setId(GLOCK).build());
                inv.setItem(5, GunItemBuilder.create().setId(new ResourceLocation("tacz:m4a1")).build());
                inv.setItem(20, GunItemBuilder.create().setId(new ResourceLocation("tacz:scar_l")).build());
                inv.selected = 0;
                sp.connection.send(new ClientboundSetCarriedItemPacket(0));
                sp.setItemInHand(InteractionHand.OFF_HAND, AttachmentItemBuilder.create().setId(ACOG).build());
                sp.inventoryMenu.broadcastChanges();
                return null;
            });
            restPose();
            setOff(eye().add(-0.1, -0.12, -0.35));
        });
        await("hint: label for the scope in the off-hand", 40, mc -> !AttachmentHint.lines(InteractionHand.OFF_HAND).isEmpty(), () -> "");
        sleep(5);
        run(mc -> {
            String text = hintText(InteractionHand.OFF_HAND);
            log("hint: %s", text);
            String m4 = GunItemBuilder.create().setId(new ResourceLocation("tacz:m4a1")).build().getHoverName().getString();
            String glock = GunItemBuilder.create().setId(GLOCK).build().getHoverName().getString();
            check("hint: it fits the gun in your hand", text.contains(Component.translatable("taczvr.hint.held_fits").getString()), text);
            check("hint: lists the guns it fits, with their slot", text.contains(m4 + " (6)") && text.contains("(" + Component.translatable("taczvr.hint.bag").getString() + ")"), text);
            check("hint: not the pistol it doesn't fit", !text.contains(glock), text);
            check("hint: drawn over the hand", AttachmentHint.drawn > 0, "");
            screenshot(mc, "taczvr_attachment_hint.png");
            server(sp -> {
                sp.setItemInHand(InteractionHand.OFF_HAND, AttachmentItemBuilder.create().setId(new ResourceLocation("tacz:deagle_golden_long_barrel")).build());
                sp.inventoryMenu.broadcastChanges();
                return null;
            });
        });
        sleep(10);
        run(mc -> {
            String text = hintText(InteractionHand.OFF_HAND);
            log("hint: %s", text);
            check("hint: a part that fits nothing says so", text.contains(Component.translatable("taczvr.hint.held_no").getString())
                    && text.contains(Component.translatable("taczvr.hint.none").getString()), text);
            screenshot(mc, "taczvr_attachment_hint_none.png");
            server(sp -> {
                sp.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                sp.getInventory().setItem(3, ItemStack.EMPTY);
                sp.getInventory().setItem(5, ItemStack.EMPTY);
                sp.getInventory().setItem(20, ItemStack.EMPTY);
                sp.getInventory().setItem(0, GunItemBuilder.create().setId(AK).setAmmoCount(30).setAmmoInBarrel(true).build());
                sp.inventoryMenu.broadcastChanges();
                return null;
            });
            restPose();
        });
        sleep(5);
        run(mc -> check("hint: gone with the part put away", AttachmentHint.lines(InteractionHand.OFF_HAND).isEmpty(), ""));
    }

    private static final ResourceLocation DEAGLE_GOLDEN = new ResourceLocation("tacz:deagle_golden");
    private static final ResourceLocation CONTENDER = new ResourceLocation("tacz:scope_contender");

    /**
     * The golden Deagle with its pistol scope (long eye relief): held close and at arm's length.
     */
    private static void pistolScope() {
        run(mc -> {
            give(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(DEAGLE_GOLDEN).setAmmoCount(9).setAmmoInBarrel(true)
                    .putAttachment(AttachmentType.SCOPE, CONTENDER).build());
            restPose();
        });
        sleep(10);
        for (double behind : new double[]{0.1, 0.3}) {
            run(mc -> {
                ItemStack stack = mc.player.getMainHandItem();
                IGun iGun = IGun.getIGunOrNull(stack);
                var index = TimelessAPI.getClientAttachmentIndex(CONTENDER).orElse(null);
                log("deagle: gun %s scope %s isScope=%s zoom=%.2f eyepiece=%.3f views=%s", iGun == null ? "none" : iGun.getGunId(stack),
                        iGun == null ? "none" : iGun.getAttachmentId(stack, AttachmentType.SCOPE), index != null && index.isScope(),
                        iGun == null ? 0.0F : iGun.getAimingZoom(stack), index == null ? -1.0F : ScopeView.eyepieceDistance(index, 0),
                        index == null ? "-" : java.util.Arrays.toString(index.getViews()));
                GunPoseSolver.Pose pose = VrGunController.lastPose();
                Vec3 target = eye().add(0.0, 0.0, -behind);
                rig(rigMain.add(target.subtract(pose.origin.x, pose.origin.y, pose.origin.z)), NORTH, idleOff());
            });
            // TACZ only aims once the gun is drawn, which takes longer after some items than others
            await("pistol scope " + behind + " m: aims", 40, mc -> IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim(), () -> "");
            run(mc -> {
                GunPoseSolver.Pose pose = VrGunController.lastPose();
                boolean aim = IClientPlayerGunOperator.fromLocalPlayer(mc.player).isAim();
                log("deagle %.1f m: origin %s eye %s forward %s grip %s aim=%s viewing=%s fov=%.2f scale=%.3f", behind, v(pose.origin), v(eye()),
                        v(pose.forward), v(pose.grip), aim, ScopeView.isViewing(), ScopeView.fovDegrees(), pose.scale);
                check("pistol scope " + behind + " m: magnified view on", ScopeView.isViewing(), "");
                scopeEye = true;
            });
            sleep(3);
            run(mc -> {
                screenshot(mc, "taczvr_deagle_scope_" + (int) (behind * 100) + ".png");
                scopeEye = false;
                restPose();
            });
            sleep(10);
        }
        run(mc -> give(InteractionHand.MAIN_HAND, GunItemBuilder.create().setId(AK).setAmmoCount(30).setAmmoInBarrel(true).build()));
        sleep(5);
    }

    private static int standInTexture(Minecraft mc) {
        return mc.getTextureManager().getTexture(new ResourceLocation("textures/block/diamond_block.png")).getId();
    }

    private static ItemStack scopedAk() {
        return GunItemBuilder.create().setId(AK).putAttachment(AttachmentType.SCOPE, ACOG).build();
    }

    // --- static checks ---

    /**
     * AK with an ACOG: aim frame through the scope, eyepiece distance, and where the attachment slots are.
     */
    private static void scopeAndSlots() {
        try {
            ItemStack stack = scopedAk();
            GunPoseSolver.Pose pose = GunPoseSolver.solve(FAKE_PLAYER, stack, new FakePose(Vec3.ZERO, new Quaternionf(), null),
                    Vec3.ZERO, 1.0F, 0.0F, 0.0F);
            if (pose == null) {
                fail("scoped ak pose", "no pose");
                return;
            }
            log("scoped ak: sightOrigin=%s muzzle=%s", v(pose.origin), v(pose.muzzle));
            var index = TimelessAPI.getClientAttachmentIndex(ACOG).orElse(null);
            if (index == null) {
                fail("scoped ak", "no ACOG index");
                return;
            }
            IGun iGun = IGun.getIGunOrNull(stack);
            log("scoped ak: isScope=%s zoom=%.2f eyepieceDistance=%.3f (aim frame units)", index.isScope(),
                    iGun.getAimingZoom(stack), ScopeView.eyepieceDistance(index, 0));
            for (AttachmentType type : AttachmentType.values()) {
                if (type == AttachmentType.NONE) {
                    continue;
                }
                Vector3d slot = AttachmentModule.slotPosition(pose, type);
                log("  slot %s -> %s", type, slot == null ? "none" : v(slot));
            }
            GunPoseSolver.forget(FAKE_PLAYER);
            pass("scoped ak pose");
        } catch (Throwable t) {
            failures++;
            TaczVR.LOGGER.error("[SELFTEST] FAIL scope/slot test", t);
        }
    }

    private static void poseMath(String gunId) {
        try {
            ItemStack stack = GunItemBuilder.create().setId(new ResourceLocation(gunId)).build();
            // controller at the origin, pointing at -Z (north), no roll
            FakePose oneHand = new FakePose(Vec3.ZERO, new Quaternionf(), null);
            GunPoseSolver.Pose pose = GunPoseSolver.solve(FAKE_PLAYER, stack, oneHand, Vec3.ZERO, 1.0F, 0.0F, 0.0F);
            if (pose == null) {
                fail(gunId + " pose", "no pose (stack empty=" + stack.isEmpty() + ")");
                return;
            }
            log("%s one hand: scale=%.3f grip=%s muzzle=%s sightOrigin=%s forward=%s mag=%s",
                    gunId, pose.scale, v(pose.grip), v(pose.muzzle), v(pose.origin), v(pose.forward),
                    pose.magazine == null ? "none" : v(pose.magazine));
            // off-hand a bit off the barrel, 60% of the way to the muzzle
            Vector3d at = new Vector3d(pose.muzzle).sub(pose.grip).mul(0.6).add(pose.grip).add(-0.03, 0.04, 0.0);
            GunPoseSolver.Pose pose2 = GunPoseSolver.solve(FAKE_PLAYER, stack,
                    new FakePose(Vec3.ZERO, new Quaternionf(), new Vec3(at.x, at.y, at.z)), Vec3.ZERO, 1.0F, 0.0F, 0.0F);
            check(gunId + " pose: points where the controller points", pose.forward.z < -0.99, v(pose.forward));
            // a pistol is too short for a handguard, the support hand never takes over its aim
            boolean pistol = pose.muzzle.distance(pose.grip) < 0.2;
            check(gunId + (pistol ? " pose: pistol stays one-handed" : " pose: two hands on the handguard"),
                    pose2 != null && pose2.twoHanded != pistol, "");
            GunPoseSolver.forget(FAKE_PLAYER);
        } catch (Throwable t) {
            failures++;
            TaczVR.LOGGER.error("[SELFTEST] FAIL pose math for " + gunId, t);
        }
    }

    private static void patched(String className) {
        try {
            Class<?> cls = Class.forName(className, true, SelfTest.class.getClassLoader());
            int count = 0;
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().contains("taczvr$")) {
                    count++;
                }
            }
            check("mixin applied: " + className + " (" + count + ")", count > 0, "no injected handlers");
        } catch (Throwable t) {
            fail("mixin applied: " + className, t.toString());
        }
    }

    private static void screenshot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(),
                message -> log("screenshot: %s", message.getString()));
    }

    private static String v(Vec3 p) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", p.x, p.y, p.z);
    }

    private static String v(Vector3d p) {
        return String.format(Locale.ROOT, "(%.3f, %.3f, %.3f)", p.x, p.y, p.z);
    }

    private static void log(String format, Object... args) {
        TaczVR.LOGGER.info("[SELFTEST] " + String.format(Locale.ROOT, format, args));
    }

    private record FakePart(Vec3 pos, Quaternionfc rot) implements VrPart {
        @Override
        public Vec3 getPos() {
            return this.pos;
        }

        @Override
        public Vec3 getDir() {
            Vector3f d = this.rot.transform(new Vector3f(0, 0, -1));
            return new Vec3(d.x, d.y, d.z);
        }

        @Override
        public Quaternionfc getRotation() {
            return this.rot;
        }
    }

    private record FakePose(Vec3 mainPos, Quaternionfc mainRot, @Nullable Vec3 offPos, Vec3 headPos,
                            Quaternionfc headRot) implements VrPose {
        FakePose(Vec3 mainPos, Quaternionfc mainRot, @Nullable Vec3 offPos) {
            this(mainPos, mainRot, offPos, mainPos.add(0, 0.3, 0.4), new Quaternionf());
        }

        @Override
        public VrPart getHead() {
            return new FakePart(this.headPos, this.headRot);
        }

        @Override
        public VrPart getMainHand() {
            return new FakePart(this.mainPos, this.mainRot);
        }

        @Override
        public @Nullable VrPart getOffHand() {
            return this.offPos == null ? null : new FakePart(this.offPos, new Quaternionf());
        }

        @Override
        public boolean isSeated() {
            return false;
        }

        @Override
        public boolean isLeftHanded() {
            return testLeftHanded;
        }
    }
}
