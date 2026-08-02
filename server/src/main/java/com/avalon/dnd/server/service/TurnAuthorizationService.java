package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Role;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.InitiativeStateDto;
import org.springframework.stereotype.Service;

/** Central, server-authoritative policy for commands that control a token. */
@Service
public class TurnAuthorizationService {

    public void requireMovePermission(GameSession session, Player actor, Token token) {
        if (actor == null || token == null) {
            throw new RuntimeException("Actor and token are required");
        }
        if (actor.getRole() == Role.DM) {
            return;
        }
        if (actor.getRole() != Role.PLAYER || !actor.getId().equals(token.getOwnerId())) {
            throw new RuntimeException("You can only control your own token");
        }

        InitiativeStateDto initiative = session == null ? null : session.getInitiativeState();
        if (initiative == null || initiative.getEntries() == null || initiative.getEntries().isEmpty()) {
            return; // Exploration mode: ownership still applies, no active turn exists.
        }
        int currentIndex = initiative.getCurrentIndex();
        if (currentIndex < 0 || currentIndex >= initiative.getEntries().size()) {
            throw new RuntimeException("Initiative state is invalid; ask the DM to republish it");
        }
        InitiativeStateDto.InitiativeEntry current = initiative.getEntries().get(currentIndex);
        if (current == null || current.getTokenId() == null || !current.getTokenId().equals(token.getId())) {
            throw new RuntimeException("It is not this token's turn");
        }
    }
}
