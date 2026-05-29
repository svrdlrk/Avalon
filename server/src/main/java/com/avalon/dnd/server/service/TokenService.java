package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Role;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.PlacementSizingRules;
import com.avalon.dnd.shared.TokenAssignRequest;
import com.avalon.dnd.shared.TokenCreateRequest;
import com.avalon.dnd.shared.TokenDto;
import com.avalon.dnd.shared.TokenHpUpdateEvent;
import com.avalon.dnd.shared.TokenMoveEvent;
import com.avalon.dnd.shared.TokenRemoveEvent;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TokenService {

    private static final int MAX_TOKEN_NAME_LENGTH = 80;
    private static final int MAX_VISION_RADIUS = 120;

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

        synchronized (session) {
            String ownerId = blankToNull(request.getOwnerId());
            if (ownerId != null && !session.getPlayers().containsKey(ownerId)) {
                throw new RuntimeException("Owner not found in session");
            }

            int maxHp = Math.max(1, request.getMaxHp());
            int hp = Math.min(maxHp, Math.max(0, request.getHp()));
            int gridSize = PlacementSizingRules.clampTokenGridSize(request.getGridSize());
            String tokenId = UUID.randomUUID().toString();

            Token token = new Token(
                    tokenId,
                    normalizeTokenName(request.getName(), tokenId),
                    request.getCol(),
                    request.getRow(),
                    ownerId,
                    player.getSessionId(),
                    clampVision(request.getDayVision()),
                    clampVision(request.getNightVision())
            );
            token.setHp(hp);
            token.setMaxHp(maxHp);
            token.setGridSize(gridSize);
            token.setImageUrl(AssetUrlNormalizer.normalize(blankToNull(request.getImageUrl())));

            if (!battleRulesService.isTokenPlacementAllowed(session, token.getCol(), token.getRow(), token.getGridSize())) {
                throw new RuntimeException("Placement blocked by terrain or wall geometry");
            }

            session.getTokens().put(tokenId, token);
            session.markVisibilityDirty();
            return token;
        }
    }

    public Token moveToken(TokenMoveEvent event, Player player) {
        if (event.getTokenId() == null || event.getTokenId().isBlank()) {
            throw new RuntimeException("TokenId is required");
        }

        var session = sessionService.getSession(player.getSessionId());
        if (session == null) throw new RuntimeException("Session not found");

        synchronized (session) {
            Token token = session.getTokens().get(event.getTokenId());
            if (token == null) throw new RuntimeException("Token not found: " + event.getTokenId());

            if (!canMove(token, player)) {
                throw new RuntimeException("Forbidden: player " + player.getId()
                        + " cannot move token " + token.getId());
            }

            var grid = session.getGrid();
            int size = PlacementSizingRules.clampTokenGridSize(token.getGridSize());
            token.setGridSize(size);
            int maxCol = grid.getCols() - size;
            int maxRow = grid.getRows() - size;
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

            token.setCol(newCol);
            token.setRow(newRow);
            session.markVisibilityDirty();
            return token;
        }
    }

    public String removeToken(TokenRemoveEvent event, Player player) {
        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can remove tokens");
        }
        var session = sessionService.getSession(player.getSessionId());
        if (session == null) throw new RuntimeException("Session not found");

        synchronized (session) {
            Token token = session.getTokens().remove(event.getTokenId());
            if (token == null) throw new RuntimeException("Token not found");
            session.markVisibilityDirty();
        }

        return event.getTokenId();
    }

    public Token assignToken(TokenAssignRequest request, Player player) {
        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can assign tokens");
        }
        var session = sessionService.getSession(player.getSessionId());
        if (session == null) throw new RuntimeException("Session not found");

        synchronized (session) {
            Token token = session.getTokens().get(request.getTokenId());
            if (token == null) throw new RuntimeException("Token not found");

            String ownerId = blankToNull(request.getOwnerId());
            if (ownerId != null && !session.getPlayers().containsKey(ownerId)) {
                throw new RuntimeException("Owner not found in session");
            }

            token.setOwnerId(ownerId);
            session.markVisibilityDirty();
            return token;
        }
    }

    public Token updateHp(TokenHpUpdateEvent event, Player player) {
        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can edit HP");
        }
        var session = sessionService.getSession(player.getSessionId());
        if (session == null) throw new RuntimeException("Session not found");

        synchronized (session) {
            Token token = session.getTokens().get(event.getTokenId());
            if (token == null) throw new RuntimeException("Token not found");

            int maxHp = Math.max(1, event.getMaxHp());
            int hp = Math.min(maxHp, Math.max(0, event.getHp()));
            token.setMaxHp(maxHp);
            token.setHp(hp);
            return token;
        }
    }

    public static TokenDto toDto(Token t) {
        TokenDto dto = new TokenDto(
                t.getId(), t.getName(),
                t.getCol(), t.getRow(),
                t.getOwnerId(),
                t.getHp(), t.getMaxHp(),
                t.getGridSize(), AssetUrlNormalizer.normalize(t.getImageUrl()),
                t.getDayVision(), t.getNightVision()
        );
        dto.setFacingAngleDeg(t.getFacingAngleDeg());
        return dto;
    }

    private boolean canMove(Token token, Player player) {
        if (player.getRole() == Role.DM) return true;
        return token.getOwnerId() != null
                && token.getOwnerId().equals(player.getId());
    }

    private static String normalizeTokenName(String name, String fallbackId) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isBlank()) {
            return "Token " + fallbackId.substring(0, 8);
        }
        return normalized.length() <= MAX_TOKEN_NAME_LENGTH
                ? normalized
                : normalized.substring(0, MAX_TOKEN_NAME_LENGTH);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static int clampVision(int value) {
        return Math.max(0, Math.min(MAX_VISION_RADIUS, value));
    }
}
