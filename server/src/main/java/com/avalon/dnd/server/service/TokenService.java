package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Role;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.TokenAssignRequest;
import com.avalon.dnd.shared.TokenCreateRequest;
import com.avalon.dnd.shared.TokenHpUpdateEvent;
import com.avalon.dnd.shared.TokenMoveEvent;
import com.avalon.dnd.shared.TokenRemoveEvent;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenService {

    private final SessionService sessionService;

    public TokenService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public Token createToken(TokenCreateRequest request, Player player) {
        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can create tokens");
        }
        var session = sessionService.getSession(player.getSessionId());
        if (session == null) {
            throw new RuntimeException("Session not found");
        }
        String tokenId = java.util.UUID.randomUUID().toString();
        Token token = new Token(
                tokenId, request.getName(), request.getCol(),
                request.getRow(), request.getOwnerId(), player.getSessionId()
        );
        // Добавляем установку HP из реквеста
        token.setHp(request.getHp());
        token.setMaxHp(request.getMaxHp());

        session.getTokens().put(tokenId, token);
        return token;
    }

    public Token moveToken(TokenMoveEvent event, Player player) {

        var session = sessionService.getSession(player.getSessionId());
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        Token token = session.getTokens().get(event.getTokenId());

        if (token == null) {
            throw new RuntimeException("Token not found");
        }

        if (event.getTokenId() == null) {
            throw new RuntimeException("TokenId is required");
        }

        if (!canMove(token, player)) {
            throw new RuntimeException("Forbidden");
        }
        var grid = session.getGrid();

        if (event.getToCol() < 0 || event.getToCol() >= grid.getCols()
                || event.getToRow() < 0 || event.getToRow() >= grid.getRows()) {
            throw new RuntimeException("Out of bounds");
        }

        synchronized (token) {
            token.setCol(event.getToCol());
            token.setRow(event.getToRow());
        }

        return token;
    }

    public String removeToken(TokenRemoveEvent event, Player player) {

        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can remove tokens");
        }

        var session = sessionService.getSession(player.getSessionId());
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        Token token = session.getTokens().remove(event.getTokenId());

        if (token == null) {
            throw new RuntimeException("Token not found");
        }

        return event.getTokenId();
    }

    public Token assignToken(TokenAssignRequest request, Player player) {

        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can assign tokens");
        }

        var session = sessionService.getSession(player.getSessionId());
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        Token token = session.getTokens().get(request.getTokenId());

        if (token == null) {
            throw new RuntimeException("Token not found");
        }

        // проверяем что игрок существует
        if (request.getOwnerId() != null &&
                !session.getPlayers().containsKey(request.getOwnerId())) {
            throw new RuntimeException("Owner not found in session");
        }

        token.setOwnerId(request.getOwnerId());

        return token;
    }

    public Token updateHp(TokenHpUpdateEvent event, Player player) {

        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can edit HP");
        }

        var session = sessionService.getSession(player.getSessionId());
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        Token token = session.getTokens().get(event.getTokenId());
        if (token == null) {
            throw new RuntimeException("Token not found");
        }

        int maxHp = Math.max(1, event.getMaxHp());
        int hp = Math.min(maxHp, Math.max(0, event.getHp()));
        token.setMaxHp(maxHp);
        token.setHp(hp);

        return token;
    }

    private boolean canMove(Token token, Player player) {
        if (player.getRole() == Role.DM) return true;

        return token.getOwnerId() != null &&
                token.getOwnerId().equals(player.getId());
    }

}