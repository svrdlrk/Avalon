package com.avalon.dnd.server.startup;

import com.avalon.dnd.server.persistence.SavedSessionEntity;
import com.avalon.dnd.server.persistence.SavedSessionRepository;
import com.avalon.dnd.server.persistence.SessionSnapshot;
import com.avalon.dnd.server.service.SessionPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * При старте сервера автоматически восстанавливает все сохранённые сессии из БД
 * в оперативную память (SessionService.sessions map).
 *
 * Благодаря файловой H2 (jdbc:h2:file:...) данные переживают рестарт,
 * а этот runner подтягивает их в in-memory слой.
 */
@Component
public class SessionRestoreRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SessionRestoreRunner.class);

    private final SavedSessionRepository     repository;
    private final SessionPersistenceService  persistenceService;
    private final ObjectMapper               objectMapper;

    public SessionRestoreRunner(SavedSessionRepository repository,
                                SessionPersistenceService persistenceService,
                                ObjectMapper objectMapper) {
        this.repository         = repository;
        this.persistenceService = persistenceService;
        this.objectMapper       = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SavedSessionEntity> saved = repository.findAllByOrderBySavedAtDesc();

        if (saved.isEmpty()) {
            log.info("[startup] No saved sessions found in database.");
            return;
        }

        log.info("[startup] Restoring {} session(s) from database...", saved.size());
        int ok = 0, fail = 0;

        for (SavedSessionEntity entity : saved) {
            try {
                // Validate JSON before restoring
                objectMapper.readValue(entity.getSnapshotJson(), SessionSnapshot.class);
                persistenceService.loadSession(entity.getSessionId());
                log.info("[startup]  ✓ Restored '{}' ({})", entity.getDisplayName(), entity.getSessionId());
                ok++;
            } catch (Exception e) {
                log.warn("[startup]  ✗ Failed to restore '{}' ({}): {}",
                        entity.getDisplayName(), entity.getSessionId(), e.getMessage());
                fail++;
            }
        }

        log.info("[startup] Restore complete: {} ok, {} failed.", ok, fail);
    }
}