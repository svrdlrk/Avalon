package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.persistence.SavedSessionEntity;
import com.avalon.dnd.server.persistence.SavedSessionRepository;
import com.avalon.dnd.server.persistence.SessionSnapshot;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сохранение и загрузка сессий через файловую H2 БД.
 *
 * Жизненный цикл данных:
 *  - БД переживает рестарты сервера (jdbc:h2:file:./data/avalondb)
 *  - SessionRestoreRunner при старте подгружает все записи обратно в память
 *  - DM может сохранять вручную через REST API
 *  - Автосохранение можно включить через UI (каждые 5 минут)
 */
@Service
public class SessionPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(SessionPersistenceService.class);

    private final SavedSessionRepository repository;
    private final SessionService         sessionService;
    private final ObjectMapper           objectMapper;

    public SessionPersistenceService(SavedSessionRepository repository,
                                     SessionService sessionService,
                                     ObjectMapper objectMapper) {
        this.repository     = repository;
        this.sessionService = sessionService;
        this.objectMapper   = objectMapper;
    }

    // ================================================================ Save

    /**
     * Сохраняет сессию под именем displayName.
     * Если запись уже существует — обновляет её.
     */
    @Transactional
    public void saveSession(String sessionId, String displayName) {
        GameSession session = sessionService.getSession(sessionId);
        if (session == null) throw new RuntimeException("Session not found: " + sessionId);

        try {
            String json = objectMapper.writeValueAsString(SessionSnapshot.from(session));

            SavedSessionEntity entity = repository.findById(sessionId)
                    .orElseGet(() -> new SavedSessionEntity(sessionId, displayName, json, 0));

            entity.setDisplayName(displayName);
            entity.setSnapshotJson(json);
            entity.setVersion(session.getVersion());
            entity.setSavedAt(LocalDateTime.now());
            repository.save(entity);

            log.debug("[persist] Saved session '{}' ({})", displayName, sessionId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save session: " + e.getMessage(), e);
        }
    }

    /**
     * Тихое автосохранение — не бросает исключение при ошибке, только логирует.
     * Используется для автоматических сохранений по таймеру.
     */
    @Transactional
    public void autoSave(String sessionId) {
        try {
            GameSession session = sessionService.getSession(sessionId);
            if (session == null) return;

            // Use existing display name if already saved, otherwise generic name
            String displayName = repository.findById(sessionId)
                    .map(SavedSessionEntity::getDisplayName)
                    .orElse("Сессия " + sessionId.substring(0, 8));

            saveSession(sessionId, displayName);
        } catch (Exception e) {
            log.warn("[persist] Auto-save failed for {}: {}", sessionId, e.getMessage());
        }
    }

    // ================================================================ Load

    /**
     * Загружает сессию из БД и регистрирует её в SessionService.
     * Перезаписывает состояние если сессия уже активна.
     *
     * @return восстановленная GameSession
     */
    @Transactional(readOnly = true)
    public GameSession loadSession(String sessionId) {
        SavedSessionEntity entity = repository.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Saved session not found: " + sessionId));

        try {
            SessionSnapshot snap = objectMapper.readValue(
                    entity.getSnapshotJson(), SessionSnapshot.class);
            GameSession session = restoreFromSnapshot(snap);
            log.debug("[persist] Loaded session '{}' ({})",
                    entity.getDisplayName(), sessionId);
            return session;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load session: " + e.getMessage(), e);
        }
    }

    // ================================================================ List / Delete

    public List<SavedSessionMeta> listSavedSessions() {
        return repository.findAllByOrderBySavedAtDesc().stream()
                .map(e -> new SavedSessionMeta(
                        e.getSessionId(),
                        e.getDisplayName(),
                        e.getSavedAt().toString(),
                        e.getVersion()))
                .toList();
    }

    @Transactional
    public void deleteSavedSession(String sessionId) {
        repository.deleteById(sessionId);
    }

    // ================================================================ Private

    private GameSession restoreFromSnapshot(SessionSnapshot snap) {
        // createSessionWithId returns existing OR creates new
        GameSession session = sessionService.createSessionWithId(snap.id);

        if (snap.grid != null) session.setGrid(snap.grid);
        session.setBackgroundUrl(snap.backgroundUrl);

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

        // Restore players — but clear existing first to avoid duplicates
        session.getPlayers().clear();
        if (snap.players != null) {
            snap.players.forEach(ps -> {
                var p = ps.toModel();
                session.getPlayers().put(p.getId(), p);
            });
        }

        // Restore initiative state if present
        session.setInitiativeState(snap.initiative);

        return session;
    }

    // ================================================================ DTO

    public record SavedSessionMeta(
            String sessionId,
            String displayName,
            String savedAt,
            long   version
    ) {}
}