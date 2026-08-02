package com.avalon.dnd.server.controller;

import com.avalon.dnd.server.service.SessionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Только id сессии — без внутреннего состояния (токены, игроки).
     */
    @PostMapping("/create")
    public SessionCreatedResponse createSession() {
        var session = sessionService.createSession();
        return new SessionCreatedResponse(session.getId(), session.getDmSecret());
    }

    @PostMapping("/{sessionId}/projector-link")
    public ResponseEntity<ProjectorAccessResponse> issueProjectorLink(
            @PathVariable String sessionId,
            @RequestHeader("X-DM-Secret") String dmSecret) {
        try {
            return ResponseEntity.ok(new ProjectorAccessResponse(sessionId,
                    sessionService.issueProjectorToken(sessionId, dmSecret)));
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).build();
        }
    }

    @DeleteMapping("/{sessionId}/projector-link")
    public ResponseEntity<Void> revokeProjectorLink(
            @PathVariable String sessionId,
            @RequestHeader("X-DM-Secret") String dmSecret) {
        try {
            sessionService.revokeProjectorToken(sessionId, dmSecret);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.status(403).build();
        }
    }

    public record SessionCreatedResponse(String id, String dmSecret) {}
    public record ProjectorAccessResponse(String sessionId, String projectorToken) {}
}
