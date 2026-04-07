package com.avalon.dnd.dm.net;

import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.*;
import com.fasterxml.jackson.databind.JavaType;
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
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private ServerConnection() {}

    // ================================================================ STOMP

    public void connect(String serverUrl, String sessionId,
                        String playerName, boolean isDm,
                        Consumer<Void> onConnected) {
        disconnect();
        this.onConnected = onConnected;
        String joinNonce = UUID.randomUUID().toString();

        var wsClient = new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient())));
        var stompClient = new WebSocketStompClient(wsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        stompClient.connectAsync(serverUrl + "/ws", new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                stompSession = session;
                stompSession.subscribe("/topic/session/" + sessionId, new BroadcastHandler());
                stompSession.subscribe(
                        "/topic/session/" + sessionId + "/join/" + joinNonce,
                        new JoinStateHandler(sessionId, true)
                );

                JoinSessionRequestDto req = new JoinSessionRequestDto();
                req.setSessionId(sessionId);
                req.setPlayerName(playerName);
                req.setDm(isDm);
                req.setJoinNonce(joinNonce);
                stompSession.send("/app/session.join", req);
            }

            @Override
            public void handleException(StompSession s, StompCommand cmd,
                                        StompHeaders h, byte[] p, Throwable ex) {
                System.err.println("STOMP error: " + ex.getMessage());
                ex.printStackTrace();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                System.err.println("STOMP transport error: " + exception.getMessage());
            }
        });
    }

    public void send(String destination, Object payload) {
        if (stompSession == null || !stompSession.isConnected()) {
            System.err.println("STOMP not connected, cannot send to " + destination);
            return;
        }
        StompHeaders headers = new StompHeaders();
        headers.setDestination("/app" + destination);
        headers.set("sessionId", ClientState.getInstance().getSessionId());
        headers.set("playerId", ClientState.getInstance().getPlayerId());
        stompSession.send(headers, payload);
    }

    public void disconnect() {
        if (stompSession != null && stompSession.isConnected()) {
            stompSession.disconnect();
        }
        stompSession = null;
    }

    private void subscribePrivateChannel(String sessionId, String playerId) {
        if (stompSession == null || !stompSession.isConnected()) return;
        stompSession.subscribe(
                "/topic/session/" + sessionId + "/private/" + playerId,
                new JoinStateHandler(sessionId, false)
        );
    }

    // ================================================================ Handlers

    private class BroadcastHandler extends StompSessionHandlerAdapter {
        @Override public Type getPayloadType(StompHeaders h) { return String.class; }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            try {
                String json = (String) payload;
                JavaType type = mapper.getTypeFactory()
                        .constructParametricType(WsMessage.class, Object.class);
                WsMessage<?> msg = mapper.readValue(json, type);
                handleEvent(msg);
            } catch (Exception e) {
                System.err.println("Broadcast parse error: " + e.getMessage());
            }
        }
    }

    private class JoinStateHandler extends StompSessionHandlerAdapter {
        private final String sessionId;
        private final boolean completeHandshake;

        JoinStateHandler(String sessionId, boolean completeHandshake) {
            this.sessionId = sessionId;
            this.completeHandshake = completeHandshake;
        }

        @Override public Type getPayloadType(StompHeaders h) { return String.class; }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            try {
                String json = (String) payload;
                JavaType t = mapper.getTypeFactory()
                        .constructParametricType(WsMessage.class, SessionStateDto.class);
                WsMessage<SessionStateDto> msg = mapper.readValue(json, t);

                if (msg.getType() != WsEventType.SESSION_STATE) return;

                SessionStateDto state = msg.getPayload();
                String myPlayerId = state.getMyPlayerId();

                Platform.runLater(() -> {
                    ClientState.getInstance().applyState(state, sessionId, myPlayerId);
                    if (completeHandshake) {
                        subscribePrivateChannel(sessionId, myPlayerId);
                        if (onConnected != null) {
                            onConnected.accept(null);
                            onConnected = null;
                        }
                    }
                });
            } catch (Exception e) {
                System.err.println("JoinState parse error: " + e.getMessage());
            }
        }
    }

    private void handleEvent(WsMessage<?> msg) {
        Platform.runLater(() -> {
            ClientState state = ClientState.getInstance();
            switch (msg.getType()) {
                case TOKEN_MOVED, TOKEN_ADDED, TOKEN_ASSIGNED, TOKEN_HP -> {
                    TokenDto token = mapper.convertValue(msg.getPayload(), TokenDto.class);
                    state.moveToken(token);
                }
                case TOKEN_REMOVED -> {
                    String tokenId = mapper.convertValue(msg.getPayload(), String.class);
                    state.removeToken(tokenId);
                }
                case MAP_OBJECT_ADDED -> {
                    MapObjectDto obj = mapper.convertValue(msg.getPayload(), MapObjectDto.class);
                    state.addObject(obj);
                }
                case MAP_OBJECT_REMOVED -> {
                    String objId = mapper.convertValue(msg.getPayload(), String.class);
                    state.removeObject(objId);
                }
                case MAP_UPDATED -> {
                    MapLayoutUpdateDto layout = mapper.convertValue(
                            msg.getPayload(), MapLayoutUpdateDto.class);
                    state.applyMapLayoutUpdate(layout);
                }
                case MAP_BACKGROUND_UPDATED -> {
                    String url = mapper.convertValue(msg.getPayload(), String.class);
                    state.setBackgroundUrl(url);
                }
                default -> {}
            }
        });
    }

    // ================================================================ HTTP

    public void createSession(String serverUrl, Consumer<String> onSessionCreated) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(serverUrl + "/api/session/create")
                        .post(RequestBody.create(new byte[0]))
                        .build();
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String sessionId = mapper.readTree(response.body().string()).get("id").asText();
                        Platform.runLater(() -> onSessionCreated.accept(sessionId));
                    } else {
                        Platform.runLater(() -> onSessionCreated.accept(null));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> onSessionCreated.accept(null));
            }
        }).start();
    }

    public void uploadMap(String serverUrl, String sessionId,
                          java.io.File file, Consumer<String> onUploaded) {
        new Thread(() -> {
            try {
                RequestBody body = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("sessionId", sessionId)
                        .addFormDataPart("file", file.getName(),
                                RequestBody.create(file, MediaType.parse("image/*")))
                        .build();

                Request request = new Request.Builder()
                        .url(serverUrl + "/api/map/upload")
                        .post(body)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String url = response.body().string();
                        Platform.runLater(() -> { if (onUploaded != null) onUploaded.accept(url); });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void uploadMap(String serverUrl, String sessionId, java.io.File file) {
        uploadMap(serverUrl, sessionId, file, null);
    }

    // ================================================================ Высокоуровневые методы

    /** Создать токен с поддержкой gridSize и imageUrl. */
    public void createToken(String name, int col, int row,
                            int hp, int maxHp,
                            int gridSize, String imageUrl,
                            String ownerId) {
        TokenCreateRequest req = new TokenCreateRequest(
                name, col, row, ownerId, hp, maxHp, gridSize, imageUrl);
        send("/token.create", req);
    }

    /** Устаревшая перегрузка для обратной совместимости. */
    public void createToken(String name, int col, int row, int hp, String ownerId) {
        createToken(name, col, row, hp, hp, 1, null, ownerId);
    }

    public void assignToken(String tokenId, String newOwnerId) {
        TokenAssignRequest req = new TokenAssignRequest();
        req.setTokenId(tokenId);
        req.setOwnerId(newOwnerId);
        send("/token.assign", req);
    }

    public void updateTokenHp(String tokenId, int newHp, int newMaxHp) {
        TokenHpUpdateEvent event = new TokenHpUpdateEvent();
        event.setTokenId(tokenId);
        event.setHp(newHp);
        event.setMaxHp(newMaxHp);
        send("/token.hp", event);
    }
}