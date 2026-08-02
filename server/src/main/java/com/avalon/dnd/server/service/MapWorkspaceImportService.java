package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.MapEditorProjectImportDto;
import com.avalon.dnd.server.model.MapObject;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.GridConfig;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

@Service
public class MapWorkspaceImportService {

    public void apply(GameSession session, String sessionId, MapEditorProjectImportDto dto) {
        if (session == null || dto == null) {
            throw new IllegalArgumentException("session and dto are required");
        }
        String normalizedSessionId = sessionId == null || sessionId.isBlank() ? session.getId() : sessionId;

        session.getTokens().clear();
        session.getObjects().clear();

        session.setGrid(dto.getGrid() == null ? new GridConfig(64, 20, 20) : dto.getGrid());
        session.setBackgroundUrl(AssetUrlNormalizer.normalize(
                extractBackgroundUrl(dto.getBackgroundLayer(), dto.getBackgroundUrl(), dto.getReferenceOverlayLayer())));
        session.setReferenceOverlayLayer(dto.getReferenceOverlayLayer());
        session.setTerrainLayer(dto.getTerrainLayer());
        session.setWallLayer(WallLayerNormalizer.normalize(dto.getWallLayer()));
        session.setFogSettings(dto.getFogSettings());
        session.setMicroLocations(dto.getMicroLocations());
        session.setAssetPackIds(dto.getAssetPackIds());
        session.setInitiativeState(null);
        session.setVisibilityState(null);
        session.getVisibilityStatesByPlayer().clear();
        session.markVisibilityDirty();

        if (dto.getPlacements() != null) {
            for (MapEditorProjectImportDto.PlacementDto placement : dto.getPlacements()) {
                if (placement == null) continue;
                if (isTokenKind(placement.getKind())) {
                    Token token = new Token(
                            safeId(placement.getId()),
                            safeName(placement.getName(), placement.getAssetId(), placement.getId()),
                            placement.getCol(),
                            placement.getRow(),
                            null,
                            normalizedSessionId
                    );
                    token.setGridSize(Math.max(1, placement.getGridSize()));
                    token.setImageUrl(AssetUrlNormalizer.normalize(placement.getImageUrl()));
                    token.setDayVision(placement.getDayVision());
                    token.setNightVision(placement.getNightVision());
                    session.getTokens().put(token.getId(), token);
                } else {
                    int w = Math.max(1, placement.getWidth());
                    int h = Math.max(1, placement.getHeight());
                    MapObject obj = new MapObject(
                            safeId(placement.getId()),
                            safeName(placement.getName(), placement.getAssetId(), placement.getId()),
                            placement.getCol(),
                            placement.getRow(),
                            w,
                            h,
                            normalizedSessionId,
                            Math.max(1, placement.getGridSize()),
                            AssetUrlNormalizer.normalize(placement.getImageUrl()),
                            placement.isBlocksMovement(),
                            placement.isBlocksSight()
                    );
                    obj.setMicroLocationId(placement.getMicroLocationId());
                    session.getObjects().put(obj.getId(), obj);
                }
            }
        }
    }

    private static boolean isTokenKind(String kind) {
        if (kind == null) return false;
        return "TOKEN".equalsIgnoreCase(kind) || "SPAWN".equalsIgnoreCase(kind);
    }

    private static String safeId(String id) {
        return (id == null || id.isBlank()) ? java.util.UUID.randomUUID().toString() : id;
    }

    private static String safeName(String name, String assetId, String fallbackId) {
        if (name != null && !name.isBlank()) return name;
        if (assetId != null && !assetId.isBlank()) return assetId;
        return fallbackId;
    }

    static String extractBackgroundUrl(JsonNode backgroundLayer, String legacyBackgroundUrl, JsonNode referenceOverlayLayer) {
        String background = extractLayerImageUrl(backgroundLayer);
        if (background != null) {
            return background;
        }
        if (legacyBackgroundUrl != null && !legacyBackgroundUrl.isBlank()) {
            return legacyBackgroundUrl;
        }
        return extractLayerImageUrl(referenceOverlayLayer);
    }

    private static String extractLayerImageUrl(JsonNode layer) {
        if (layer != null && !layer.isNull() && !layer.isMissingNode()) {
            for (String key : new String[]{"imageUrl", "image", "path", "src", "url", "file", "imagePath", "assetPath", "backgroundUrl"}) {
                JsonNode value = layer.get(key);
                if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                    return value.asText();
                }
            }
        }
        return null;
    }
}
