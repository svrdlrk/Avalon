package com.avalon.dnd.server.controller;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.service.SessionPersistenceService;
import com.avalon.dnd.server.service.SessionPersistenceService.SavedSessionMeta;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API для сохранения и загрузки сессий.
 *
 * POST   /api/session/{id}/save?name=...   — сохранить текущую активную сессию
 * POST   /api/session/{id}/load            — загрузить сессию из БД (сессия становится активной)
 * GET    /api/session/saved                — список всех сохранений
 * DELETE /api/session/{id}/saved           — удалить сохранение
 */
@RestController
@RequestMapping("/api/session")
public class SessionPersistenceController {

    private final SessionPersistenceService persistenceService;
    private final SessionController sessionController; // переиспользуем createSession

    public SessionPersistenceController(SessionPersistenceService persistenceService,
                                        SessionController sessionController) {
        this.persistenceService = persistenceService;
        this.sessionController = sessionController;
    }

    /** Сохранить активную сессию под именем. */
    @PostMapping("/{sessionId}/save")
    public ResponseEntity<String> save(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "Без названия") String name) {
        try {
            persistenceService.saveSession(sessionId, name);
            return ResponseEntity.ok("Saved");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Загрузить сессию из БД.
     * Возвращает ID сессии (тот же, что был сохранён).
     * После вызова DM должен переподключиться к ней через /session.join.
     */
    @PostMapping("/{sessionId}/load")
    public ResponseEntity<SessionController.SessionCreatedResponse> load(
            @PathVariable String sessionId) {
        try {
            GameSession session = persistenceService.loadSession(sessionId);
            return ResponseEntity.ok(new SessionController.SessionCreatedResponse(session.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** Список всех сохранённых сессий. */
    @GetMapping("/saved")
    public ResponseEntity<List<SavedSessionMeta>> listSaved() {
        return ResponseEntity.ok(persistenceService.listSavedSessions());
    }

    /** Удалить сохранение из БД (активная in-memory сессия не затрагивается). */
    @DeleteMapping("/{sessionId}/saved")
    public ResponseEntity<Void> deleteSaved(@PathVariable String sessionId) {
        persistenceService.deleteSavedSession(sessionId);
        return ResponseEntity.noContent().build();
    }
}