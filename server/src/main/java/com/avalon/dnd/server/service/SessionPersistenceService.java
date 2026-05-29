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
    private final SessionSnapshotRestorer snapshotRestorer;
    private final SessionSnapshotWriter snapshotWriter;
    private final ObjectMapper objectMapper;

    public SessionPersistenceService(SavedSessionRepository repository,
                                     SessionSnapshotRestorer snapshotRestorer,
                                     SessionSnapshotWriter snapshotWriter,
                                     ObjectMapper objectMapper) {
        this.repository = repository;
        this.snapshotRestorer = snapshotRestorer;
        this.snapshotWriter = snapshotWriter;
        this.objectMapper = objectMapper;
    }

    // ================================================================ Save

    /**
     * Сохраняет сессию под именем displayName.
     * Если запись уже существует — обновляет её.
     */
    @Transactional
    public void saveSession(String sessionId, String displayName) {
        snapshotWriter.persistSession(sessionId, displayName);
    }

    /**
     * Тихое автосохранение — не бросает исключение при ошибке, только логирует.
     * Используется для автоматических сохранений по таймеру.
     */
    @Transactional
    public void autoSave(String sessionId) {
        try {
            String displayName = repository.findById(sessionId)
                    .map(SavedSessionEntity::getDisplayName)
                    .orElse("Сессия " + sessionId.substring(0, 8));

            snapshotWriter.persistSession(sessionId, displayName);
        } catch (Exception e) {
            log.warn("[persist] Auto-save failed for {}: {}", sessionId, e.getMessage(), e);
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
            GameSession session = snapshotRestorer.restore(snap);
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

    // ================================================================ DTO

    public record SavedSessionMeta(
            String sessionId,
            String displayName,
            String savedAt,
            long   version
    ) {}
}