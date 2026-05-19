package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.server.service.MapBattleRulesService;
import com.avalon.dnd.server.service.SessionValidationService;
import com.avalon.dnd.server.service.TokenService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.shared.*;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class TokenWsController {

    private final TokenService tokenService;
    private final SessionService sessionService;
    private final SimpMessagingTemplate messaging;
    private final SessionValidationService validationService;
    private final SessionWsController sessionWsController;
    private final MapBattleRulesService battleRulesService;

    public TokenWsController(TokenService tokenService,
                             SessionService sessionService,
                             SimpMessagingTemplate messaging,
                             SessionValidationService validationService,
                             SessionWsController sessionWsController,
                             MapBattleRulesService battleRulesService) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.messaging = messaging;
        this.validationService = validationService;
        this.sessionWsController = sessionWsController;
        this.battleRulesService = battleRulesService;
    }

    @MessageMapping("/token.move")
    public void moveToken(TokenMoveEvent event,
                          @Header(value = "playerId", required = false) String playerId,
                          @Header("sessionId") String sessionId) {
        Player player = validationService.validate(sessionId, playerId);
        GameSession session = getSession(sessionId);
        Token updated = tokenService.moveToken(event, player);
        battleRulesService.computeVisibility(session);
        broadcast(sessionId, session, WsEventType.TOKEN_MOVED, TokenService.toDto(updated));
        sessionWsController.broadcastSessionState(session);
    }

    @MessageMapping("/token.create")
    public void createToken(TokenCreateRequest request,
                            @Header(value = "playerId", required = false) String playerId,
                            @Header("sessionId") String sessionId) {
        Player player = validationService.validate(sessionId, playerId);
        GameSession session = getSession(sessionId);
        Token token = tokenService.createToken(request, player);
        battleRulesService.computeVisibility(session);
        broadcast(sessionId, session, WsEventType.TOKEN_ADDED, TokenService.toDto(token));
        sessionWsController.broadcastSessionState(session);
    }

    @MessageMapping("/token.remove")
    public void removeToken(TokenRemoveEvent event,
                            @Header(value = "playerId", required = false) String playerId,
                            @Header("sessionId") String sessionId) {
        Player player = validationService.validate(sessionId, playerId);
        GameSession session = getSession(sessionId);
        String removedId = tokenService.removeToken(event, player);
        battleRulesService.computeVisibility(session);
        broadcast(sessionId, session, WsEventType.TOKEN_REMOVED, removedId);
        sessionWsController.broadcastSessionState(session);
    }

    @MessageMapping("/token.assign")
    public void assignToken(TokenAssignRequest request,
                            @Header(value = "playerId", required = false) String playerId,
                            @Header("sessionId") String sessionId) {
        GameSession session = getSession(sessionId);
        Player player = resolveActor(session, sessionId, playerId);
        Token updated = tokenService.assignToken(request, player);
        battleRulesService.computeVisibility(session);
        broadcast(sessionId, session, WsEventType.TOKEN_ASSIGNED, TokenService.toDto(updated));
        sessionWsController.broadcastSessionState(session);
    }

    @MessageMapping("/token.hp")
    public void updateHp(TokenHpUpdateEvent event,
                         @Header(value = "playerId", required = false) String playerId,
                         @Header("sessionId") String sessionId) {
        Player player = validationService.validate(sessionId, playerId);
        GameSession session = getSession(sessionId);
        Token updated = tokenService.updateHp(event, player);
        battleRulesService.computeVisibility(session);
        broadcast(sessionId, session, WsEventType.TOKEN_HP, TokenService.toDto(updated));
        sessionWsController.broadcastSessionState(session);
    }

    private Player resolveActor(GameSession session, String sessionId, String playerId) {
        if (playerId != null && !playerId.isBlank()) {
            return validationService.validate(sessionId, playerId);
        }

        return session.getPlayers().values().stream()
                .filter(player -> player.getRole() == com.avalon.dnd.server.model.Role.DM)
                .findFirst()
                .orElseThrow(() -> new RuntimeException("DM not found for session: " + sessionId));
    }

    private GameSession getSession(String sessionId) {
        GameSession s = sessionService.getSession(sessionId);
        if (s == null) throw new RuntimeException("Session not found: " + sessionId);
        return s;
    }

    private <T> void broadcast(String sessionId, GameSession session,
                               WsEventType type, T payload) {
        long version = session.incrementVersion();
        messaging.convertAndSend(
                "/topic/session/" + sessionId,
                new WsMessage<>(type, sessionId, version, payload)
        );
    }
}