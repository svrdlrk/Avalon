package com.avalon.dnd.server.controller;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.MapEditorProjectImportDto;
import com.avalon.dnd.server.model.MapEditorProjectImportDto.PlacementDto;
import com.avalon.dnd.server.model.MapObject;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.server.service.MapBattleRulesService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.TokenDto;
import com.avalon.dnd.shared.WsEventType;
import com.avalon.dnd.shared.WsMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/session")
public class MapImportController {

    private final SessionService sessionService;
    private final SimpMessagingTemplate messaging;
    private final MapBattleRulesService battleRulesService;

    public MapImportController(SessionService sessionService,
                               SimpMessagingTemplate messaging,
                               MapBattleRulesService battleRulesService) {
        this.sessionService = sessionService;
        this.messaging = messaging;
        this.battleRulesService = battleRulesService;
    }

    @PostMapping("/{sessionId}/import-map")
    public ResponseEntity<SessionController.SessionCreatedResponse> importMap(@PathVariable String sessionId,
                                                                              @RequestBody MapEditorProjectImportDto dto) {
        try {
            GameSession session = sessionService.createSessionWithId(sessionId);
            session.getTokens().clear();
            session.getObjects().clear();

            session.setGrid(dto.getGrid() == null ? new GridConfig(64, 20, 20) : dto.getGrid());
            session.setBackgroundUrl(extractBackgroundUrl(dto.getBackgroundLayer()));
            session.setReferenceOverlayLayer(dto.getReferenceOverlayLayer());
            session.setTerrainLayer(dto.getTerrainLayer());
            session.setWallLayer(dto.getWallLayer());
            session.setFogSettings(dto.getFogSettings());
            session.setMicroLocations(dto.getMicroLocations());
            session.setAssetPackIds(dto.getAssetPackIds());
            session.setInitiativeState(null);
            session.setVisibilityState(null);
            session.getVisibilityStatesByPlayer().clear();

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
                        token.setImageUrl(placement.getImageUrl());
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
                                placement.getImageUrl(),
                                placement.isBlocksMovement(),
                                placement.isBlocksSight()
                        );
                        obj.setMicroLocationId(placement.getMicroLocationId());
                        session.getObjects().put(obj.getId(), obj);
                    }
                }
            }

            long version = session.incrementVersion();
            battleRulesService.computeVisibility(session);
            MapLayoutUpdateDto baseLayout = battleRulesService.buildMapLayout(session, null);
            for (var player : session.getPlayers().values()) {
                MapLayoutUpdateDto layout = new MapLayoutUpdateDto(
                        baseLayout.getGrid(),
                        baseLayout.getTokens(),
                        baseLayout.getObjects(),
                        baseLayout.getBackgroundUrl(),
                        battleRulesService.getVisibilityForPlayer(session, player.getId()),
                        baseLayout.getReferenceOverlayLayer(),
                        baseLayout.getTerrainLayer(),
                        baseLayout.getWallLayer(),
                        baseLayout.getFogSettings(),
                        baseLayout.getMicroLocations(),
                        baseLayout.getAssetPackIds()
                );
                messaging.convertAndSend(
                        "/topic/session/" + sessionId + "/private/" + player.getId(),
                        new WsMessage<>(WsEventType.MAP_UPDATED, sessionId, version, layout));
            }

            return ResponseEntity.ok(new SessionController.SessionCreatedResponse(session.getId()));
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

    private static String extractBackgroundUrl(Object backgroundLayer) {
        if (!(backgroundLayer instanceof java.util.Map<?, ?> map)) {
            return null;
        }
        Object imageUrl = map.get("imageUrl");
        return imageUrl == null ? null : String.valueOf(imageUrl);
    }
}
