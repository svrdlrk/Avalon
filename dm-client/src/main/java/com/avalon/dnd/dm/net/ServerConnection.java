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

    public static ServerConnection getInstance() {
        return INSTANCE;
    }

    private StompSession stompSession;
    private final ObjectMapper mapper = new ObjectMapper();
    private Consumer<Void> onConnected;

    // FIX: убрана зависимость tyrus-standalone-client, используем OkHttp только для HTTP
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();

    private ServerConnection() {
    }

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

                JoinSessionRequestDto joinRequest = new JoinSessionRequestDto();
                joinRequest.setSessionId(sessionId);
                joinRequest.setPlayerName(playerName);
                joinRequest.setDm(isDm);
                joinRequest.setJoinNonce(joinNonce);

                stompSession.send("/app/session.join", joinRequest);
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

    // ==================== BROADCAST HANDLER ====================

    private class BroadcastHandler extends StompSessionHandlerAdapter {

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return WsMessage.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            WsMessage<?> msg = (WsMessage<?>) payload;
            handleEvent(msg);
        }
    }

    // ==================== JOIN / PRIVATE HANDLER ====================

    private class JoinStateHandler extends StompSessionHandlerAdapter {

        private final String sessionId;
        private final boolean completeHandshake;

        JoinStateHandler(String sessionId, boolean completeHandshake) {
            this.sessionId = sessionId;
            this.completeHandshake = completeHandshake;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return WsMessage.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            WsMessage<?> raw = (WsMessage<?>) payload;

            if (raw.getType() != WsEventType.SESSION_STATE) {
                return;
            }

            SessionStateDto state = mapper.convertValue(raw.getPayload(), SessionStateDto.class);
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
        }
    }

    // ==================== EVENT DISPATCH ====================

    private void handleEvent(WsMessage<?> msg) {
        Platform.runLater(() -> {
            ClientState state = ClientState.getInstance();
            switch (msg.getType()) {
                case TOKEN_MOVED, TOKEN_ADDED, TOKEN_ASSIGNED -> {
                    TokenDto token = mapper.convertValue(msg.getPayload(), TokenDto.class);
                    state.moveToken(token);
                }
                case TOKEN_HP -> {
                    TokenDto t = mapper.convertValue(msg.getPayload(), TokenDto.class);
                    state.moveToken(t);
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
                default -> {
                    // SESSION_STATE, PLAYER_JOINED — обрабатываются в специализированных хендлерах
                }
            }
        });
    }

    // ==================== HTTP МЕТОДЫ ДЛЯ DM ====================

    /**
     * Создаёт новую сессию на сервере и вызывает callback с ID.
     * FIX: добавлен Consumer<String> callback вместо простого println.
     */
    public void createSession(String serverUrl, Consumer<String> onSessionCreated) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(serverUrl + "/api/session/create")
                        .post(RequestBody.create(new byte[0]))
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String body = response.body().string();
                        String sessionId = mapper.readTree(body).get("id").asText();
                        System.out.println("✅ Сессия создана: " + sessionId);
                        Platform.runLater(() -> onSessionCreated.accept(sessionId));
                    } else {
                        System.err.println("❌ Ошибка создания сессии: " + response.code());
                        Platform.runLater(() -> onSessionCreated.accept(null));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> onSessionCreated.accept(null));
            }
        }).start();
    }

    /**
     * Загружает файл карты на сервер и вызывает callback с URL.
     * FIX: добавлен Consumer<String> callback — URL фона передаётся в UI.
     */
    public void uploadMap(String serverUrl, String sessionId,
                          java.io.File file, Consumer<String> onUploaded) {
        new Thread(() -> {
            try {
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("sessionId", sessionId)
                        .addFormDataPart("file", file.getName(),
                                RequestBody.create(file, MediaType.parse("image/*")))
                        .build();

                Request request = new Request.Builder()
                        .url(serverUrl + "/api/map/upload")
                        .post(requestBody)
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String url = response.body().string();
                        System.out.println("✅ Карта загружена: " + url);
                        Platform.runLater(() -> {
                            if (onUploaded != null) onUploaded.accept(url);
                        });
                    } else {
                        System.err.println("❌ Ошибка загрузки карты: " + response.code());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /** Перегрузка без callback для обратной совместимости. */
    public void uploadMap(String serverUrl, String sessionId, java.io.File file) {
        uploadMap(serverUrl, sessionId, file, null);
    }

    public void createToken(String name, int col, int row, int hp, String ownerId) {
        TokenCreateRequest req = new TokenCreateRequest(name, col, row, ownerId, hp, hp);
        send("/token.create", req);
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

    /**
     * FIX: revealAllFog убран — сервер не поддерживает /map.fog.clear.
     * Нужно добавить WsEventType.FOG_CLEARED и обработчик на сервере,
     * только после этого метод имеет смысл.
     */
}