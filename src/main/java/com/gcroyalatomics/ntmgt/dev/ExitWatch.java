package com.gcroyalatomics.ntmgt.dev;

import com.gcroyalatomics.ntmgt.NtmGt;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * After a dev run stops the game, a thread that is not a daemon can keep the JVM (and the gradle task waiting on it)
 * alive. Ten seconds after the game thread ends the threads still running go to exit-threads.txt and the JVM halts.
 */
final class ExitWatch {
    private ExitWatch() {}

    static void start() {
        Thread game = Thread.currentThread();
        Thread t = new Thread(() -> {
            try {
                game.join();
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                return;
            }
            StringBuilder out = new StringBuilder("still running 10 s after the game stopped:\n");
            Thread.getAllStackTraces().forEach((th, stack) -> {
                if (th.isDaemon() || th == Thread.currentThread()) return;
                out.append('"').append(th.getName()).append("\" ").append(th.getState()).append('\n');
                for (var s : stack) out.append("    at ").append(s).append('\n');
            });
            NtmGt.LOGGER.error("[ntmgt] exit watch: {}", out);
            try {
                Files.writeString(Path.of("exit-threads.txt"), out);
            } catch (Exception ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "ntmgt exit watch");
        t.setDaemon(true);
        t.start();
    }
}
