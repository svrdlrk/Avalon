package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.service.GridService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.service.SessionValidationService;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.WsEventType;
import com.avalon.dnd.shared.WsMessage;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class GridWsController {

    private final GridService gridService;
    private final SessionService sessionService;
    private final SessionValidationService validationService;
    private final SimpMessagingTemplate messagingTemplate;

    public GridWsController(GridService gridService,
                            SessionService sessionService,
                            SessionValidationService validationService,
                            SimpMessagingTemplate messagingTemplate) {
        this.gridService = gridService;
        this.sessionService = sessionService;
        this.validationService = validationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/map.grid.update")
    public void updateGrid(GridConfig newGrid,
                           @Header("playerId") String playerId,
                           @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        GameSession session = sessionService.getSession(sessionId);

        MapLayoutUpdateDto layout = gridService.updateGrid(player, newGrid);
        long version = session.incrementVersion();

        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                new WsMessage<>(WsEventType.MAP_UPDATED, sessionId, version, layout)
        );
    }
}
