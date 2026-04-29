package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.service.MapObjectService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.service.SessionValidationService;
import com.avalon.dnd.server.service.TokenService;
import com.avalon.dnd.shared.*;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Controller
public class SessionWsController {

    private static final long RECONNECT_GRACE_SECONDS = 15L;

    private final SessionService           sessionService;
    private final SimpMessagingTemplate    messaging;
    private final SessionValidationService validationService;
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "session-ws-cleanup");
        t.setDaemon(true);
        return t;
    });

    /** WS-session-id → game player reference. Used for disconnect handling. */
    private final Map<String, PlayerRef> wsToPlayer = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ScheduledFuture<?>> pendingDisconnects = new ConcurrentHashMap<>();

    private record PlayerRef(String gameSessionId, String playerId) {}

    public SessionWsController(SessionService sessionService,
                               SimpMessagingTemplate messaging,
                               SessionValidationService validationService) {
        this.sessionService    = sessionService;
        this.messaging         = messaging;
        this.validationService = validationService;
    }

    // ---- state builder ----

    private SessionStateDto buildState(GameSession session, String forPlayerId) {
        return new SessionStateDto(
                forPlayerId,
                session.getGrid(),
                session.getTokens().values().stream().map(TokenService::toDto).toList(),
                session.getPlayers().values().stream()
                        .map(p -> new PlayerDto(p.getId(), p.getName(), p.getRole().name()))
                        .toList(),
                session.getObjects().values().stream().map(MapObjectService::toDto).toList(),
                session.getBackgroundUrl(),
                session.getInitiativeState(),
                session.getReferenceOverlayLayer(),
                session.getTerrainLayer(),
                session.getWallLayer(),
                session.getFogSettings(),
                session.getMicroLocations(),
                session.getAssetPackIds()
        );
    }

    // ---- join ----

    @MessageMapping("/session.join")
    public void join(JoinSessionRequestDto request,
                     @Header("simpSessionId") String wsSessionId) {

        if (request.getPlayerName() == null || request.getPlayerName().isBlank())
            throw new RuntimeException("Player name required");
        if (request.getJoinNonce() == null || request.getJoinNonce().isBlank())
            throw new RuntimeException("joinNonce required");

        String sessionId = normalizeSessionId(request.getSessionId());
        GameSession session = sessionService.getSession(sessionId);
        if (session == null) throw new RuntimeException("Session not found");

        Player player = sessionService.joinSession(
                request.getSessionId(), request.getPlayerName(), request.isDm());

        cancelPendingDisconnect(sessionId, player.getId());

        // Map WS session → game player for disconnect cleanup
        wsToPlayer.put(wsSessionId, new PlayerRef(sessionId, player.getId()));

        // Broadcast PLAYER_JOINED so DM refreshes its player list
        messaging.convertAndSend(
                "/topic/session/" + sessionId,
                new WsMessage<>(WsEventType.PLAYER_JOINED,
                        sessionId,
                        session.incrementVersion(),
                        new PlayerDto(player.getId(), player.getName(), player.getRole().name())));

        // Send full state to the joining player
        messaging.convertAndSend(
                joinTopic(sessionId, request.getJoinNonce()),
                new WsMessage<>(WsEventType.SESSION_STATE,
                        sessionId,
                        session.getVersion(),
                        buildState(session, player.getId())));
    }

    // ---- sync ----

    @MessageMapping("/session.sync")
    public void sync(@Header("sessionId") String sessionId,
                     @Header("playerId")  String playerId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        Player player = validationService.validate(normalizedSessionId, playerId);
        GameSession session = sessionService.getSession(normalizedSessionId);
        messaging.convertAndSend(
                privateTopic(normalizedSessionId, player.getId()),
                new WsMessage<>(WsEventType.SESSION_STATE, normalizedSessionId,
                        session.getVersion(), buildState(session, playerId)));
    }

    // ---- disconnect ----

    /**
     * Handles WebSocket disconnection.
     * The player remains in the session during a reconnect grace period,
     * and is only removed if they do not come back in time.
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String wsId = sha.getSessionId();
        if (wsId == null) return;

        PlayerRef ref = wsToPlayer.remove(wsId);
        if (ref == null) return;   // no record — maybe non-game connection

        scheduleDisconnectCleanup(ref);
    }

    // ---- helpers ----

    private void scheduleDisconnectCleanup(PlayerRef ref) {
        String key = disconnectKey(ref.gameSessionId(), ref.playerId());
        cancelPendingDisconnect(ref.gameSessionId(), ref.playerId());

        ScheduledFuture<?> future = cleanupExecutor.schedule(() -> cleanupDisconnectedPlayer(ref),
                RECONNECT_GRACE_SECONDS, TimeUnit.SECONDS);
        pendingDisconnects.put(key, future);
    }

    private void cancelPendingDisconnect(String sessionId, String playerId) {
        if (sessionId == null || playerId == null) return;
        String key = disconnectKey(sessionId, playerId);
        ScheduledFuture<?> future = pendingDisconnects.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void cleanupDisconnectedPlayer(PlayerRef ref) {
        String key = disconnectKey(ref.gameSessionId(), ref.playerId());
        pendingDisconnects.remove(key);

        GameSession session = sessionService.getSession(ref.gameSessionId());
        if (session == null) return;

        Player leaving = session.getPlayers().get(ref.playerId());
        if (leaving == null) return;

        // If the player has already reconnected, keep them in session.
        if (wsToPlayer.values().stream().anyMatch(r -> Objects.equals(r.gameSessionId(), ref.gameSessionId())
                && Objects.equals(r.playerId(), ref.playerId()))) {
            return;
        }

        session.getPlayers().remove(ref.playerId());

        // Unassign tokens owned by the leaving player
        List<TokenDto> released = new ArrayList<>();
        session.getTokens().values().forEach(token -> {
            if (ref.playerId().equals(token.getOwnerId())) {
                token.setOwnerId(null);
                released.add(TokenService.toDto(token));
            }
        });

        long version = session.incrementVersion();

        // Notify everyone the player left
        messaging.convertAndSend(
                "/topic/session/" + ref.gameSessionId(),
                new WsMessage<>(WsEventType.PLAYER_LEFT,
                        ref.gameSessionId(), version, ref.playerId()));

        // Notify everyone about unassigned tokens
        for (TokenDto t : released) {
            version = session.incrementVersion();
            messaging.convertAndSend(
                    "/topic/session/" + ref.gameSessionId(),
                    new WsMessage<>(WsEventType.TOKEN_ASSIGNED,
                            ref.gameSessionId(), version, t));
        }
    }

    private static String disconnectKey(String sessionId, String playerId) {
        return sessionId + "::" + playerId;
    }

    private static String joinTopic(String sid, String nonce) {
        return "/topic/session/" + sid + "/join/" + nonce;
    }

    private static String privateTopic(String sid, String playerId) {
        return "/topic/session/" + sid + "/private/" + playerId;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null) return null;
        String normalized = sessionId.trim();
        int comma = normalized.indexOf(',');
        if (comma >= 0) normalized = normalized.substring(0, comma).trim();
        return normalized;
    }
}
