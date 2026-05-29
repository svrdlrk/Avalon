package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.MapObject;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.server.service.AssetUrlNormalizer;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.MapObjectDto;
import com.avalon.dnd.shared.TokenDto;
import com.avalon.dnd.shared.VisibilityStateDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Assembles the client-facing map layout from the mutable server session model.
 * Keeps DTO conversion separate from gameplay rules so the rules service stays focused.
 */
@Service
public class MapLayoutAssembler {

    public MapLayoutUpdateDto build(GameSession session, VisibilityStateDto visibility) {
        return build(session, visibility, null, false);
    }

    public MapLayoutUpdateDto build(GameSession session, VisibilityStateDto visibility, String viewerPlayerId, boolean isDm) {
        GridConfig grid = session == null ? null : session.getGrid();
        List<TokenDto> tokens = filterTokens(session, visibility, viewerPlayerId, isDm);
        List<MapObjectDto> objects = filterObjects(session, visibility, isDm);
        String backgroundUrl = resolveBackgroundUrl(session);

        return new MapLayoutUpdateDto(
                grid,
                tokens,
                objects,
                backgroundUrl,
                visibility,
                session == null ? null : session.getReferenceOverlayLayer(),
                session == null ? null : session.getTerrainLayer(),
                session == null ? null : session.getWallLayer(),
                session == null ? null : session.getFogSettings(),
                session == null ? null : session.getMicroLocations(),
                session == null ? null : session.getAssetPackIds()
        );
    }

    private List<TokenDto> filterTokens(GameSession session, VisibilityStateDto visibility, String viewerPlayerId, boolean isDm) {
        if (session == null || session.getTokens() == null || session.getTokens().isEmpty()) {
            return List.of();
        }
        if (visibility == null || isDm) {
            return session.getTokens().values().stream()
                    .filter(Objects::nonNull)
                    .map(this::toTokenDto)
                    .toList();
        }
        return session.getTokens().values().stream()
                .filter(token -> token != null && (isOwnedByViewer(token, viewerPlayerId)
                        || isAnyCellVisible(visibility, token.getCol(), token.getRow(),
                        Math.max(1, token.getGridSize()), Math.max(1, token.getGridSize()))))
                .map(this::toTokenDto)
                .toList();
    }

    private List<MapObjectDto> filterObjects(GameSession session, VisibilityStateDto visibility, boolean isDm) {
        if (session == null || session.getObjects() == null || session.getObjects().isEmpty()) {
            return List.of();
        }
        if (visibility == null || isDm) {
            return session.getObjects().values().stream()
                    .filter(Objects::nonNull)
                    .map(this::toObjectDto)
                    .toList();
        }
        return session.getObjects().values().stream()
                .filter(object -> object != null && isAnyCellVisible(visibility, object.getCol(), object.getRow(),
                        Math.max(1, object.getWidth()), Math.max(1, object.getHeight())))
                .map(this::toObjectDto)
                .toList();
    }

    private boolean isOwnedByViewer(Token token, String viewerPlayerId) {
        return token != null && viewerPlayerId != null && !viewerPlayerId.isBlank()
                && viewerPlayerId.equals(token.getOwnerId());
    }

    private boolean isAnyCellVisible(VisibilityStateDto visibility, int col, int row, int width, int height) {
        boolean[][] cells = visibility == null ? null : visibility.getVisibleCells();
        if (cells == null || cells.length == 0) {
            return false;
        }
        for (int r = row; r < row + height; r++) {
            if (r < 0 || r >= cells.length || cells[r] == null) {
                continue;
            }
            for (int c = col; c < col + width; c++) {
                if (c >= 0 && c < cells[r].length && cells[r][c]) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveBackgroundUrl(GameSession session) {
        String background = AssetUrlNormalizer.normalize(session == null ? null : session.getBackgroundUrl());
        if (background != null) {
            return background;
        }
        return AssetUrlNormalizer.normalize(extractLayerImageUrl(session == null ? null : session.getReferenceOverlayLayer()));
    }

    private String extractLayerImageUrl(com.fasterxml.jackson.databind.JsonNode layer) {
        if (layer == null || layer.isNull() || layer.isMissingNode()) {
            return null;
        }
        for (String key : new String[]{"imageUrl", "image", "path", "src", "url", "file", "imagePath", "assetPath", "backgroundUrl"}) {
            com.fasterxml.jackson.databind.JsonNode value = layer.get(key);
            if (value != null && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private TokenDto toTokenDto(Token token) {
        if (token == null) {
            return null;
        }
        TokenDto dto = new TokenDto(
                token.getId(),
                token.getName(),
                token.getCol(),
                token.getRow(),
                token.getOwnerId(),
                token.getHp(),
                token.getMaxHp(),
                token.getGridSize(),
                token.getImageUrl(),
                token.getDayVision(),
                token.getNightVision()
        );
        dto.setFacingAngleDeg(token.getFacingAngleDeg());
        return dto;
    }

    private MapObjectDto toObjectDto(MapObject object) {
        if (object == null) {
            return null;
        }
        MapObjectDto dto = new MapObjectDto(
                object.getId(),
                object.getType(),
                object.getCol(),
                object.getRow(),
                object.getWidth(),
                object.getHeight(),
                object.getGridSize(),
                object.getImageUrl(),
                object.isBlocksMovement(),
                object.isBlocksSight()
        );
        dto.setMicroLocationId(object.getMicroLocationId());
        return dto;
    }
}
