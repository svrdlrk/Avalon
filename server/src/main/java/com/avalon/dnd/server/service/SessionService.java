package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Role;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SessionService {

    private static final int MAX_SESSION_ID_LENGTH = 128;
    private static final int MAX_PLAYER_NAME_LENGTH = 64;

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public GameSession createSession() {
        String id = UUID.randomUUID().toString();
        return createSessionWithId(id);
    }

    /**
     * Создаёт сессию с конкретным ID (используется при восстановлении из БД).
     * Если сессия с таким ID уже существует — возвращает её, НЕ перезаписывает.
     * (Перезапись состояния делает SessionPersistenceService.)
     */
    public GameSession createSessionWithId(String id) {
        String normalizedId = validateSessionId(id);
        return sessions.computeIfAbsent(normalizedId, GameSession::new);
    }

    /**
     * Подключение к сессии.
     * Если игрок с таким именем и ролью уже есть (переподключение) — возвращаем
     * существующего игрока вместо создания нового.
     */
    public Player joinSession(String sessionId, String playerName, boolean isDm, String dmSecret) {
        String normalizedSessionId = validateSessionId(sessionId);
        String normalizedPlayerName = validatePlayerName(playerName);
        GameSession session = sessions.get(normalizedSessionId);
        if (session == null) {
            throw new RuntimeException("Session not found: " + normalizedSessionId);
        }

        synchronized (session) {
            Role desiredRole = isDm ? Role.DM : Role.PLAYER;

            if (isDm) {
                validateDmSecret(session, dmSecret);
                Optional<Player> existingDm = session.getPlayers().values().stream()
                        .filter(p -> p.getRole() == Role.DM)
                        .findFirst();
                if (existingDm.isPresent()) {
                    if (existingDm.get().getName().equals(normalizedPlayerName)) {
                        return existingDm.get();
                    }
                    throw new RuntimeException("Session already has a DM");
                }
            }

            Optional<Player> reconnecting = session.getPlayers().values().stream()
                    .filter(p -> p.getName().equals(normalizedPlayerName) && p.getRole() == desiredRole)
                    .findFirst();

            if (reconnecting.isPresent()) {
                return reconnecting.get();
            }

            String playerId = UUID.randomUUID().toString();
            Player player = new Player(playerId, normalizedPlayerName, normalizedSessionId, desiredRole);
            session.getPlayers().put(playerId, player);
            return player;
        }
    }

    public GameSession getSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return sessions.get(normalizeSessionId(sessionId));
    }

    private static String validateSessionId(String sessionId) {
        String normalized = normalizeSessionId(sessionId);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("Session id is required");
        }
        if (normalized.length() > MAX_SESSION_ID_LENGTH) {
            throw new IllegalArgumentException("Session id is too long");
        }
        return normalized;
    }

    private static String validatePlayerName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("Player name required");
        }
        String normalized = playerName.trim();
        if (normalized.length() > MAX_PLAYER_NAME_LENGTH) {
            throw new IllegalArgumentException("Player name is too long");
        }
        return normalized;
    }

    private static String normalizeSessionId(String sessionId) {
        if (sessionId == null) return null;
        String normalized = sessionId.trim();
        int comma = normalized.indexOf(',');
        if (comma >= 0) normalized = normalized.substring(0, comma).trim();
        return normalized;
    }

    private static void validateDmSecret(GameSession session, String providedSecret) {
        String expected = session.getDmSecret();
        if (expected == null || expected.isBlank()) {
            session.setDmSecret(null);
            expected = session.getDmSecret();
        }
        if (providedSecret == null || !expected.equals(providedSecret.trim())) {
            throw new RuntimeException("Invalid DM secret");
        }
    }
}
