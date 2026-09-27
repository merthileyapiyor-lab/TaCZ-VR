package com.taczvr.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Runs in its own small java process, from a copy of this class outside the mods folder: waits for the game to
 * close, then deletes the old TaCZ VR jar and puts the new one in its place. Uses nothing but the JDK.
 */
public final class JarSwap {
    private JarSwap() {
    }

    /**
     * Arguments: game pid, old jar, downloaded .part file, new jar name.
     */
    public static void main(String[] args) throws Exception {
        long pid = Long.parseLong(args[0]);
        Path old = Path.of(args[1]);
        Path part = Path.of(args[2]);
        Path target = Path.of(args[3]);
        Optional<ProcessHandle> game = ProcessHandle.of(pid);
        if (game.isPresent()) {
            try {
                game.get().onExit().get(30, TimeUnit.MINUTES);
            } catch (Exception e) {
                // still try, the jar may be free anyway
            }
        }
        // Windows can take a moment to let go of the file after the game is gone
        for (int i = 0; i < 60 && Files.exists(old); i++) {
            try {
                Files.delete(old);
            } catch (IOException e) {
                Thread.sleep(500);
            }
        }
        if (!Files.exists(old) && Files.exists(part)) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
