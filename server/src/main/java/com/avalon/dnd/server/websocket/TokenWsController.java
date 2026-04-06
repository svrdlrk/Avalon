package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Token;
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

    public TokenWsController(TokenService tokenService,
                             SessionService sessionService,
                             SimpMessagingTemplate messaging,
                             SessionValidationService validationService) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.messaging = messaging;
        this.validationService = validationService;
    }

    @MessageMapping("/token.move")
    public void moveToken(TokenMoveEvent event,
                          @Header("playerId") String playerId,
                          @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = getSession(sessionId);
        Token updated = tokenService.moveToken(event, player);
        broadcast(sessionId, session, WsEventType.TOKEN_MOVED, toDto(updated));
    }

    @MessageMapping("/token.create")
    public void createToken(TokenCreateRequest request,
                            @Header("playerId") String playerId,
                            @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = getSession(sessionId);
        Token token = tokenService.createToken(request, player);
        broadcast(sessionId, session, WsEventType.TOKEN_ADDED, toDto(token));
    }

    @MessageMapping("/token.remove")
    public void removeToken(TokenRemoveEvent event,
                            @Header("playerId") String playerId,
                            @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = getSession(sessionId);
        String removedId = tokenService.removeToken(event, player);
        broadcast(sessionId, session, WsEventType.TOKEN_REMOVED, removedId);
    }

    @MessageMapping("/token.assign")
    public void assignToken(TokenAssignRequest request,
                            @Header("playerId") String playerId,
                            @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = getSession(sessionId);
        Token updated = tokenService.assignToken(request, player);
        broadcast(sessionId, session, WsEventType.TOKEN_ASSIGNED, toDto(updated));
    }

    @MessageMapping("/token.hp")
    public void updateHp(TokenHpUpdateEvent event,
                         @Header("playerId") String playerId,
                         @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = getSession(sessionId);
        Token updated = tokenService.updateHp(event, player);
        // TOKEN_HP — отдельный тип, не TOKEN_MOVED
        broadcast(sessionId, session, WsEventType.TOKEN_HP, toDto(updated));
    }

    // --- helpers ---

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

    private static TokenDto toDto(Token t) {
        return new TokenDto(
                t.getId(), t.getName(),
                t.getCol(), t.getRow(),
                t.getOwnerId(),
                t.getHp(), t.getMaxHp()
        );
    }
}