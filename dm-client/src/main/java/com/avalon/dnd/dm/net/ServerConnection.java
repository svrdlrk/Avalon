package com.avalon.dnd.dm.net;

import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.*;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.io.File;
import java.lang.reflect.Type;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class ServerConnection {

    private static final ServerConnection INSTANCE = new ServerConnection();
    public static ServerConnection getInstance() { return INSTANCE; }

    private StompSession stompSession;
    private final ObjectMapper mapper = new ObjectMapper();
    private Consumer<Void> onConnected;

    private ServerConnection() {}

    public void connect(String serverUrl, String sessionId,
                        String playerName, boolean isDm,
                        Consumer<Void> onConnected) {

        disconnect();
        this.onConnected = onConnected;
        String joinNonce = UUID.randomUUID().toString();

        var wsClient = new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))
        );

        var stompClient = new WebSocketStompClient(wsClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());

        stompClient.connectAsync(
                serverUrl + "/ws",
                new StompSessionHandlerAdapter() {

                    @Override
                    public void afterConnected(StompSession session,
                                               StompHeaders connectedHeaders) {
                        stompSession = session;

                        stompSession.subscribe(
                                "/topic/session/" + sessionId,
                                new BroadcastHandler()
                        );

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
                        ex.printStackTrace();
                    }
                }
        );
    }

    public void send(String destination, Object payload) {
        if (stompSession == null || !stompSession.isConnected()) return;

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
    }

    private void subscribePrivateChannel(String sessionId, String playerId) {
        if (stompSession == null || !stompSession.isConnected()) return;
        stompSession.subscribe(
                "/topic/session/" + sessionId + "/private/" + playerId,
                new JoinStateHandler(sessionId, false)
        );
    }

    private class BroadcastHandler extends StompSessionHandlerAdapter {

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            try {
                JavaType type = mapper.getTypeFactory()
                        .constructParametricType(WsMessage.class, Object.class);
                WsMessage<?> msg = mapper.readValue((String) payload, type);
                handleEvent(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Первый кадр после join (notify UI) и последующие SESSION_STATE из /session.sync.
     */
    private class JoinStateHandler extends StompSessionHandlerAdapter {

        private final String sessionId;
        private final boolean completeHandshake;

        JoinStateHandler(String sessionId, boolean completeHandshake) {
            this.sessionId = sessionId;
            this.completeHandshake = completeHandshake;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return String.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            try {
                JavaType stateType = mapper.getTypeFactory()
                        .constructParametricType(WsMessage.class, SessionStateDto.class);
                WsMessage<SessionStateDto> msg =
                        mapper.readValue((String) payload, stateType);

                if (msg.getType() != WsEventType.SESSION_STATE) {
                    return;
                }

                SessionStateDto state = msg.getPayload();
                String myPlayerId = state.getMyPlayerId();

                Platform.runLater(() -> {
                    ClientState.getInstance()
                            .applyState(state, sessionId, myPlayerId);

                    if (completeHandshake) {
                        subscribePrivateChannel(sessionId, myPlayerId);
                        if (onConnected != null) {
                            onConnected.accept(null);
                            onConnected = null;
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handleEvent(WsMessage<?> msg) {
        Platform.runLater(() -> {
            ClientState state = ClientState.getInstance();
            switch (msg.getType()) {
                case TOKEN_MOVED, TOKEN_ADDED, TOKEN_ASSIGNED -> {
                    TokenDto token = mapper.convertValue(
                            msg.getPayload(), TokenDto.class);
                    state.moveToken(token);
                }
                case TOKEN_REMOVED -> {
                    String tokenId = mapper.convertValue(msg.getPayload(), String.class);
                    state.removeToken(tokenId);
                }
                case MAP_OBJECT_ADDED -> {
                    MapObjectDto obj = mapper.convertValue(
                            msg.getPayload(), MapObjectDto.class);
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
                case TOKEN_HP -> {
                    TokenDto t = mapper.convertValue(msg.getPayload(), TokenDto.class);
                    state.moveToken(t);
                }
                default -> {}
            }
        });
    }

    // ==================== МЕТОДЫ ДЛЯ DM ====================

    public void createSession(String sessionName, GridConfig grid) {
        System.out.println("DM → Server: createSession '" + sessionName + "' " + grid.getCols() + "x" + grid.getRows());
        // TODO: Реальная отправка через STOMP
        // sessionTemplate.convertAndSend("/app/session.create", new CreateSessionRequest(sessionName, grid));
    }

    public void uploadMap(File file) {
        System.out.println("DM → Server: uploadMap " + file.getName());
        // TODO: Отправка файла (base64 или multipart)
    }

    public void createToken(String name, int col, int row, int hp, String ownerId) {
        TokenCreateRequest req = new TokenCreateRequest(
                name,
                col,
                row,
                ownerId, // Передаем ID игрока или null для NPC
                hp,
                hp       // maxHp при создании обычно равен текущему HP
        );

        send("/token.create", req);
    }

    public void assignToken(String tokenId, String newOwnerId) {
        TokenAssignRequest req = new TokenAssignRequest();
        req.setTokenId(tokenId);
        req.setOwnerId(newOwnerId); // null сделает токен NPC

        send("/token.assign", req);
    }

    public void updateTokenHp(String tokenId, int newHp, int newMaxHp) {
        TokenHpUpdateEvent event = new TokenHpUpdateEvent();
        event.setTokenId(tokenId);
        event.setHp(newHp);
        event.setMaxHp(newMaxHp); // Обязательно передаем maxHp
        send("/token.hp", event);
    }

    public void revealAllFog() {
        System.out.println("DM → Server: revealAllFog");
        // TODO: Отправка события очистки тумана
    }
}
