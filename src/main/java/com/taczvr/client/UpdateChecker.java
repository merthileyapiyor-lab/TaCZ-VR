package com.taczvr.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.taczvr.TaczVR;
import com.taczvr.TaczVRConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Looks on GitHub for a newer TaCZ VR once per game start and offers it on the title screen.
 * The jars are published in a public repository of their own, because the source repository is private.
 */
public final class UpdateChecker {
    /**
     * The newest releases. The latest one is for 1.20.1 (its older jars take the first jar of the latest release), the
     * 1.19.2 jars come in releases of their own.
     */
    static final String RELEASES_API = "https://api.github.com/repos/merthileyapiyor-lab/TaCZ-VR-releases/releases?per_page=30";
    private static final Pattern SAFE_JAR_NAME = Pattern.compile("[A-Za-z0-9._+-]+\\.jar");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    record Release(String version, String notes, String jarName, String jarUrl, long jarSize) {
    }

    // a newer release than ours, once the check found one
    @Nullable
    static volatile Release newer = null;
    private static boolean started = false;
    private static boolean offered = false;

    private UpdateChecker() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        // the self-test makes its world from the title screen, it checks with its own server instead
        if (started || !(event.getScreen() instanceof TitleScreen) || Boolean.getBoolean("taczvr.selftest")
                || !TaczVRConfig.CLIENT.updateCheck.get()) {
            return;
        }
        started = true;
        String url = System.getProperty("taczvr.update.url", RELEASES_API);
        String ours = System.getProperty("taczvr.update.pretend", currentVersion());
        CompletableFuture.runAsync(() -> {
            try {
                newer = check(url, ours);
                TaczVR.LOGGER.info("Update check: running {}, {}", ours, newer == null ? "up to date" : newer.version() + " is out");
            } catch (Exception e) {
                TaczVR.LOGGER.info("Update check failed: {}", e.toString());
            }
        });
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Release release = newer;
        if (event.phase == TickEvent.Phase.END && release != null && !offered && mc.screen instanceof TitleScreen) {
            offered = true;
            mc.setScreen(new UpdateScreen(mc.screen, release, currentVersion()));
        }
    }

    static String currentVersion() {
        return ModList.get().getModContainerById(TaczVR.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString()).orElse("0");
    }

    /**
     * The newest release with a jar for our Minecraft version, if it's newer than {@code ours}. {@code url} answers a
     * list of releases (newest first) or a single one.
     */
    @Nullable
    static Release check(String url, String ours) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "TaCZ-VR/" + ours)
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("GitHub answered " + response.statusCode());
        }
        JsonElement root = JsonParser.parseString(response.body());
        List<JsonObject> releases = new ArrayList<>();
        if (root.isJsonArray()) {
            root.getAsJsonArray().forEach(element -> releases.add(element.getAsJsonObject()));
        } else {
            releases.add(root.getAsJsonObject());
        }
        // every Minecraft version has its own jar, only ours will do; the version comes from its name, the tags of
        // 1.19.2 releases carry the Minecraft version too
        String prefix = TaczVR.MOD_ID + "-" + FMLLoader.versionInfo().mcVersion() + "-";
        for (JsonObject json : releases) {
            if (flag(json, "draft") || flag(json, "prerelease")) {
                continue;
            }
            for (JsonElement element : json.getAsJsonArray("assets")) {
                JsonObject asset = element.getAsJsonObject();
                String name = asset.get("name").getAsString();
                if (!name.startsWith(prefix) || !SAFE_JAR_NAME.matcher(name).matches()) {
                    continue;
                }
                String version = name.substring(prefix.length(), name.length() - ".jar".length());
                if (compareVersions(version, ours) <= 0) {
                    return null;
                }
                JsonElement body = json.get("body");
                String notes = body == null || body.isJsonNull() ? "" : body.getAsString();
                return new Release(version, notes, name, asset.get("browser_download_url").getAsString(), asset.get("size").getAsLong());
            }
        }
        return null;
    }

    private static boolean flag(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && !value.isJsonNull() && value.getAsBoolean();
    }

    /**
     * Compares dotted version numbers: 1.3.10 is newer than 1.3.9.
     */
    static int compareVersions(String a, String b) {
        String[] x = a.replaceFirst("^[vV]", "").split("[^0-9]+");
        String[] y = b.replaceFirst("^[vV]", "").split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int c = Long.compare(number(x, i), number(y, i));
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static long number(String[] parts, int i) {
        try {
            return i < parts.length && !parts[i].isEmpty() ? Long.parseLong(parts[i]) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Our own jar in the mods folder, or null when we don't run from one (the dev client).
     */
    @Nullable
    static Path currentJar() {
        return ModList.get().getModFileById(TaczVR.MOD_ID) == null ? null : jarOrNull(ModList.get().getModFileById(TaczVR.MOD_ID).getFile().getFilePath());
    }

    @Nullable
    private static Path jarOrNull(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar") ? path : null;
    }

    /**
     * Downloads the new jar next to {@code currentJar} and swaps it in: right away if the running jar can be
     * deleted, otherwise by a small process that waits for the game to close.
     *
     * @return true if it's swapped already, false if it happens once the game has closed
     */
    static boolean install(Release release, Path currentJar, IntConsumer percent) throws IOException, InterruptedException {
        if (!SAFE_JAR_NAME.matcher(release.jarName()).matches()) {
            throw new IOException("odd file name " + release.jarName());
        }
        Path mods = currentJar.toAbsolutePath().getParent();
        Path target = mods.resolve(release.jarName());
        // Forge only loads .jar files, so a half downloaded one is never picked up
        Path part = mods.resolve(release.jarName() + ".part");
        try {
            download(release, part, percent);
            verify(part, release);
        } catch (IOException | InterruptedException | RuntimeException e) {
            Files.deleteIfExists(part);
            throw e;
        }
        if (!target.equals(currentJar.toAbsolutePath())) {
            try {
                Files.delete(currentJar);
                Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException locked) {
                // Windows keeps the running jar locked, swap it once the game is gone
                TaczVR.LOGGER.info("Update: {} is in use, swapping it after the game closes", currentJar.getFileName());
            }
        }
        swapAfterExit(ProcessHandle.current().pid(), currentJar, part, target);
        return false;
    }

    private static void download(Release release, Path part, IntConsumer percent) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(release.jarUrl()))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "TaCZ-VR/" + currentVersion())
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("download answered " + response.statusCode());
        }
        long total = release.jarSize() > 0 ? release.jarSize() : response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
            byte[] buffer = new byte[16384];
            long done = 0;
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                done += read;
                if (total > 0) {
                    percent.accept((int) Math.min(100, done * 100 / total));
                }
            }
        }
    }

    /**
     * The download has the size GitHub said, and it is a TaCZ VR mod jar.
     */
    private static void verify(Path jar, Release release) throws IOException {
        if (release.jarSize() > 0 && Files.size(jar) != release.jarSize()) {
            throw new IOException("download is " + Files.size(jar) + " bytes, expected " + release.jarSize());
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry toml = zip.getEntry("META-INF/mods.toml");
            if (toml == null) {
                throw new IOException("not a mod jar");
            }
            String text = new String(zip.getInputStream(toml).readAllBytes(), StandardCharsets.UTF_8);
            if (!text.contains("modId = \"" + TaczVR.MOD_ID + "\"") && !text.contains("modId=\"" + TaczVR.MOD_ID + "\"")) {
                throw new IOException("not a TaCZ VR jar");
            }
        }
    }

    /**
     * Starts {@link JarSwap} in its own java process: it waits for {@code pid} to end, then deletes the old jar and
     * puts the new one in place. It runs from a copy of the class in the temp folder, so it doesn't hold our jar open.
     * Java rather than a script: Windows blocks hidden PowerShell started by Java.
     */
    static Process swapAfterExit(long pid, Path oldJar, Path part, Path target) throws IOException {
        Path classes = Files.createTempDirectory("taczvr-update");
        String name = JarSwap.class.getName();
        Path file = classes.resolve(name.replace('.', '/') + ".class");
        Files.createDirectories(file.getParent());
        try (InputStream in = JarSwap.class.getResourceAsStream(JarSwap.class.getSimpleName() + ".class")) {
            if (in == null) {
                throw new IOException("can't read " + name);
            }
            Files.copy(in, file);
        }
        List<String> command = List.of(javaExecutable(), "-cp", classes.toString(), name, String.valueOf(pid),
                oldJar.toAbsolutePath().toString(), part.toAbsolutePath().toString(), target.toAbsolutePath().toString());
        return new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
    }

    /**
     * The java the game runs on, javaw on Windows so no console window pops up.
     */
    private static String javaExecutable() {
        Path java = ProcessHandle.current().info().command().map(Path::of)
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java"));
        Path windowless = java.resolveSibling("javaw.exe");
        return Files.isRegularFile(windowless) ? windowless.toString() : java.toString();
    }
}
