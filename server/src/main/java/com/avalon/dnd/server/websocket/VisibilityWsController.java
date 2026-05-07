package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Role;
import com.avalon.dnd.server.service.MapBattleRulesService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.service.SessionValidationService;
import com.avalon.dnd.shared.VisibilityShareApprovalRequest;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

@Controller
public class VisibilityWsController {

    private final SessionService sessionService;
    private final SessionValidationService validationService;
    private final MapBattleRulesService battleRulesService;
    private final SessionWsController sessionWsController;

    public VisibilityWsController(SessionService sessionService,
                                  SessionValidationService validationService,
                                  MapBattleRulesService battleRulesService,
                                  SessionWsController sessionWsController) {
        this.sessionService = sessionService;
        this.validationService = validationService;
        this.battleRulesService = battleRulesService;
        this.sessionWsController = sessionWsController;
    }

    @MessageMapping("/visibility.share.approve")
    public void approve(VisibilityShareApprovalRequest request,
                        @Header("playerId") String playerId,
                        @Header("sessionId") String sessionId) {
        Player player = validationService.validate(sessionId, playerId);
        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can approve visibility sharing");
        }

        GameSession session = sessionService.getSession(sessionId);
        if (session == null) throw new RuntimeException("Session not found");

        boolean approved = battleRulesService.approveVisibilityShare(session, request == null ? null : request.getSuggestionId());
        if (!approved) {
            throw new RuntimeException("Visibility share suggestion not found");
        }

        session.incrementVersion();
        sessionWsController.broadcastSessionState(session);
    }
}
