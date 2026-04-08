package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.persistence.SavedSessionEntity;
import com.avalon.dnd.server.persistence.SavedSessionRepository;
import com.avalon.dnd.server.persistence.SessionSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сохраняет и восстанавливает сессии через БД.
 */
@Service
public class SessionPersistenceService {

    private final SavedSessionRepository repository;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    public SessionPersistenceService(SavedSessionRepository repository,
                                     SessionService sessionService,
                                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Сохраняет текущее состояние сессии под именем displayName.
     * Если запись уже есть — обновляет.
     */
    public void saveSession(String sessionId, String displayName) {
        GameSession session = sessionService.getSession(sessionId);
        if (session == null) throw new RuntimeException("Session not found: " + sessionId);

        try {
            SessionSnapshot snap = SessionSnapshot.from(session);
            String json = objectMapper.writeValueAsString(snap);

            SavedSessionEntity entity = repository.findById(sessionId)
                    .orElse(new SavedSessionEntity(sessionId, displayName, json, session.getVersion()));

            entity.setDisplayName(displayName);
            entity.setSnapshotJson(json);
            entity.setVersion(session.getVersion());
            entity.setSavedAt(LocalDateTime.now());

            repository.save(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save session: " + e.getMessage(), e);
        }
    }

    /**
     * Восстанавливает сессию из БД и регистрирует её в SessionService под исходным ID.
     * Если сессия с таким ID уже активна — перезаписывает её состояние.
     */
    public GameSession loadSession(String sessionId) {
        SavedSessionEntity entity = repository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Saved session not found: " + sessionId));

        try {
            SessionSnapshot snap = objectMapper.readValue(entity.getSnapshotJson(), SessionSnapshot.class);
            return restoreFromSnapshot(snap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load session: " + e.getMessage(), e);
        }
    }

    /**
     * Список всех сохранённых сессий (мета-данные, без снапшота).
     */
    public List<SavedSessionMeta> listSavedSessions() {
        return repository.findAllByOrderBySavedAtDesc().stream()
                .map(e -> new SavedSessionMeta(
                        e.getSessionId(),
                        e.getDisplayName(),
                        e.getSavedAt().toString(),
                        e.getVersion()))
                .toList();
    }

    /**
     * Удаляет сохранённую сессию.
     */
    public void deleteSavedSession(String sessionId) {
        repository.deleteById(sessionId);
    }

    // ---- private ----

    private GameSession restoreFromSnapshot(SessionSnapshot snap) {
        // Создаём новую GameSession или получаем существующую
        GameSession session = sessionService.getSession(snap.id);
        if (session == null) {
            // Регистрируем через внутренний механизм
            session = sessionService.createSessionWithId(snap.id);
        }

        // Восстанавливаем состояние
        if (snap.grid != null) session.setGrid(snap.grid);
        session.setBackgroundUrl(snap.backgroundUrl);

        session.getPlayers().clear();
        if (snap.players != null) {
            snap.players.forEach(ps -> {
                var p = ps.toModel();
                session.getPlayers().put(p.getId(), p);
            });
        }

        session.getTokens().clear();
        if (snap.tokens != null) {
            snap.tokens.forEach(ts -> {
                var t = ts.toModel();
                session.getTokens().put(t.getId(), t);
            });
        }

        session.getObjects().clear();
        if (snap.objects != null) {
            snap.objects.forEach(os -> {
                var o = os.toModel(snap.id);
                session.getObjects().put(o.getId(), o);
            });
        }

        return session;
    }

    // ---- DTO ----

    public record SavedSessionMeta(
            String sessionId,
            String displayName,
            String savedAt,
            long version
    ) {}
}