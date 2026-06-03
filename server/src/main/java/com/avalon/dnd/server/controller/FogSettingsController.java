package com.avalon.dnd.server.controller;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.service.MapBattleRulesService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.websocket.SessionWsController;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class FogSettingsController {

    private final SessionService sessionService;
    private final MapBattleRulesService battleRulesService;
    private final SessionWsController sessionWsController;

    public FogSettingsController(SessionService sessionService,
                                 MapBattleRulesService battleRulesService,
                                 SessionWsController sessionWsController) {
        this.sessionService = sessionService;
        this.battleRulesService = battleRulesService;
        this.sessionWsController = sessionWsController;
    }

    @PostMapping("/{sessionId}/fog")
    public ResponseEntity<String> updateFog(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "true")  boolean enabled,
            @RequestParam(defaultValue = "true")  boolean revealFromTokens,
            @RequestParam(defaultValue = "false") boolean retainExplored) {

        GameSession session = sessionService.getSession(sessionId);
        if (session == null) return ResponseEntity.notFound().build();

        ObjectNode fogSettings = JsonNodeFactory.instance.objectNode();
        fogSettings.put("enabled", enabled);
        fogSettings.put("revealFromTokens", revealFromTokens);
        fogSettings.put("retainExploredCells", retainExplored);

        session.setFogSettings(fogSettings);
        session.markVisibilityDirty();
        battleRulesService.computeVisibility(session);
        session.incrementVersion();
        sessionWsController.broadcastSessionState(session);

        return ResponseEntity.ok(enabled ? "Fog enabled" : "Fog disabled");
    }
}