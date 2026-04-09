package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Role;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.service.SessionValidationService;
import com.avalon.dnd.shared.InitiativeStateDto;
import com.avalon.dnd.shared.InitiativeUpdateRequest;
import com.avalon.dnd.shared.WsEventType;
import com.avalon.dnd.shared.WsMessage;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Принимает от DM обновление инициативы и рассылает его всем участникам сессии.
 *
 * DM → /app/initiative.update  →  broadcast /topic/session/{id}  →  player-clients
 */
@Controller
public class InitiativeWsController {

    private final SessionService           sessionService;
    private final SessionValidationService validationService;
    private final SimpMessagingTemplate    messaging;

    public InitiativeWsController(SessionService sessionService,
                                  SessionValidationService validationService,
                                  SimpMessagingTemplate messaging) {
        this.sessionService    = sessionService;
        this.validationService = validationService;
        this.messaging         = messaging;
    }

    @MessageMapping("/initiative.update")
    public void update(InitiativeUpdateRequest request,
                       @Header("playerId")  String playerId,
                       @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can update initiative");
        }

        GameSession session = sessionService.getSession(sessionId);
        if (session == null) throw new RuntimeException("Session not found");

        InitiativeStateDto state = new InitiativeStateDto(
                request.getEntries(),
                Math.max(0, request.getCurrentIndex()));

        // Сохраняем в сессии чтобы новые игроки получали при SESSION_STATE
        session.setInitiativeState(state);

        long version = session.incrementVersion();
        messaging.convertAndSend(
                "/topic/session/" + sessionId,
                new WsMessage<>(WsEventType.INITIATIVE_UPDATED, sessionId, version, state));
    }

    /**
     * DM сбрасывает инициативу (очищает панель у всех клиентов).
     */
    @MessageMapping("/initiative.clear")
    public void clear(@Header("playerId")  String playerId,
                      @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can clear initiative");
        }

        GameSession session = sessionService.getSession(sessionId);
        if (session == null) throw new RuntimeException("Session not found");

        session.setInitiativeState(null);

        long version = session.incrementVersion();
        messaging.convertAndSend(
                "/topic/session/" + sessionId,
                new WsMessage<>(WsEventType.INITIATIVE_UPDATED, sessionId, version,
                        new InitiativeStateDto(java.util.List.of(), 0)));
    }
}