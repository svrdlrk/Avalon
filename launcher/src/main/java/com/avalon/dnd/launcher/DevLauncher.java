package com.avalon.dnd.launcher;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DevLauncher {

    private static final int SERVER_PORT = 8080;
    private static final int PLAYER_PORT = 5173;
    private static final String WATCHDOG_FLAG = "--watchdog";
    private static final String SESSION_ENV = "AVALON_LAUNCHER_SESSION";

    private static final List<Process> processes = new CopyOnWriteArrayList<>();
    private static final Set<String> activeClients = ConcurrentHashMap.newKeySet();
    private static final ConcurrentMap<String, Process> trackedProcesses = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Long> lastHeartbeat = new ConcurrentHashMap<>();
    private static final AtomicBoolean shutdownRequested = new AtomicBoolean(false);
    private static volatile Path projectRoot;
    private static volatile String launcherSession;
    private static volatile String controlBaseUrl;
    private static volatile HttpServer controlServer;
    private static volatile ScheduledExecutorService monitorExecutor;

    public static void main(String[] args) throws Exception {
        if (args != null && args.length >= 3 && WATCHDOG_FLAG.equalsIgnoreCase(args[0])) {
            runWatchdog(Long.parseLong(args[1]), Path.of(args[2]), args.length > 3 ? args[3] : null);
            return;
        }

        Path root = findProjectRoot();
        projectRoot = root;
        launcherSession = UUID.randomUUID().toString();

        log("root: " + root);

        installSignalHandlers();
        startControlServer();
        Runtime.getRuntime().addShutdownHook(new Thread(DevLauncher::shutdownAll, "avalon-shutdown"));
        startCleanupWatchdog(root, launcherSession);

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

    private static Path findProjectRoot() {
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path current = cwd;

        while (current != null) {
            if (Files.exists(current.resolve("gradlew.bat")) && Files.exists(current.resolve("settings.gradle"))) {
                return current;
            }
            if (Files.exists(current.resolve("gradlew.bat")) && Files.exists(current.resolve("build.gradle"))) {
                return current;
            }
            current = current.getParent();
        }

        return cwd;
    }


    private static void startCleanupWatchdog(Path root, String session) {
        if (!isWindows()) {
            return;
        }
        try {
            long launcherPid = ProcessHandle.current().pid();
            String javaBin = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
            String classPath = System.getProperty("java.class.path", "");
            String className = DevLauncher.class.getName();

            ProcessBuilder pb = new ProcessBuilder(
                    "cmd", "/c", "start", "", "/b",
                    javaBin,
                    "-cp", classPath,
                    className,
                    WATCHDOG_FLAG,
                    Long.toString(launcherPid),
                    root.toAbsolutePath().normalize().toString(),
                    session == null ? "" : session
            );
            pb.redirectErrorStream(true);
            pb.start();
        } catch (Exception e) {
            log("watchdog start failed: " + e.getMessage());
        }
    }

    private static void startControlServer() {
        try {
            controlServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            controlServer.createContext("/launcher/client-closed", new ClientClosedHandler());
            controlServer.createContext("/launcher/client-heartbeat", new ClientHeartbeatHandler());
            controlServer.setExecutor(Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "avalon-launcher-control");
                t.setDaemon(true);
                return t;
            }));
            controlServer.start();
            int port = controlServer.getAddress().getPort();
            controlBaseUrl = "http://127.0.0.1:" + port;
            startHeartbeatMonitor();
            log("control server started on " + controlBaseUrl);
        } catch (Exception e) {
            log("control server start failed: " + e.getMessage());
        }
    }

    private static final class ClientClosedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                addCors(exchange);
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                String method = exchange.getRequestMethod();
                if (!"POST".equalsIgnoreCase(method) && !"GET".equalsIgnoreCase(method)) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String client = readQueryParam(exchange.getRequestURI(), "client");
                if (client == null || client.isBlank()) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                onClientClosed(client.trim().toLowerCase(Locale.ROOT));
                exchange.sendResponseHeaders(204, -1);
            } finally {
                exchange.close();
            }
        }
    }

    private static void addCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Access-Control-Max-Age", "86400");
    }

    private static String readQueryParam(URI uri, String key) {
        String query = uri == null ? null : uri.getRawQuery();
        if (query == null || query.isBlank()) return null;
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            String k = idx >= 0 ? part.substring(0, idx) : part;
            if (!key.equals(k)) continue;
            String v = idx >= 0 ? part.substring(idx + 1) : "";
            return java.net.URLDecoder.decode(v, StandardCharsets.UTF_8);
        }
        return null;
    }


    private static final class ClientHeartbeatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                addCors(exchange);
                if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }
                String method = exchange.getRequestMethod();
                if (!"POST".equalsIgnoreCase(method) && !"GET".equalsIgnoreCase(method)) {
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }
                String client = readQueryParam(exchange.getRequestURI(), "client");
                if (client == null || client.isBlank()) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                onClientHeartbeat(client.trim().toLowerCase(Locale.ROOT));
                exchange.sendResponseHeaders(204, -1);
            } finally {
                exchange.close();
            }
        }
    }

    private static void onClientHeartbeat(String client) {
        lastHeartbeat.put(client, System.currentTimeMillis());
        if ("dm".equals(client) || "player".equals(client)) {
            activeClients.add(client);
        }
    }

    private static void onClientClosed(String client) {
        if (!activeClients.remove(client)) {
            return;
        }
        log(client + " client closed");
        Process process = trackedProcesses.remove(client);
        if (process != null) {
            try {
                terminateProcessTree(process);
            } catch (Exception ignored) {
            }
        }
        maybeShutdownLauncher();
    }

    private static void registerClient(String name, Process process) {
        trackedProcesses.put(name, process);
        if ("dm".equals(name) || "player".equals(name)) {
            activeClients.add(name);
            lastHeartbeat.put(name, System.currentTimeMillis());
        }
        process.onExit().thenRun(() -> onTrackedProcessExit(name, process));
    }

    private static void onTrackedProcessExit(String name, Process process) {
        trackedProcesses.remove(name, process);
        if ("dm".equals(name) || "player".equals(name)) {
            activeClients.remove(name);
        }
        maybeShutdownLauncher();
    }

    private static void maybeShutdownLauncher() {
        if (!activeClients.isEmpty()) {
            return;
        }
        if (shutdownRequested.compareAndSet(false, true)) {
            log("all clients closed; exiting launcher");
            new Thread(() -> System.exit(0), "avalon-launcher-exit").start();
        }
    }

    private static void startHeartbeatMonitor() {
        monitorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "avalon-launcher-heartbeat-monitor");
            t.setDaemon(true);
            return t;
        });
        monitorExecutor.scheduleAtFixedRate(() -> {
            try {
                monitorPlayerHeartbeat();
            } catch (Exception e) {
                log("heartbeat monitor failed: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    private static void monitorPlayerHeartbeat() {
        if (!activeClients.contains("player")) {
            return;
        }
        Long last = lastHeartbeat.get("player");
        if (last == null) {
            return;
        }
        long age = System.currentTimeMillis() - last;
        if (age > 15000L) {
            log("player heartbeat stale (" + age + "ms), closing player process");
            onClientClosed("player");
        }
    }

    private static void runWatchdog(long launcherPid, Path root, String session) {
        log("watchdog active for pid " + launcherPid + " root " + root);
        while (ProcessHandle.of(launcherPid).map(ProcessHandle::isAlive).orElse(false)) {
            sleepQuietly(500);
        }
        sleepQuietly(700);
        try {
            stopGradleDaemons(root);
            killLingeringProcesses(root, launcherPid, true, session);
        } catch (Exception e) {
            log("watchdog cleanup failed: " + e.getMessage());
        }
    }

    private static void installSignalHandlers() {
        try {
            Class<?> signalClass = Class.forName("sun.misc.Signal");
            Class<?> handlerClass = Class.forName("sun.misc.SignalHandler");
            java.lang.reflect.Method handle = signalClass.getMethod("handle", signalClass, handlerClass);
            java.lang.reflect.InvocationHandler invocationHandler = (proxy, method, args) -> {
                shutdownAll();
                System.exit(130);
                return null;
            };
            Object handler = java.lang.reflect.Proxy.newProxyInstance(
                    DevLauncher.class.getClassLoader(),
                    new Class<?>[]{handlerClass},
                    invocationHandler
            );
            for (String name : List.of("INT", "TERM", "HUP")) {
                try {
                    Object signal = signalClass.getConstructor(String.class).newInstance(name);
                    handle.invoke(null, signal, handler);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // =========================
    // STARTERS
    // =========================

    private static void startServer(Path root) throws IOException {
        log("starting server...");
        Process process = startProcess(root, root, "server", "cmd", "/c", "gradlew.bat", "--no-daemon", ":server:bootRun");
        processes.add(process);
    }

    private static void startDmClient(Path root) throws IOException {
        log("starting dm-client...");
        Process process = startProcess(root, root, "dm", "cmd", "/c", "gradlew.bat", "--no-daemon", ":dm-client:run");
        processes.add(process);
    }

    private static void startPlayerClient(Path root) throws IOException {
        log("starting player-client...");
        Process process = startProcess(
                root.resolve("player-client"),
                root,
                "player",
                "cmd", "/c", "npm.cmd", "run", "dev", "--", "--host", "0.0.0.0", "--port", "5173", "--strictPort"
        );
        processes.add(process);
    }

    private static Process startProcess(Path dir, Path projectRoot, String name, String... command) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        if (projectRoot != null) {
            pb.environment().put("AVALON_PROJECT_ROOT", projectRoot.toAbsolutePath().normalize().toString());
        }
        if (launcherSession != null && !launcherSession.isBlank()) {
            pb.environment().put(SESSION_ENV, launcherSession);
        }
        if (controlBaseUrl != null && !controlBaseUrl.isBlank()) {
            pb.environment().put("AVALON_LAUNCHER_CONTROL_URL", controlBaseUrl);
            if ("player".equals(name)) {
                pb.environment().put("VITE_AVALON_LAUNCHER_CONTROL_URL", controlBaseUrl);
            }
        }

        Process process = pb.start();
        registerClient(name, process);

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

        try {
            if (monitorExecutor != null) {
                monitorExecutor.shutdownNow();
            }
        } catch (Exception ignored) {}
        try {
            if (controlServer != null) {
                controlServer.stop(0);
            }
        } catch (Exception ignored) {}

        List<Process> snapshot = new ArrayList<>(processes);
        Collections.reverse(snapshot);

        for (Process p : snapshot) {
            try {
                terminateProcessTree(p);
            } catch (Exception ignored) {}
        }

        try {
            stopGradleDaemons(projectRoot);
            killLingeringProcesses(projectRoot, ProcessHandle.current().pid(), false, launcherSession);
        } catch (Exception ignored) {}

        processes.clear();
        activeClients.clear();
        trackedProcesses.clear();
        lastHeartbeat.clear();
    }

    private static void terminateProcessTree(Process process) throws IOException, InterruptedException {
        if (process == null) {
            return;
        }

        ProcessHandle handle = process.toHandle();
        terminateHandle(handle);

        if (isWindows()) {
            long pid = process.pid();
            new ProcessBuilder("cmd", "/c", "taskkill", "/PID", Long.toString(pid), "/T", "/F")
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(10, TimeUnit.SECONDS);
        }

        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
    }

    private static void terminateHandle(ProcessHandle handle) {
        if (handle == null || !handle.isAlive()) {
            return;
        }

        handle.descendants().forEach(DevLauncher::terminateHandle);
        handle.destroy();

        try {
            handle.onExit().get(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {}

        if (handle.isAlive()) {
            handle.destroyForcibly();
            try {
                handle.onExit().get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {}
        }
    }

    private static void stopGradleDaemons(Path root) {
        if (root == null) {
            return;
        }
        try {
            new ProcessBuilder("cmd", "/c", "gradlew.bat", "--stop")
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .start();
        } catch (Exception ignored) {
        }
    }

    private static void killLingeringProcesses(Path root, long currentPid, boolean watchdogMode, String session) throws IOException, InterruptedException {
        if (!isWindows()) {
            return;
        }

        Set<Long> trackedRoots = new HashSet<>();
        for (Process process : processes) {
            if (process != null) {
                trackedRoots.add(process.pid());
            }
        }
        if (currentPid > 0) {
            trackedRoots.add(currentPid);
        }

        List<Long> pids = new ArrayList<>();
        for (ProcessHandle handle : ProcessHandle.allProcesses().toList()) {
            if (handle == null || !handle.isAlive()) {
                continue;
            }
            long pid = handle.pid();
            if (pid == currentPid) {
                continue;
            }
            String command = handle.info().commandLine().orElse("").toLowerCase(Locale.ROOT);
            String cmd = handle.info().command().orElse("").toLowerCase(Locale.ROOT);
            boolean match = matchesLauncherProcess(command, cmd, root, session);
            if (!match) {
                match = isDescendantOfAny(handle, trackedRoots);
            }
            if (match) {
                pids.add(pid);
            }
        }

        Collections.reverse(pids);
        for (Long pid : pids) {
            try {
                new ProcessBuilder("cmd", "/c", "taskkill", "/PID", Long.toString(pid), "/T", "/F")
                        .redirectErrorStream(true)
                        .start()
                        .waitFor(10, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean isDescendantOfAny(ProcessHandle handle, Set<Long> rootPids) {
        ProcessHandle current = handle;
        int depth = 0;
        while (current != null && depth++ < 16) {
            if (rootPids.contains(current.pid())) {
                return true;
            }
            current = current.parent().orElse(null);
        }
        return false;
    }

    private static boolean matchesLauncherProcess(String commandLine, String command, Path root, String session) {
        String text = (commandLine == null ? "" : commandLine) + " " + (command == null ? "" : command);
        String lower = text.toLowerCase(Locale.ROOT);
        String rootText = root == null ? "" : root.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        String sessionText = session == null ? "" : session.toLowerCase(Locale.ROOT);
        return lower.contains("gradlew.bat")
                || lower.contains(":server:bootrun")
                || lower.contains(":dm-client:run")
                || lower.contains("gradledaemon")
                || lower.contains("org.gradle.launcher.daemon")
                || lower.contains("org.springframework.boot")
                || lower.contains("spring-boot")
                || lower.contains("avalon.launcher.session")
                || (!sessionText.isBlank() && lower.contains(sessionText))
                || lower.contains("dmapplication")
                || lower.contains("mapeditorapplication")
                || lower.contains("serverapplication")
                || lower.contains("npm.cmd")
                || lower.contains("npm ")
                || lower.contains("node ")
                || lower.contains("vite")
                || (!rootText.isBlank() && lower.contains(rootText));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
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