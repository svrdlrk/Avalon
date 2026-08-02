package com.avalon.dnd.server.controller;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.MapEditorProjectImportDto;
import com.avalon.dnd.server.service.MapBattleRulesService;
import com.avalon.dnd.server.service.MapWorkspaceImportService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.websocket.SessionWsController;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.WsEventType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/session")
public class MapImportController {

    private final SessionService sessionService;
    private final MapBattleRulesService battleRulesService;
    private final SessionWsController sessionWsController;
    private final MapWorkspaceImportService mapWorkspaceImportService;

    public MapImportController(SessionService sessionService,
                               MapBattleRulesService battleRulesService,
                               SessionWsController sessionWsController,
                               MapWorkspaceImportService mapWorkspaceImportService) {
        this.sessionService = sessionService;
        this.battleRulesService = battleRulesService;
        this.sessionWsController = sessionWsController;
        this.mapWorkspaceImportService = mapWorkspaceImportService;
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
                mapWorkspaceImportService.apply(session, sessionId, dto);
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

}
