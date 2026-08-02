package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.server.service.SessionValidationService;
import com.avalon.dnd.server.service.TokenService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.shared.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class TokenWsController {

    private static final Logger log = LoggerFactory.getLogger(TokenWsController.class);

    private final TokenService tokenService;
    private final SessionService sessionService;
    private final SimpMessagingTemplate messaging;
    private final SessionValidationService validationService;
    private final SessionWsController sessionWsController;

    public TokenWsController(TokenService tokenService,
                             SessionService sessionService,
                             SimpMessagingTemplate messaging,
                             SessionValidationService validationService,
                             SessionWsController sessionWsController) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.messaging = messaging;
        this.validationService = validationService;
        this.sessionWsController = sessionWsController;
    }

    @MessageMapping("/token.move")
    public void moveToken(TokenMoveEvent event,
                              @Header(value = "simpSessionId", required = false) String wsSessionId,
                          @Header("sessionId") String sessionId) {
        Player player = validationService.validateBound(sessionId, wsSessionId);
        GameSession session = getSession(sessionId);
        try {
            Token updated = tokenService.moveToken(event, player);
            broadcast(sessionId, session, WsEventType.TOKEN_MOVED, TokenService.toDto(updated));
        } catch (RuntimeException e) {
            log.debug("[token.move] move rejected for token {} by player {}: {}",
                    event.getTokenId(), player.getId(), e.getMessage());
            messaging.convertAndSend(
                    "/topic/session/" + sessionId + "/private/" + player.getId(),
                    new WsMessage<>(WsEventType.COMMAND_REJECTED, sessionId, session.getVersion(), e.getMessage()));
        }
        sessionWsController.broadcastSessionState(session);
    }

    @MessageMapping("/token.create")
    public void createToken(TokenCreateRequest request,
                             @Header(value = "simpSessionId", required = false) String wsSessionId,
                            @Header("sessionId") String sessionId) {
        Player player = validationService.validateBound(sessionId, wsSessionId);
        GameSession session = getSession(sessionId);
        Token token = tokenService.createToken(request, player);
        broadcast(sessionId, session, WsEventType.TOKEN_ADDED, TokenService.toDto(token));
        sessionWsController.broadcastSessionState(session);
    }

    @MessageMapping("/token.remove")
    public void removeToken(TokenRemoveEvent event,
                             @Header(value = "simpSessionId", required = false) String wsSessionId,
                            @Header("sessionId") String sessionId) {
        Player player = validationService.validateBound(sessionId, wsSessionId);
        GameSession session = getSession(sessionId);
        String removedId = tokenService.removeToken(event, player);
        broadcast(sessionId, session, WsEventType.TOKEN_REMOVED, removedId);
        sessionWsController.broadcastSessionState(session);
    }

    @MessageMapping("/token.assign")
    public void assignToken(TokenAssignRequest request,
                             @Header(value = "simpSessionId", required = false) String wsSessionId,
                            @Header("sessionId") String sessionId) {
        GameSession session = getSession(sessionId);
        Player player = validationService.validateBound(sessionId, wsSessionId);
        Token updated = tokenService.assignToken(request, player);
        broadcast(sessionId, session, WsEventType.TOKEN_ASSIGNED, TokenService.toDto(updated));
        sessionWsController.broadcastSessionState(session);
    }

    @MessageMapping("/token.hp")
    public void updateHp(TokenHpUpdateEvent event,
                            @Header(value = "simpSessionId", required = false) String wsSessionId,
                         @Header("sessionId") String sessionId) {
        Player player = validationService.validateBound(sessionId, wsSessionId);
        GameSession session = getSession(sessionId);
        Token updated = tokenService.updateHp(event, player);
        broadcast(sessionId, session, WsEventType.TOKEN_HP, TokenService.toDto(updated));
        sessionWsController.broadcastSessionState(session);
    }

    private GameSession getSession(String sessionId) {
        GameSession s = sessionService.getSession(sessionId);
        if (s == null) throw new RuntimeException("Session not found: " + sessionId);
        return s;
    }

    private <T> void broadcast(String sessionId, GameSession session,
                               WsEventType type, T payload) {
        // Full state is sent through per-connection private topics immediately
        // afterwards. Never publish token coordinates on a guessable public topic.
        session.incrementVersion();
    }
}
