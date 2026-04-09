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
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class SessionWsController {

    private final SessionService           sessionService;
    private final SimpMessagingTemplate    messaging;
    private final SessionValidationService validationService;

    /** WS-session-id → game player reference. Used for disconnect handling. */
    private final Map<String, PlayerRef> wsToPlayer = new ConcurrentHashMap<>();

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
                session.getInitiativeState()
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

        GameSession session = sessionService.getSession(request.getSessionId());
        if (session == null) throw new RuntimeException("Session not found");

        Player player = sessionService.joinSession(
                request.getSessionId(), request.getPlayerName(), request.isDm());

        // Map WS session → game player for disconnect cleanup
        wsToPlayer.put(wsSessionId, new PlayerRef(request.getSessionId(), player.getId()));

        // Broadcast PLAYER_JOINED so DM refreshes its player list
        messaging.convertAndSend(
                "/topic/session/" + request.getSessionId(),
                new WsMessage<>(WsEventType.PLAYER_JOINED,
                        request.getSessionId(),
                        session.incrementVersion(),
                        new PlayerDto(player.getId(), player.getName(), player.getRole().name())));

        // Send full state to the joining player
        messaging.convertAndSend(
                joinTopic(request.getSessionId(), request.getJoinNonce()),
                new WsMessage<>(WsEventType.SESSION_STATE,
                        request.getSessionId(),
                        session.getVersion(),
                        buildState(session, player.getId())));
    }

    // ---- sync ----

    @MessageMapping("/session.sync")
    public void sync(@Header("sessionId") String sessionId,
                     @Header("playerId")  String playerId) {
        Player player = validationService.validate(sessionId, playerId);
        GameSession session = sessionService.getSession(sessionId);
        messaging.convertAndSend(
                privateTopic(sessionId, player.getId()),
                new WsMessage<>(WsEventType.SESSION_STATE, sessionId,
                        session.getVersion(), buildState(session, playerId)));
    }

    // ---- disconnect ----

    /**
     * Handles WebSocket disconnection.
     * - Removes the player from the session.
     * - Converts all their tokens to NPC (ownerId = null).
     * - Broadcasts PLAYER_LEFT and TOKEN_ASSIGNED events.
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String wsId = sha.getSessionId();
        if (wsId == null) return;

        PlayerRef ref = wsToPlayer.remove(wsId);
        if (ref == null) return;   // no record — maybe non-game connection

        GameSession session = sessionService.getSession(ref.gameSessionId());
        if (session == null) return;

        Player leaving = session.getPlayers().remove(ref.playerId());
        if (leaving == null) return;

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

    // ---- helpers ----

    private static String joinTopic(String sid, String nonce) {
        return "/topic/session/" + sid + "/join/" + nonce;
    }

    private static String privateTopic(String sid, String playerId) {
        return "/topic/session/" + sid + "/private/" + playerId;
    }
}