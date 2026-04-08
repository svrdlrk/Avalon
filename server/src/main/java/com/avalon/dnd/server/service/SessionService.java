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
        return sessions.computeIfAbsent(id, GameSession::new);
    }

    /**
     * Подключение к сессии.
     * Если игрок с таким именем и ролью уже есть (переподключение) — возвращаем
     * существующего игрока вместо создания нового.
     */
    public Player joinSession(String sessionId, String playerName, boolean isDm) {
        GameSession session = sessions.get(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found: " + sessionId);
        }

        Role desiredRole = isDm ? Role.DM : Role.PLAYER;

        if (isDm) {
            Optional<Player> existingDm = session.getPlayers().values().stream()
                    .filter(p -> p.getRole() == Role.DM)
                    .findFirst();
            if (existingDm.isPresent()) {
                if (existingDm.get().getName().equals(playerName)) {
                    return existingDm.get();
                }
                throw new RuntimeException("Session already has a DM");
            }
        }

        Optional<Player> reconnecting = session.getPlayers().values().stream()
                .filter(p -> p.getName().equals(playerName) && p.getRole() == desiredRole)
                .findFirst();

        if (reconnecting.isPresent()) {
            return reconnecting.get();
        }

        String playerId = UUID.randomUUID().toString();
        Player player = new Player(playerId, playerName, sessionId, desiredRole);
        session.getPlayers().put(playerId, player);
        return player;
    }

    public GameSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }
}