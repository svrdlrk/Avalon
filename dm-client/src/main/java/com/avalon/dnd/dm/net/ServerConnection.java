package com.avalon.dnd.dm.net;

import com.avalon.dnd.dm.config.RuntimeConfig;
import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.*;
import com.avalon.dnd.shared.uploads.AssetCatalogSupport;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.application.Platform;
import okhttp3.*;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ServerConnection {

    private static final ServerConnection INSTANCE = new ServerConnection();
    public static ServerConnection getInstance() { return INSTANCE; }

    private StompSession stompSession;
    private final ObjectMapper mapper = new ObjectMapper();
    private Consumer<Void> onConnected;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private ServerConnection() {}

    // ================================================================ STOMP

    public void connect(String serverUrl, String sessionId,
                        String playerName, boolean isDm,
                        Consumer<Void> onConnected) {
        disconnect();
        ClientState.getInstance().resetVersion();
        this.onConnected = onConnected;
        String joinNonce = UUID.randomUUID().toString();
        String normalizedSessionId = normalizeSessionId(sessionId);
        String normalizedServerUrl = normalizeServerUrl(serverUrl);

        var wsClient    = new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())));
        var stompClient = new WebSocketStompClient(wsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        stompClient.connectAsync(normalizedServerUrl + "/ws", new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders headers) {
                stompSession = session;
                stompSession.subscribe("/topic/session/" + normalizedSessionId, new BroadcastHandler());
                stompSession.subscribe(
                        "/topic/session/" + normalizedSessionId + "/join/" + joinNonce,
                        new JoinStateHandler(normalizedSessionId, true));

                JoinSessionRequestDto req = new JoinSessionRequestDto();
                req.setSessionId(normalizedSessionId);
                req.setPlayerName(playerName);
                req.setDm(isDm);
                req.setJoinNonce(joinNonce);
                stompSession.send("/app/session.join", req);
            }

            @Override
            public void handleException(StompSession s, StompCommand cmd,
                                        StompHeaders h, byte[] p, Throwable ex) {
                System.err.println("STOMP error: " + ex.getMessage());
            }

            @Override
            public void handleTransportError(StompSession s, Throwable ex) {
                System.err.println("STOMP transport error: " + ex.getMessage());
            }
        });
    }

    public void send(String destination, Object payload) {
        if (stompSession == null || !stompSession.isConnected()) {
            System.err.println("Not connected, dropping: " + destination);
            return;
        }
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app" + destination);
        headers.set("sessionId", normalizeSessionId(ClientState.getInstance().getSessionId()));
        headers.set("playerId",  ClientState.getInstance().getPlayerId());
        stompSession.send(headers, payload);
    }

    public void disconnect() {
        if (stompSession != null && stompSession.isConnected()) stompSession.disconnect();
        stompSession = null;
    }

    private void subscribePrivateChannel(String sessionId, String playerId) {
        if (stompSession == null || !stompSession.isConnected()) return;
        stompSession.subscribe(
                "/topic/session/" + sessionId + "/private/" + playerId,
                new JoinStateHandler(sessionId, false));
    }


    private String normalizeServerUrl(String serverUrl) {
        return RuntimeConfig.normalize(serverUrl);
    }

    // ================================================================ Handlers

    private class BroadcastHandler extends StompSessionHandlerAdapter {
        @Override public Type getPayloadType(StompHeaders h) { return Object.class; }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            try {
                JavaType type = mapper.getTypeFactory()
                        .constructParametricType(WsMessage.class, Object.class);
                WsMessage<?> msg;
                if (payload instanceof byte[] b) msg = mapper.readValue(b, type);
                else if (payload instanceof String s) msg = mapper.readValue(s, type);
                else msg = mapper.convertValue(payload, type);
                handleEvent(msg);
            } catch (Exception e) {
                System.err.println("Broadcast parse error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private class JoinStateHandler extends StompSessionHandlerAdapter {
        private final String  sessionId;
        private final boolean completeHandshake;

        JoinStateHandler(String sessionId, boolean completeHandshake) {
            this.sessionId = sessionId;
            this.completeHandshake = completeHandshake;
        }

        @Override public Type getPayloadType(StompHeaders h) { return Object.class; }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            try {
                JavaType type = mapper.getTypeFactory()
                        .constructParametricType(WsMessage.class, SessionStateDto.class);
                WsMessage<SessionStateDto> msg;
                if (payload instanceof byte[] b) msg = mapper.readValue(b, type);
                else if (payload instanceof String s) msg = mapper.readValue(s, type);
                else msg = mapper.convertValue(payload, type);

                SessionStateDto state = msg.getPayload();
                long version = msg.getVersion();
                Platform.runLater(() -> {
                    ClientState clientState = ClientState.getInstance();
                    if (!clientState.shouldApplyVersion(version)) {
                        return;
                    }
                    clientState.applyState(state, sessionId, state.getMyPlayerId());
                    if (completeHandshake) {
                        subscribePrivateChannel(sessionId, state.getMyPlayerId());
                        if (onConnected != null) { onConnected.accept(null); onConnected = null; }
                    }
                });
            } catch (Exception e) {
                System.err.println("JoinState parse error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void handleEvent(WsMessage<?> msg) {
        Platform.runLater(() -> {
            ClientState state = ClientState.getInstance();
            if (!state.shouldApplyVersion(msg.getVersion())) {
                return;
            }
            switch (msg.getType()) {
                case TOKEN_MOVED, TOKEN_ADDED, TOKEN_ASSIGNED, TOKEN_HP -> {
                    TokenDto t = mapper.convertValue(msg.getPayload(), TokenDto.class);
                    state.moveToken(t);
                }
                case TOKEN_REMOVED -> state.removeToken(
                        mapper.convertValue(msg.getPayload(), String.class));
                case MAP_OBJECT_ADDED -> state.addObject(
                        mapper.convertValue(msg.getPayload(), MapObjectDto.class));
                case MAP_OBJECT_REMOVED -> state.removeObject(
                        mapper.convertValue(msg.getPayload(), String.class));
                case MAP_UPDATED -> state.applyMapLayoutUpdate(
                        mapper.convertValue(msg.getPayload(), MapLayoutUpdateDto.class));
                case MAP_BACKGROUND_UPDATED -> state.setBackgroundUrl(
                        mapper.convertValue(msg.getPayload(), String.class));
                case PLAYER_JOINED -> state.addPlayer(
                        mapper.convertValue(msg.getPayload(), PlayerDto.class));
                case PLAYER_LEFT -> state.removePlayer(
                        mapper.convertValue(msg.getPayload(), String.class));
                default -> {}
            }
        });
    }

    // ================================================================ HTTP helpers

    public void createSession(String serverUrl, Consumer<String> onDone) {
        String baseUrl = normalizeServerUrl(serverUrl);
        httpAsync(() -> {
            Request req = new Request.Builder()
                    .url(baseUrl + "/api/session/create")
                    .post(RequestBody.create(new byte[0])).build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null)
                    return mapper.readTree(resp.body().string()).get("id").asText();
            }
            return null;
        }, onDone);
    }

    public void saveSession(String serverUrl, String sessionId,
                            String name, Consumer<Boolean> onDone) {
        String baseUrl = normalizeServerUrl(serverUrl);
        runAsync("dm-save-session", () -> {
            try {
                HttpUrl url = HttpUrl.parse(baseUrl + "/api/session/" + sessionId + "/save")
                        .newBuilder().addQueryParameter("name", name).build();
                try (Response r = httpClient.newCall(
                        new Request.Builder().url(url)
                                .post(RequestBody.create(new byte[0])).build()).execute()) {
                    Platform.runLater(() -> onDone.accept(r.isSuccessful()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> onDone.accept(false));
            }
        });
    }

    public void deleteSavedSession(String serverUrl, String sessionId, Consumer<Boolean> onDone) {
        String baseUrl = normalizeServerUrl(serverUrl);
        runAsync("dm-delete-saved-session", () -> {
            try {
                Request req = new Request.Builder()
                        .url(baseUrl + "/api/session/" + sessionId + "/saved")
                        .delete()
                        .build();
                try (Response r = httpClient.newCall(req).execute()) {
                    Platform.runLater(() -> onDone.accept(r.isSuccessful()));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> onDone.accept(false));
            }
        });
    }

    public void loadSession(String serverUrl, String sessionId, Consumer<String> onDone) {
        String baseUrl = normalizeServerUrl(serverUrl);
        httpAsync(() -> {
            Request req = new Request.Builder()
                    .url(baseUrl + "/api/session/" + sessionId + "/load")
                    .post(RequestBody.create(new byte[0])).build();
            try (Response r = httpClient.newCall(req).execute()) {
                if (r.isSuccessful() && r.body() != null)
                    return mapper.readTree(r.body().string()).get("id").asText();
            }
            return null;
        }, onDone);
    }

    public void importMapWorkspace(String serverUrl, String sessionId, Path workspaceRoot, Consumer<Boolean> onDone) {
        String baseUrl = normalizeServerUrl(serverUrl);
        String normalizedSessionId = normalizeSessionId(sessionId);
        runAsync("dm-worker", () -> {
            try {
                if (workspaceRoot == null || !Files.isDirectory(workspaceRoot)) {
                    Platform.runLater(() -> onDone.accept(false));
                    return;
                }

                Path mapFile = workspaceRoot.resolve("map.json");
                Path microLocationsFile = workspaceRoot.resolve("microlocations.json");
                Path legacyMicroLocationsFile = workspaceRoot.resolve("microLocations.json");
                if (!Files.exists(mapFile)) {
                    Platform.runLater(() -> onDone.accept(false));
                    return;
                }

                JsonNode root = mapper.readTree(mapFile.toFile());
                ObjectNode payload = root != null && root.isObject()
                        ? ((ObjectNode) root).deepCopy()
                        : mapper.createObjectNode();

                Path microSource = Files.exists(microLocationsFile) ? microLocationsFile : legacyMicroLocationsFile;
                if (Files.exists(microSource)) {
                    payload.set("microLocations", mapper.readTree(microSource.toFile()));
                }

                normalizeImportedAssetUrls(payload, workspaceRoot);

                RequestBody body = RequestBody.create(
                        mapper.writeValueAsBytes(payload),
                        MediaType.parse("application/json")
                );
                Request req = new Request.Builder()
                        .url(baseUrl + "/api/session/" + normalizedSessionId + "/import-map")
                        .post(body)
                        .build();
                try (Response r = httpClient.newCall(req).execute()) {
                    boolean ok = r.isSuccessful();
                    Platform.runLater(() -> onDone.accept(ok));
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> onDone.accept(false));
            }
        });
    }

    private void normalizeImportedAssetUrls(com.fasterxml.jackson.databind.JsonNode node, Path workspaceRoot) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            var fields = obj.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (value != null && value.isTextual() && isAssetUrlKey(key)) {
                    String normalized = normalizeImportedAssetUrl(value.asText(), workspaceRoot);
                    if (normalized != null && !normalized.equals(value.asText())) {
                        obj.put(key, normalized);
                    }
                } else {
                    normalizeImportedAssetUrls(value, workspaceRoot);
                }
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                normalizeImportedAssetUrls(item, workspaceRoot);
            }
        }
    }

    private boolean isAssetUrlKey(String key) {
        if (key == null) {
            return false;
        }
        return switch (key) {
            case "imageUrl", "imagePath", "image", "path", "file", "src", "url", "assetPath", "sprite", "thumbnail" -> true;
            default -> false;
        };
    }

    private String normalizeImportedAssetUrl(String raw, Path workspaceRoot) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim().replace('\\', '/');
        if (cleaned.isBlank()) {
            return cleaned;
        }

        if (cleaned.startsWith("http://") || cleaned.startsWith("https://")
                || cleaned.startsWith("file:") || cleaned.startsWith("data:") || cleaned.startsWith("jar:")) {
            return cleaned;
        }

        if (cleaned.startsWith("/uploads/") || cleaned.startsWith("uploads/")
                || cleaned.startsWith("/assets/") || cleaned.startsWith("assets/")) {
            return cleaned.startsWith("/") ? cleaned : "/" + cleaned;
        }

        for (Path root : resolveProjectRoots(workspaceRoot)) {
            Path[] candidates = new Path[] {
                    root.resolve(cleaned),
                    root.resolve("uploads").resolve(cleaned),
                    root.resolve("uploads/assets").resolve(cleaned),
                    root.resolve("uploads/maps/finished").resolve(cleaned),
                    root.resolve("uploads/maps/backups").resolve(cleaned),
                    root.resolve("uploads/maps/reference").resolve(cleaned)
            };
            for (Path candidate : candidates) {
                try {
                    if (Files.exists(candidate)) {
                        String candidateText = candidate.toAbsolutePath().normalize().toString().replace('\\', '/');
                        int uploadsIdx = candidateText.toLowerCase(java.util.Locale.ROOT).indexOf("/uploads/");
                        if (uploadsIdx >= 0) {
                            return candidateText.substring(uploadsIdx);
                        }
                        return candidate.toUri().toString();
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (cleaned.indexOf('/') < 0 && cleaned.indexOf('\\') < 0) {
            return "/uploads/assets/" + cleaned;
        }

        return cleaned;
    }

    private java.util.List<Path> resolveProjectRoots(Path workspaceRoot) {
        java.util.LinkedHashSet<Path> roots = new java.util.LinkedHashSet<>();
        addProjectRoot(roots, workspaceRoot);
        Path current = workspaceRoot;
        while (current != null) {
            addProjectRoot(roots, current);
            current = current.getParent();
        }
        return new java.util.ArrayList<>(roots);
    }

    private void addProjectRoot(java.util.Set<Path> roots, Path candidate) {
        if (candidate == null) return;
        try {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalized.resolve("gradlew.bat"))
                    || Files.exists(normalized.resolve("settings.gradle"))
                    || Files.exists(normalized.resolve("settings.gradle.kts"))
                    || Files.exists(normalized.resolve("build.gradle"))
                    || Files.exists(normalized.resolve("uploads"))) {
                roots.add(normalized);
            }
        } catch (Exception ignored) {
        }
    }

    public void listSavedSessions(String serverUrl, Consumer<List<JsonNode>> onDone) {
        String baseUrl = normalizeServerUrl(serverUrl);
        runAsync("dm-worker", () -> {
            try {
                Request req = new Request.Builder()
                        .url(baseUrl + "/api/session/saved").build();
                try (Response r = httpClient.newCall(req).execute()) {
                    if (r.isSuccessful() && r.body() != null) {
                        List<JsonNode> list = new java.util.ArrayList<>();
                        mapper.readTree(r.body().string()).forEach(list::add);
                        Platform.runLater(() -> onDone.accept(list));
                        return;
                    }
                }
            } catch (Exception e) { e.printStackTrace(); }
            Platform.runLater(() -> onDone.accept(List.of()));
        });
    }

    public void uploadMap(String serverUrl, String sessionId,
                          java.io.File file, Consumer<String> onDone) {
        String baseUrl = normalizeServerUrl(serverUrl);
        runAsync("dm-worker", () -> {
            try {
                HttpUrl uploadUrl = HttpUrl.parse(baseUrl + "/api/map/upload")
                        .newBuilder()
                        .addPathSegment(normalizeSessionId(sessionId))
                        .build();
                RequestBody body = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", file.getName(),
                                RequestBody.create(file, MediaType.parse("image/*")))
                        .build();
                try (Response r = httpClient.newCall(
                        new Request.Builder().url(uploadUrl)
                                .post(body).build()).execute()) {
                    if (r.isSuccessful() && r.body() != null) {
                        String responseUrl = r.body().string().trim();
                        System.out.println("[upload] url: " + responseUrl);
                        Platform.runLater(() -> { if (onDone != null) onDone.accept(responseUrl); });
                        return;
                    }
                    System.err.println("[upload] failed: " + r.code());
                }
            } catch (Exception e) { e.printStackTrace(); }
            Platform.runLater(() -> { if (onDone != null) onDone.accept(null); });
        });
    }

    // ================================================================ Initiative

    public void publishInitiative(List<InitiativeStateDto.InitiativeEntry> entries, int idx) {
        InitiativeUpdateRequest req = new InitiativeUpdateRequest();
        req.setEntries(entries);
        req.setCurrentIndex(idx);
        send("/initiative.update", req);
    }

    public void clearInitiative() {
        send("/initiative.clear", new java.util.HashMap<>());
    }

    // ================================================================ Token helpers

    public void createToken(String name, int col, int row,
                            int hp, int maxHp, int gridSize,
                            String imageUrl, String ownerId) {
        createToken(name, col, row, hp, maxHp, gridSize, imageUrl, ownerId, 0, 0);
    }

    public void createToken(String name, int col, int row,
                            int hp, int maxHp, int gridSize,
                            String imageUrl, String ownerId,
                            int dayVision, int nightVision) {
        send("/token.create",
                new TokenCreateRequest(name, col, row, ownerId, hp, maxHp, gridSize, imageUrl, dayVision, nightVision));
    }

    public void updateTokenHp(String tokenId, int hp, int maxHp) {
        TokenHpUpdateEvent ev = new TokenHpUpdateEvent();
        ev.setTokenId(tokenId); ev.setHp(hp); ev.setMaxHp(maxHp);
        send("/token.hp", ev);
    }

    // ================================================================ Private

    private void runAsync(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    /** Run a blocking call on a background thread, deliver result on FX thread. */
    private <T> void httpAsync(java.util.concurrent.Callable<T> call, Consumer<T> onResult) {
        runAsync("dm-http", () -> {
            T result = null;
            try { result = call.call(); } catch (Exception e) { e.printStackTrace(); }
            final T r = result;
            Platform.runLater(() -> onResult.accept(r));
        });
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null) return null;
        String normalized = sessionId.trim();
        int comma = normalized.indexOf(',');
        if (comma >= 0) normalized = normalized.substring(0, comma).trim();
        return normalized;
    }
}