package com.avalon.dnd.dm.net;

import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.*;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import okhttp3.*;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
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
        this.onConnected = onConnected;
        String joinNonce = UUID.randomUUID().toString();
        String normalizedSessionId = normalizeSessionId(sessionId);

        var wsClient    = new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())));
        var stompClient = new WebSocketStompClient(wsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        stompClient.connectAsync(serverUrl + "/ws", new StompSessionHandlerAdapter() {
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
                Platform.runLater(() -> {
                    ClientState.getInstance().applyState(state, sessionId, state.getMyPlayerId());
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
        httpAsync(() -> {
            Request req = new Request.Builder()
                    .url(serverUrl + "/api/session/create")
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
        new Thread(() -> {
            try {
                HttpUrl url = HttpUrl.parse(serverUrl + "/api/session/" + sessionId + "/save")
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
        }).start();
    }

    public void loadSession(String serverUrl, String sessionId, Consumer<String> onDone) {
        httpAsync(() -> {
            Request req = new Request.Builder()
                    .url(serverUrl + "/api/session/" + sessionId + "/load")
                    .post(RequestBody.create(new byte[0])).build();
            try (Response r = httpClient.newCall(req).execute()) {
                if (r.isSuccessful() && r.body() != null)
                    return mapper.readTree(r.body().string()).get("id").asText();
            }
            return null;
        }, onDone);
    }

    public void listSavedSessions(String serverUrl, Consumer<List<JsonNode>> onDone) {
        new Thread(() -> {
            try {
                Request req = new Request.Builder()
                        .url(serverUrl + "/api/session/saved").build();
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
        }).start();
    }

    public void uploadMap(String serverUrl, String sessionId,
                          java.io.File file, Consumer<String> onDone) {
        new Thread(() -> {
            try {
                HttpUrl uploadUrl = HttpUrl.parse(serverUrl + "/api/map/upload")
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
        }).start();
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
        send("/token.create",
                new TokenCreateRequest(name, col, row, ownerId, hp, maxHp, gridSize, imageUrl));
    }

    public void updateTokenHp(String tokenId, int hp, int maxHp) {
        TokenHpUpdateEvent ev = new TokenHpUpdateEvent();
        ev.setTokenId(tokenId); ev.setHp(hp); ev.setMaxHp(maxHp);
        send("/token.hp", ev);
    }

    // ================================================================ Private

    /** Run a blocking call on a background thread, deliver result on FX thread. */
    private <T> void httpAsync(java.util.concurrent.Callable<T> call, Consumer<T> onResult) {
        new Thread(() -> {
            T result = null;
            try { result = call.call(); } catch (Exception e) { e.printStackTrace(); }
            final T r = result;
            Platform.runLater(() -> onResult.accept(r));
        }).start();
    }

    private static MediaType guessMediaType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.parse("image/jpeg");
        if (lower.endsWith(".png")) return MediaType.parse("image/png");
        if (lower.endsWith(".gif")) return MediaType.parse("image/gif");
        if (lower.endsWith(".webp")) return MediaType.parse("image/webp");
        return MediaType.parse("application/octet-stream");
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null) return null;
        String normalized = sessionId.trim();
        int comma = normalized.indexOf(',');
        if (comma >= 0) normalized = normalized.substring(0, comma).trim();
        return normalized;
    }
}