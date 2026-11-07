package com.taczvr.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.logging.LogUtils;
import com.taczvr.VrCommon;
import com.taczvr.client.interact.BackDrawModule;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.GunItemBuilder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.vivecraft.client_vr.ClientDataHolderVR;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A scripted showcase video, only in the dev client:
 * {@code gradlew runClient -Ptrailer=<out.mp4> -Pshaders -Prundir=run-shaders}.
 * <p>
 * Vivecraft really renders through NullVR. The script moves NullVR's right controller and turns the room, and every
 * frame of the left eye goes to ffmpeg, timed by the wall clock so slow frames don't speed the video up. The
 * scenes: the gun in hand while looking around, the scope raised to the eye and a zombie shot, the gun drawn from
 * the back. Captions are added afterwards, at the times in {@link #SCENES}.
 */
public final class Trailer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FFMPEG = "C:\\Program Files\\FFmpeg\\bin\\ffmpeg.exe";
    private static final int W = 1920;
    private static final int H = 1080;
    private static final ResourceLocation AK = new ResourceLocation("tacz:ak47");
    private static final ResourceLocation ACOG = new ResourceLocation("tacz:scope_acog_ta31");
    private static final double LENGTH = 17.0;
    /**
     * Scene start times in seconds, for the captions.
     */
    public static final double[] SCENES = {0.0, 4.0, 10.2, 13.4};

    // NullVR's room: the head at (0, 1.62, 0) looking along -Z
    private static final Vector3f REST = new Vector3f(0.20F, 1.28F, -0.40F);
    private static final Vector3f SHOULDER = new Vector3f(0.14F, 1.66F, 0.17F);
    private static final Vector3f OFF_HAND = new Vector3f(-0.24F, 0.95F, -0.15F);
    private static final ByteBuffer END = ByteBuffer.allocate(0);

    private record Key(double t, Vector3f pos, Quaternionf rot) {
    }

    private record Turn(double t, float yaw) {
    }

    private record Event(double t, Consumer<Minecraft> action) {
    }

    private static boolean started;
    private static int worldTicks;
    private static boolean driving;
    private static boolean recording;
    private static boolean done;
    private static long t0 = -1L;
    private static Vector3f aim;
    private static List<Key> keys = List.of();
    private static final List<Turn> TURNS = List.of(new Turn(0.0, -35.0F), new Turn(3.6, 0.0F),
            new Turn(13.0, 0.0F), new Turn(16.8, 22.0F));
    private static final List<Event> EVENTS = new ArrayList<>();
    private static int nextEvent;
    private static Process ffmpeg;
    private static Thread writer;
    private static final BlockingQueue<ByteBuffer> FRAMES = new ArrayBlockingQueue<>(6);
    private static final BlockingQueue<ByteBuffer> FREE = new ArrayBlockingQueue<>(8);
    private static int captured;
    private static int dropped;
    private static TextureTarget capture;
    private static Method setKeyBindState;

    private Trailer() {
    }

    @SubscribeEvent
    public static void onTitle(ScreenEvent.Init.Post event) {
        if (started || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        started = true;
        Minecraft mc = Minecraft.getInstance();
        String name = "taczvr_trailer_" + System.currentTimeMillis();
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        // a dead zombie's experience orbs fly into the camera, huge up close
        rules.getRule(GameRules.RULE_DOMOBLOOT).set(false, null);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE, false, Difficulty.EASY, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.execute(() -> mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()));
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || !started || mc.player == null || mc.level == null || done) {
            return;
        }
        worldTicks++;
        if (worldTicks == 40) {
            setUp(mc);
        } else if (worldTicks >= 100 && worldTicks < 130) {
            // where the controller has to be for the eyepiece to sit 10 cm in front of the left eye
            moveEyepiece();
        } else if (worldTicks == 130) {
            aim = new Vector3f(offsets()[0]);
            LOGGER.info("[TRAILER] aim spot {}", aim);
            keys = List.of(new Key(0.0, REST, rest()), new Key(4.0, REST, rest()), new Key(5.2, aim, new Quaternionf()),
                    new Key(9.2, aim, new Quaternionf()), new Key(10.2, REST, rest()), new Key(10.5, REST, rest()),
                    new Key(11.1, SHOULDER, shoulder()), new Key(11.6, SHOULDER, shoulder()),
                    new Key(12.6, REST, rest()), new Key(LENGTH, REST, rest()));
            driving = true;
            spawnTargets(mc);
        } else if (worldTicks == 230) {
            startRecording();
        }
        if (recording && t0 >= 0L && seconds() > LENGTH) {
            stopRecording();
            done = true;
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!driving || mc.player == null) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            double t = t0 < 0L ? 0.0 : seconds();
            while (recording && t0 >= 0L && nextEvent < EVENTS.size() && EVENTS.get(nextEvent).t() <= t) {
                EVENTS.get(nextEvent++).action().accept(mc);
            }
            pose(t);
        } else if (recording) {
            captureFrame();
        }
    }

    private static void setUp(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        VrCommon.testNoSpread = true;
        // NullVR shows every controller buzz as music notes at the controller; a headset doesn't
        ClientDataHolderVR.getInstance().vrSettings.nullvrHaptics = false;
        mc.player.connection.sendCommand("time set 1000");
        mc.player.connection.sendCommand("weather clear");
        server(mc, sp -> {
            sp.teleportTo(sp.serverLevel(), 0.5, -60.0, 0.5, 180.0F, 0.0F);
            Inventory inventory = sp.getInventory();
            inventory.clearContent();
            inventory.setItem(0, GunItemBuilder.create().setId(AK).setAmmoCount(30).setAmmoInBarrel(true)
                    .putAttachment(AttachmentType.SCOPE, ACOG).build());
            inventory.selected = 0;
            sp.connection.send(new ClientboundSetCarriedItemPacket(0));
            sp.inventoryMenu.broadcastChanges();
        });
        Vector3f[] at = offsets();
        at[0].set(REST);
        rotations()[0].identity();
    }

    /**
     * Three zombies 24 blocks ahead, iron helmets against the sun; the middle one is in the line of fire.
     */
    private static void spawnTargets(Minecraft mc) {
        server(mc, sp -> {
            double[][] spots = {{-4.0, -27.0}, {0.0, -24.0}, {4.5, -28.0}};
            for (double[] spot : spots) {
                Zombie zombie = EntityType.ZOMBIE.create(sp.serverLevel());
                zombie.moveTo(sp.getX() + spot[0], sp.getY(), sp.getZ() + spot[1], 0.0F, 0.0F);
                zombie.setYHeadRot(0.0F);
                zombie.setNoAi(true);
                zombie.setPersistenceRequired();
                zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                sp.serverLevel().addFreshEntity(zombie);
            }
        });
    }

    // --- the script ---

    private static void scheduleEvents() {
        EVENTS.clear();
        for (double shot = 6.0; shot < 7.2; shot += 0.22) {
            EVENTS.add(new Event(shot, Trailer::pullTrigger));
            EVENTS.add(new Event(shot + 0.08, mc -> mc.options.keyAttack.setDown(false)));
        }
        for (double shot = 7.8; shot < 8.6; shot += 0.22) {
            EVENTS.add(new Event(shot, Trailer::pullTrigger));
            EVENTS.add(new Event(shot + 0.08, mc -> mc.options.keyAttack.setDown(false)));
        }
        // an empty hand, then the gun from the back
        EVENTS.add(new Event(10.4, mc -> mc.player.getInventory().selected = 1));
        EVENTS.add(new Event(11.35, mc -> new BackDrawModule().onPress(mc.player, InteractionHand.MAIN_HAND)));
        EVENTS.sort((a, b) -> Double.compare(a.t(), b.t()));
        nextEvent = 0;
    }

    private static void pose(double t) {
        Vector3f[] at = offsets();
        Quaternionf[] turn = rotations();
        Key from = keys.get(0);
        Key to = keys.get(keys.size() - 1);
        for (int i = 0; i + 1 < keys.size(); i++) {
            if (t >= keys.get(i).t() && t <= keys.get(i + 1).t()) {
                from = keys.get(i);
                to = keys.get(i + 1);
                break;
            }
        }
        float f = smooth(from.t() == to.t() ? 1.0 : (t - from.t()) / (to.t() - from.t()));
        Vector3f pos = new Vector3f(from.pos()).lerp(to.pos(), f);
        // a living hand never stands quite still, less so while aiming
        float sway = from.pos() == aim && to.pos() == aim ? 0.0015F : 0.004F;
        pos.add((float) Math.sin(t * 1.3) * sway, (float) Math.sin(t * 1.7 + 1.0) * sway, (float) Math.sin(t * 0.9 + 2.0) * sway);
        at[0].set(pos);
        turn[0].set(new Quaternionf(from.rot()).slerp(to.rot(), f));
        at[1].set(OFF_HAND);
        turn[1].identity();

        Turn a = TURNS.get(0);
        Turn b = TURNS.get(TURNS.size() - 1);
        for (int i = 0; i + 1 < TURNS.size(); i++) {
            if (t >= TURNS.get(i).t() && t <= TURNS.get(i + 1).t()) {
                a = TURNS.get(i);
                b = TURNS.get(i + 1);
                break;
            }
        }
        float g = smooth(a.t() == b.t() ? 1.0 : (t - a.t()) / (b.t() - a.t()));
        ClientDataHolderVR.getInstance().vrSettings.worldRotation = a.yaw() + (b.yaw() - a.yaw()) * g;
    }

    private static float smooth(double x) {
        double c = Math.max(0.0, Math.min(1.0, x));
        return (float) (c * c * (3.0 - 2.0 * c));
    }

    private static Quaternionf rest() {
        return new Quaternionf().rotateX((float) Math.toRadians(-8.0));
    }

    private static Quaternionf shoulder() {
        return new Quaternionf().rotateX((float) Math.toRadians(-75.0));
    }

    private static void moveEyepiece() {
        GunPoseSolver.Pose pose = VrGunController.lastPose();
        if (pose == null || VrClient.localTickPose() == null) {
            return;
        }
        rotations()[0].identity();
        float ipd = ClientDataHolderVR.getInstance().vrSettings.nullvrIPD;
        Vec3 eye = VrClient.localTickPose().getHead().getPos().add(-ipd * 0.5, 0.0, 0.0);
        Vec3 d = eye.add(0.0, 0.0, -0.10).subtract(pose.origin.x, pose.origin.y, pose.origin.z);
        offsets()[0].add((float) d.x, (float) d.y, (float) d.z);
    }

    private static void pullTrigger(Minecraft mc) {
        // TaCZ only shoots in an active window with the mouse grabbed, as in VR
        mc.setWindowActive(true);
        try {
            Field grabbed = MouseHandler.class.getDeclaredField("mouseGrabbed");
            grabbed.setAccessible(true);
            grabbed.setBoolean(mc.mouseHandler, true);
            if (setKeyBindState == null) {
                Class<?> input;
                try {
                    input = Class.forName("org.vivecraft.client_vr.provider.openvr_lwjgl.VRInputAction");
                } catch (ClassNotFoundException e) {
                    input = Class.forName("org.vivecraft.client_vr.provider.control.InputAction");
                }
                setKeyBindState = input.getMethod("setKeyBindState", KeyMapping.class, boolean.class);
            }
            setKeyBindState.invoke(null, mc.options.keyAttack, true);
        } catch (ReflectiveOperationException e) {
            LOGGER.error("[TRAILER] trigger", e);
        }
    }

    // --- recording ---

    private static void startRecording() {
        String out = System.getProperty("taczvr.trailer");
        try {
            ProcessBuilder builder = new ProcessBuilder(FFMPEG, "-y", "-use_wallclock_as_timestamps", "1",
                    "-f", "rawvideo", "-pix_fmt", "rgba", "-s", W + "x" + H, "-i", "-",
                    "-vf", "vflip", "-fps_mode", "cfr", "-r", "30",
                    "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", out);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(new File(out + ".ffmpeg.log"));
            ffmpeg = builder.start();
        } catch (Exception e) {
            LOGGER.error("[TRAILER] ffmpeg didn't start", e);
            done = true;
            return;
        }
        writer = new Thread(() -> {
            try (OutputStream stream = ffmpeg.getOutputStream()) {
                WritableByteChannel channel = Channels.newChannel(stream);
                while (true) {
                    ByteBuffer frame = FRAMES.take();
                    if (frame == END) {
                        break;
                    }
                    frame.rewind();
                    while (frame.hasRemaining()) {
                        channel.write(frame);
                    }
                    FREE.offer(frame);
                }
            } catch (Exception e) {
                LOGGER.error("[TRAILER] writing to ffmpeg", e);
            }
        }, "taczvr-trailer-ffmpeg");
        writer.start();
        scheduleEvents();
        recording = true;
        LOGGER.info("[TRAILER] recording to {}", out);
    }

    private static void captureFrame() {
        var eye = ClientDataHolderVR.getInstance().vrRenderer.framebufferEye[0];
        if (eye == null) {
            return;
        }
        if (t0 < 0L) {
            t0 = System.nanoTime();
        }
        if (capture == null) {
            capture = new TextureTarget(W, H, false, Minecraft.ON_OSX);
        }
        // a 16:9 band through the middle of the square eye picture
        int sw = eye.width;
        int band = sw * 9 / 16;
        int y0 = (eye.height - band) / 2;
        int read = GlStateManager._getInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int draw = GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, eye.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, capture.frameBufferId);
        GL30.glBlitFramebuffer(0, y0, sw, y0 + band, 0, 0, W, H, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, capture.frameBufferId);
        ByteBuffer frame = FREE.poll();
        if (frame == null) {
            frame = ByteBuffer.allocateDirect(W * H * 4);
        }
        frame.clear();
        GL11.glReadPixels(0, 0, W, H, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, frame);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
        if (FRAMES.offer(frame)) {
            captured++;
        } else {
            dropped++;
            FREE.offer(frame);
        }
    }

    private static void stopRecording() {
        recording = false;
        try {
            FRAMES.put(END);
            writer.join(30_000L);
            boolean ended = ffmpeg.waitFor(60, TimeUnit.SECONDS);
            LOGGER.info("[TRAILER] done: {} frames, {} dropped, ffmpeg {}", captured, dropped,
                    ended ? "exit " + ffmpeg.exitValue() : "still running");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // --- helpers ---

    private static double seconds() {
        return (System.nanoTime() - t0) / 1.0E9;
    }

    private static Vector3f[] offsets() {
        return (Vector3f[]) nullVr("deviceOffsets");
    }

    private static Quaternionf[] rotations() {
        return (Quaternionf[]) nullVr("deviceRotations");
    }

    private static Object nullVr(String field) {
        try {
            Object vr = ClientDataHolderVR.getInstance().vr;
            Field f = vr.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(vr);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("not NullVR", e);
        }
    }

    private static void server(Minecraft mc, Consumer<ServerPlayer> action) {
        mc.getSingleplayerServer().submit(() -> action.accept(
                mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()))).join();
    }
}
