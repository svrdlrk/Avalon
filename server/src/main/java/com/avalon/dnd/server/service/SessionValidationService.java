package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import org.springframework.stereotype.Service;

@Service
public class SessionValidationService {

    private final SessionService sessionService;

    public SessionValidationService(SessionService sessionService) {
        this.sessionService = sessionService;
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

    public Player validate(String sessionId, String playerId) {
        GameSession session = getSessionOrThrow(sessionId);
        return getPlayerOrThrow(session, playerId);
    }
}