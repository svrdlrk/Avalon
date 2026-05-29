package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class SessionConnectionRegistry {

    public record PlayerBinding(String sessionId, String playerId) {}

    private final ConcurrentMap<String, PlayerBinding> wsToPlayer = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Set<String>> playerToWsSessions = new ConcurrentHashMap<>();

    public void bind(String wsSessionId, String sessionId, String playerId) {
        if (wsSessionId == null || wsSessionId.isBlank() || sessionId == null || sessionId.isBlank() || playerId == null || playerId.isBlank()) {
            return;
        }

        PlayerBinding previous = wsToPlayer.put(wsSessionId, new PlayerBinding(sessionId, playerId));
        if (previous != null) {
            Set<String> previousSessions = playerToWsSessions.get(key(previous.sessionId(), previous.playerId()));
            if (previousSessions != null) {
                previousSessions.remove(wsSessionId);
                if (previousSessions.isEmpty()) {
                    playerToWsSessions.remove(key(previous.sessionId(), previous.playerId()), previousSessions);
                }
            }
        }

        playerToWsSessions.computeIfAbsent(key(sessionId, playerId), ignored -> ConcurrentHashMap.newKeySet())
                .add(wsSessionId);
    }

    public PlayerBinding unbind(String wsSessionId) {
        if (wsSessionId == null || wsSessionId.isBlank()) {
            return null;
        }

        PlayerBinding binding = wsToPlayer.remove(wsSessionId);
        if (binding == null) {
            return null;
        }

        Set<String> sessions = playerToWsSessions.get(key(binding.sessionId(), binding.playerId()));
        if (sessions != null) {
            sessions.remove(wsSessionId);
            if (sessions.isEmpty()) {
                playerToWsSessions.remove(key(binding.sessionId(), binding.playerId()), sessions);
            }
        }
        return binding;
    }

    public PlayerBinding resolve(String wsSessionId) {
        if (wsSessionId == null || wsSessionId.isBlank()) {
            return null;
        }
        return wsToPlayer.get(wsSessionId);
    }

    public boolean isPlayerConnected(String sessionId, String playerId) {
        if (sessionId == null || sessionId.isBlank() || playerId == null || playerId.isBlank()) {
            return false;
        }
        Set<String> sessions = playerToWsSessions.get(key(sessionId, playerId));
        return sessions != null && !sessions.isEmpty();
    }

    public Player resolvePlayer(GameSession session, String wsSessionId) {
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        if (wsSessionId == null || wsSessionId.isBlank()) {
            throw new RuntimeException("WebSocket session is not joined to a player");
        }

        PlayerBinding binding = resolve(wsSessionId);
        if (binding == null) {
            throw new RuntimeException("WebSocket session is not joined to a player");
        }
        if (!session.getId().equals(binding.sessionId())) {
            throw new RuntimeException("WebSocket session is bound to a different game session");
        }

        Player player = session.getPlayers().get(binding.playerId());
        if (player == null) {
            throw new RuntimeException("Player not found in session");
        }
        return player;
    }

    private static String key(String sessionId, String playerId) {
        return sessionId + "::" + playerId;
    }
}
