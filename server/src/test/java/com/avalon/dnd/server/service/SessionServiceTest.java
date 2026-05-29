package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionServiceTest {

    @Test
    void playerCannotJoinAsDmWithoutSessionSecret() {
        SessionService service = new SessionService();
        GameSession session = service.createSession();

        assertThrows(RuntimeException.class,
                () -> service.joinSession(session.getId(), "Sneaky player", true, null));
    }

    @Test
    void dmCanJoinWithSessionSecret() {
        SessionService service = new SessionService();
        GameSession session = service.createSession();

        var dm = service.joinSession(session.getId(), "DM", true, session.getDmSecret());

        assertEquals(Role.DM, dm.getRole());
    }
}
