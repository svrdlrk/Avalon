package com.avalon.dnd.launcher;

import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class DevLauncher {

    private static final int SERVER_PORT = 8080;
    private static final int PLAYER_PORT = 5173;

    private static final List<Process> processes = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws Exception {
        Path root = Paths.get("").toAbsolutePath();

        log("root: " + root);

        Runtime.getRuntime().addShutdownHook(new Thread(DevLauncher::shutdownAll));

        ensurePortFree(PLAYER_PORT, "player-client");

        startServer(root);
        waitForPort(SERVER_PORT, "server");

        startDmClient(root);
        startPlayerClient(root);
        waitForPort(PLAYER_PORT, "player-client");

        openBrowser("http://localhost:" + PLAYER_PORT + "/");

        log("all requested processes started. Ctrl+C to stop.");

        // держим процесс живым
        Thread.currentThread().join();
    }

    // =========================
    // STARTERS
    // =========================

    private static void startServer(Path root) throws IOException {
        log("starting server...");
        processes.add(startProcess(root, "cmd", "/c", "gradlew.bat", ":server:bootRun"));
    }

    private static void startDmClient(Path root) throws IOException {
        log("starting dm-client...");
        processes.add(startProcess(root, "cmd", "/c", "gradlew.bat", ":dm-client:run"));
    }

    private static void startPlayerClient(Path root) throws IOException {
        log("starting player-client...");
        processes.add(startProcess(
                root.resolve("player-client"),
                "cmd", "/c", "npm.cmd", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173", "--strictPort"
        ));
    }

    private static Process startProcess(Path dir, String... command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();

        // логирование stdout
        new Thread(() -> streamOutput(process)).start();

        return process;
    }

    private static void streamOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[proc] " + line);
            }
        } catch (IOException ignored) {}
    }

    // =========================
    // PORT MANAGEMENT
    // =========================

    private static void ensurePortFree(int port, String name) throws IOException, InterruptedException {
        if (!isPortOpen(port)) return;

        log(name + " port " + port + " is busy. Trying to free...");

        killProcessOnPort(port);

        Thread.sleep(1000);

        if (isPortOpen(port)) {
            throw new RuntimeException("Port " + port + " is still busy. Stop it manually.");
        }

        log("port " + port + " is now free");
    }

    private static boolean isPortOpen(int port) {
        try (Socket ignored = new Socket("localhost", port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void waitForPort(int port, String name) throws InterruptedException {
        log("waiting for " + name + " on port " + port + "...");

        int retries = 60;
        while (retries-- > 0) {
            if (isPortOpen(port)) {
                log(name + " is ready at localhost:" + port);
                return;
            }
            Thread.sleep(1000);
        }

        throw new RuntimeException(name + " did not start on port " + port);
    }

    private static void killProcessOnPort(int port) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("cmd", "/c", "netstat -ano | findstr :" + port).start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;

        Set<String> pids = new HashSet<>();

        while ((line = reader.readLine()) != null) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 5) {
                pids.add(parts[4]);
            }
        }

        for (String pid : pids) {
            log("killing PID " + pid);
            new ProcessBuilder("cmd", "/c", "taskkill", "/PID", pid, "/F").start().waitFor();
        }
    }

    // =========================
    // SHUTDOWN
    // =========================

    private static void shutdownAll() {
        log("shutting down...");

        for (Process p : processes) {
            try {
                p.destroy();
                if (!p.waitFor(3, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (Exception ignored) {}
        }
    }

    // =========================
    // UTILS
    // =========================

    private static void openBrowser(String url) {
        try {
            Runtime.getRuntime().exec("cmd /c start " + url);
            log("opened browser: " + url);
        } catch (IOException e) {
            log("failed to open browser");
        }
    }

    private static void log(String msg) {
        System.out.println("[launcher] " + msg);
    }
}