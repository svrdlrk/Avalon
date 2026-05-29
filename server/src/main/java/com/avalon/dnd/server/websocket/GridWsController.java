package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.service.GridService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.service.SessionValidationService;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.WsEventType;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class GridWsController {

    private final GridService gridService;
    private final SessionService sessionService;
    private final SessionValidationService validationService;
    private final SessionWsController sessionWsController;

    public GridWsController(GridService gridService,
                            SessionService sessionService,
                            SessionValidationService validationService,
                            SessionWsController sessionWsController) {
        this.gridService = gridService;
        this.sessionService = sessionService;
        this.validationService = validationService;
        this.sessionWsController = sessionWsController;
    }

    @MessageMapping("/map.grid.update")
    public void updateGrid(GridConfig newGrid,
                           @Header(value = "simpSessionId", required = false) String wsSessionId,
                           @Header("sessionId") String sessionId) {

        Player player = validationService.validateBound(sessionId, wsSessionId);
        GameSession session = sessionService.getSession(sessionId);

        MapLayoutUpdateDto layout = gridService.updateGrid(player, newGrid);
        session.markVisibilityDirty();
        session.incrementVersion();
        sessionWsController.broadcastMapLayout(session, WsEventType.MAP_UPDATED, layout, false);
    }
}
