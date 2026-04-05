package com.avalon.dnd.server.controller;

import com.avalon.dnd.server.service.SessionService;
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
        return new SessionCreatedResponse(sessionService.createSession().getId());
    }

    public record SessionCreatedResponse(String id) {}
}