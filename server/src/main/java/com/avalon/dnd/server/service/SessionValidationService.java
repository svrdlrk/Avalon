package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import org.springframework.stereotype.Service;

@Service
public class SessionValidationService {

    private final SessionService sessionService;
    private final SessionConnectionRegistry connectionRegistry;

    public SessionValidationService(SessionService sessionService, SessionConnectionRegistry connectionRegistry) {
        this.sessionService = sessionService;
        this.connectionRegistry = connectionRegistry;
    }

    public GameSession getSessionOrThrow(String sessionId) {
        GameSession session = sessionService.getSession(sessionId);

        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        return session;
    }

    public Player getPlayerOrThrow(GameSession session, String playerId) {
        Player player = session.getPlayers().get(playerId);

        if (player == null) {
            throw new RuntimeException("Player not found in session");
        }

        return player;
    }

    public Player validateBound(String sessionId, String wsSessionId) {
        if (wsSessionId == null || wsSessionId.isBlank()) {
            throw new RuntimeException("WebSocket session is not joined to a player");
        }
        GameSession session = getSessionOrThrow(sessionId);
        return connectionRegistry.resolvePlayer(session, wsSessionId);
    }
}
