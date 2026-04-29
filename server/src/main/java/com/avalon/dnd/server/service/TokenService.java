package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Role;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.*;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TokenService {

    private final SessionService sessionService;
    private final MapBattleRulesService battleRulesService;

    public TokenService(SessionService sessionService, MapBattleRulesService battleRulesService) {
        this.sessionService = sessionService;
        this.battleRulesService = battleRulesService;
    }

    public Token createToken(TokenCreateRequest request, Player player) {
        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can create tokens");
        }
        var session = sessionService.getSession(player.getSessionId());
        if (session == null) throw new RuntimeException("Session not found");

        String tokenId = UUID.randomUUID().toString();
        Token token = new Token(
                tokenId, request.getName(), request.getCol(),
                request.getRow(), request.getOwnerId(), player.getSessionId(),
                request.getDayVision(), request.getNightVision()
        );
        token.setHp(Math.max(1, request.getHp()));
        token.setMaxHp(Math.max(1, request.getMaxHp()));
        token.setGridSize(request.getGridSize());
        token.setImageUrl(request.getImageUrl());

        if (!battleRulesService.isTokenPlacementAllowed(session, token.getCol(), token.getRow(), token.getGridSize())) {
            throw new RuntimeException("Placement blocked by terrain or wall geometry");
        }

        session.getTokens().put(tokenId, token);
        return token;
    }

    public Token moveToken(TokenMoveEvent event, Player player) {
        if (event.getTokenId() == null || event.getTokenId().isBlank()) {
            throw new RuntimeException("TokenId is required");
        }

        var session = sessionService.getSession(player.getSessionId());
        if (session == null) throw new RuntimeException("Session not found");

        Token token = session.getTokens().get(event.getTokenId());
        if (token == null) throw new RuntimeException("Token not found: " + event.getTokenId());

        if (!canMove(token, player)) {
            throw new RuntimeException("Forbidden: player " + player.getId()
                    + " cannot move token " + token.getId());
        }

        var grid = session.getGrid();
        // Учитываем gridSize при проверке границ
        int maxCol = grid.getCols() - token.getGridSize();
        int maxRow = grid.getRows() - token.getGridSize();
        if (event.getToCol() < 0 || event.getToCol() > maxCol
                || event.getToRow() < 0 || event.getToRow() > maxRow) {
            throw new RuntimeException("Out of bounds: ("
                    + event.getToCol() + "," + event.getToRow() + ")");
        }

        int newCol = event.getToCol();
        int newRow = event.getToRow();

        if (!battleRulesService.isTokenMoveAllowed(session, token, newCol, newRow)) {
            throw new RuntimeException("Move blocked by wall, terrain, object or token geometry");
        }

        synchronized (token) {
            token.setCol(newCol);
            token.setRow(newRow);
        }

        return token;
    }

    public String removeToken(TokenRemoveEvent event, Player player) {
        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can remove tokens");
        }
        var session = sessionService.getSession(player.getSessionId());
        if (session == null) throw new RuntimeException("Session not found");

        Token token = session.getTokens().remove(event.getTokenId());
        if (token == null) throw new RuntimeException("Token not found");

        return event.getTokenId();
    }

    public Token assignToken(TokenAssignRequest request, Player player) {
        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can assign tokens");
        }
        var session = sessionService.getSession(player.getSessionId());
        if (session == null) throw new RuntimeException("Session not found");

        Token token = session.getTokens().get(request.getTokenId());
        if (token == null) throw new RuntimeException("Token not found");

        if (request.getOwnerId() != null
                && !session.getPlayers().containsKey(request.getOwnerId())) {
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
        if (session == null) throw new RuntimeException("Session not found");

        Token token = session.getTokens().get(event.getTokenId());
        if (token == null) throw new RuntimeException("Token not found");

        int maxHp = Math.max(1, event.getMaxHp());
        int hp = Math.min(maxHp, Math.max(0, event.getHp()));
        token.setMaxHp(maxHp);
        token.setHp(hp);
        return token;
    }

    // Конвертация модели → DTO
    public static TokenDto toDto(Token t) {
        return new TokenDto(
                t.getId(), t.getName(),
                t.getCol(), t.getRow(),
                t.getOwnerId(),
                t.getHp(), t.getMaxHp(),
                t.getGridSize(), t.getImageUrl(),
                t.getDayVision(), t.getNightVision()
        );
    }

    private boolean canMove(Token token, Player player) {
        if (player.getRole() == Role.DM) return true;
        return token.getOwnerId() != null
                && token.getOwnerId().equals(player.getId());
    }
}