package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Role;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.InitiativeStateDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TurnAuthorizationServiceTest {

    private final TurnAuthorizationService service = new TurnAuthorizationService();

    @Test
    void playerCanMoveOnlyTheirActiveInitiativeToken() {
        GameSession session = new GameSession("session");
        Player player = new Player("player", "Player", "session", Role.PLAYER);
        Token own = new Token("own", "Own", 0, 0, "player", "session");
        Token other = new Token("other", "Other", 1, 1, "other-player", "session");
        session.setInitiativeState(new InitiativeStateDto(List.of(
                new InitiativeStateDto.InitiativeEntry("own", "Own", 20)), 0));

        assertDoesNotThrow(() -> service.requireMovePermission(session, player, own));
        assertThrows(RuntimeException.class, () -> service.requireMovePermission(session, player, other));
    }

    @Test
    void observerCannotMoveEvenWithoutInitiative() {
        GameSession session = new GameSession("session");
        Player observer = new Player("observer", "Projector", "session", Role.OBSERVER);
        Token token = new Token("token", "Token", 0, 0, "observer", "session");

        assertThrows(RuntimeException.class, () -> service.requireMovePermission(session, observer, token));
    }
}
