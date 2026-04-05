package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.service.SessionValidationService;
import com.avalon.dnd.shared.*;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class SessionWsController {

    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionValidationService validationService;

    public SessionWsController(SessionService sessionService,
                               SimpMessagingTemplate messagingTemplate,
                               SessionValidationService validationService) {
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
        this.validationService = validationService;
    }

    private SessionStateDto buildState(GameSession session, String forPlayerId) {
        return new SessionStateDto(
                forPlayerId,
                session.getGrid(),
                session.getTokens().values().stream()
                        .map(t -> new TokenDto(
                                t.getId(), t.getName(),
                                t.getCol(), t.getRow(),
                                t.getOwnerId(),
                                t.getHp(), t.getMaxHp()))
                        .toList(),
                session.getPlayers().values().stream()
                        .map(p -> new PlayerDto(
                                p.getId(), p.getName(),
                                p.getRole().name()))
                        .toList(),
                session.getObjects().values().stream()
                        .map(o -> new MapObjectDto(
                                o.getId(), o.getType(),
                                o.getCol(), o.getRow(),
                                o.getWidth(), o.getHeight()))
                        .toList()
        );
    }

    @MessageMapping("/session.sync")
    public void sync(@Header("sessionId") String sessionId,
                     @Header("playerId") String playerId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = sessionService.getSession(sessionId);

        messagingTemplate.convertAndSend(
                privateTopic(sessionId, player.getId()),
                new WsMessage<>(WsEventType.SESSION_STATE, sessionId,
                        session.getVersion(), buildState(session, playerId))
        );
    }

    @MessageMapping("/session.join")
    public void join(JoinSessionRequestDto request) {

        if (request.getPlayerName() == null || request.getPlayerName().isBlank()) {
            throw new RuntimeException("Player name is required");
        }
        if (request.getJoinNonce() == null || request.getJoinNonce().isBlank()) {
            throw new RuntimeException("joinNonce is required");
        }

        GameSession session = sessionService.getSession(request.getSessionId());
        if (session == null) throw new RuntimeException("Session not found");

        Player player = sessionService.joinSession(
                request.getSessionId(),
                request.getPlayerName(),
                request.isDm()
        );

        // уведомляем всех о новом игроке
        messagingTemplate.convertAndSend(
                "/topic/session/" + request.getSessionId(),
                new WsMessage<>(
                        WsEventType.PLAYER_JOINED,
                        request.getSessionId(),
                        session.incrementVersion(),
                        new PlayerDto(
                                player.getId(),
                                player.getName(),
                                player.getRole().name()
                        )
                )
        );

        messagingTemplate.convertAndSend(
                joinTopic(request.getSessionId(), request.getJoinNonce()),
                new WsMessage<>(
                        WsEventType.SESSION_STATE,
                        request.getSessionId(),
                        session.getVersion(),
                        buildState(session, player.getId())
                )
        );
    }

    private static String joinTopic(String sessionId, String joinNonce) {
        return "/topic/session/" + sessionId + "/join/" + joinNonce;
    }

    private static String privateTopic(String sessionId, String playerId) {
        return "/topic/session/" + sessionId + "/private/" + playerId;
    }
}