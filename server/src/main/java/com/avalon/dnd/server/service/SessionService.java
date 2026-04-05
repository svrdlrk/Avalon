package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Role;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.GridConfig;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public GameSession createSession() {
        String id = UUID.randomUUID().toString();
        GameSession session = new GameSession(id);
        sessions.put(id, session);
        return session;
    }

    public Player joinSession(String sessionId, String playerName, boolean isDm) {

        GameSession session = sessions.get(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        // защита от двух DM
        if (isDm && session.getPlayers().values().stream()
                .anyMatch(p -> p.getRole() == Role.DM)) {
            throw new RuntimeException("Session already has a DM");
        }

        String playerId = UUID.randomUUID().toString();

        Player player = new Player(
                playerId,
                playerName,
                sessionId,
                isDm ? Role.DM : Role.PLAYER
        );

        session.getPlayers().put(playerId, player);

        return player;
    }

    public GameSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
}