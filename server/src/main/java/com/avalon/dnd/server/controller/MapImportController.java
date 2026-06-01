package com.avalon.dnd.server.controller;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.MapEditorProjectImportDto;
import com.avalon.dnd.server.model.MapEditorProjectImportDto.PlacementDto;
import com.avalon.dnd.server.model.MapObject;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.server.service.MapBattleRulesService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.websocket.SessionWsController;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.WsEventType;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/session")
public class MapImportController {

    private final SessionService sessionService;
    private final MapBattleRulesService battleRulesService;
    private final SessionWsController sessionWsController;

    public MapImportController(SessionService sessionService,
                               MapBattleRulesService battleRulesService,
                               SessionWsController sessionWsController) {
        this.sessionService = sessionService;
        this.battleRulesService = battleRulesService;
        this.sessionWsController = sessionWsController;
    }

    @PostMapping("/{sessionId}/import-map")
    public ResponseEntity<SessionController.SessionCreatedResponse> importMap(@PathVariable String sessionId,
                                                                              @RequestBody MapEditorProjectImportDto dto) {
        try {
            if (dto == null) {
                return ResponseEntity.badRequest().build();
            }
            GameSession session = sessionService.createSessionWithId(sessionId);
            synchronized (session) {
                session.getTokens().clear();
                session.getObjects().clear();

                session.setGrid(dto.getGrid() == null ? new GridConfig(64, 20, 20) : dto.getGrid());
                session.setBackgroundUrl(com.avalon.dnd.server.service.AssetUrlNormalizer.normalize(
                        extractBackgroundUrl(dto.getBackgroundLayer(), dto.getBackgroundUrl(), dto.getReferenceOverlayLayer())));
                session.setReferenceOverlayLayer(dto.getReferenceOverlayLayer());
                session.setTerrainLayer(dto.getTerrainLayer());
                session.setWallLayer(dto.getWallLayer());
                session.setFogSettings(dto.getFogSettings());
                session.setMicroLocations(dto.getMicroLocations());
                session.setAssetPackIds(dto.getAssetPackIds());
                session.setInitiativeState(null);
                session.setVisibilityState(null);
                session.getVisibilityStatesByPlayer().clear();
                session.markVisibilityDirty();

                if (dto.getPlacements() != null) {
                    for (PlacementDto placement : dto.getPlacements()) {
                        if (placement == null) continue;
                        if (isTokenKind(placement.getKind())) {
                            Token token = new Token(
                                    safeId(placement.getId()),
                                    safeName(placement.getName(), placement.getAssetId(), placement.getId()),
                                    placement.getCol(),
                                    placement.getRow(),
                                    null,
                                    sessionId
                            );
                            token.setGridSize(Math.max(1, placement.getGridSize()));
                            token.setImageUrl(com.avalon.dnd.server.service.AssetUrlNormalizer.normalize(placement.getImageUrl()));
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
                                    sessionId,
                                    Math.max(1, placement.getGridSize()),
                                    com.avalon.dnd.server.service.AssetUrlNormalizer.normalize(placement.getImageUrl()),
                                    placement.isBlocksMovement(),
                                    placement.isBlocksSight()
                            );
                            obj.setMicroLocationId(placement.getMicroLocationId());
                            session.getObjects().put(obj.getId(), obj);
                        }
                    }
                }

                session.incrementVersion();
                battleRulesService.computeVisibility(session);
            }
            long version = session.getVersion();
            MapLayoutUpdateDto baseLayout = battleRulesService.buildMapLayout(session, null);
            sessionWsController.broadcastMapLayout(session, WsEventType.MAP_UPDATED, baseLayout, false);
            sessionWsController.broadcastSessionState(session);

            return ResponseEntity.ok(new SessionController.SessionCreatedResponse(session.getId(), session.getDmSecret()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
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

    private static String extractBackgroundUrl(JsonNode backgroundLayer, String legacyBackgroundUrl, JsonNode referenceOverlayLayer) {
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
