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

/**
 * Persists a single session snapshot in its own transactional boundary.
 * Extracted from SessionPersistenceService to avoid self-invocation proxy issues.
 */
@Service
public class SessionSnapshotWriter {

    private static final Logger log = LoggerFactory.getLogger(SessionSnapshotWriter.class);

    private final SavedSessionRepository repository;
    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    public SessionSnapshotWriter(SavedSessionRepository repository,
                                 SessionService sessionService,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void persistSession(String sessionId, String displayName) {
        GameSession session = sessionService.getSession(sessionId);
        if (session == null) {
            return;
        }
        try {
            SessionSnapshot snapshot = SessionSnapshot.from(session);
            String json = objectMapper.writeValueAsString(snapshot);
            SavedSessionEntity entity = repository.findById(sessionId)
                    .orElseGet(SavedSessionEntity::new);
            entity.setSessionId(sessionId);
            entity.setDisplayName(displayName);
            entity.setSnapshotJson(json);
            entity.setVersion(session.getVersion());
            entity.setSavedAt(LocalDateTime.now());
            repository.save(entity);
            log.debug("[persist] Saved session '{}' ({})", displayName, sessionId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist session: " + e.getMessage(), e);
        }
    }
}
