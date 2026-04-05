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
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionValidationService validationService;

    public TokenWsController(TokenService tokenService,
                             SessionService sessionService,
                             SimpMessagingTemplate messagingTemplate,
                             SessionValidationService validationService) {
        this.tokenService = tokenService;
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
        this.validationService = validationService;
    }

    @MessageMapping("/token.move")
    public void moveToken(TokenMoveEvent event,
                          @Header("playerId") String playerId,
                          @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = sessionService.getSession(sessionId);

        Token updated = tokenService.moveToken(event, player);
        long version = session.incrementVersion();
        WsMessage<TokenDto> message = new WsMessage<>(
                WsEventType.TOKEN_MOVED,
                sessionId,
                version,
                toDto(updated)
        );

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                message
        );
    }

    @MessageMapping("/token.create")
    public void createToken(TokenCreateRequest request,
                            @Header("playerId") String playerId,
                            @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = sessionService.getSession(sessionId);

        Token token = tokenService.createToken(request, player);
        long version = session.incrementVersion();
        WsMessage<TokenDto> message = new WsMessage<>(
                WsEventType.TOKEN_ADDED,
                sessionId,
                version,
                toDto(token)
        );

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                message
        );
    }

    @MessageMapping("/token.remove")
    public void removeToken(TokenRemoveEvent event,
                            @Header("playerId") String playerId,
                            @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = sessionService.getSession(sessionId);

        String removedId = tokenService.removeToken(event, player);
        long version = session.incrementVersion();
        WsMessage<String> message = new WsMessage<>(
                WsEventType.TOKEN_REMOVED,
                sessionId,
                version,
                removedId
        );

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                message
        );
    }

    @MessageMapping("/token.assign")
    public void assignToken(TokenAssignRequest request,
                            @Header("playerId") String playerId,
                            @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = sessionService.getSession(sessionId);

        Token updated = tokenService.assignToken(request, player);
        long version = session.incrementVersion();
        WsMessage<TokenDto> message = new WsMessage<>(
                WsEventType.TOKEN_ASSIGNED,
                sessionId,
                version,
                toDto(updated)
        );

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                message
        );
    }

    @MessageMapping("/token.hp")
    public void updateHp(TokenHpUpdateEvent event,
                         @Header("playerId") String playerId,
                         @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = sessionService.getSession(sessionId);

        Token updated = tokenService.updateHp(event, player);
        long version = session.incrementVersion();
        WsMessage<TokenDto> message = new WsMessage<>(
                WsEventType.TOKEN_HP,
                sessionId,
                version,
                toDto(updated)
        );

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                message
        );
    }

    private static TokenDto toDto(Token t) {
        return new TokenDto(t.getId(), t.getName(), t.getCol(), t.getRow(),
                t.getOwnerId(), t.getHp(), t.getMaxHp());
    }
}