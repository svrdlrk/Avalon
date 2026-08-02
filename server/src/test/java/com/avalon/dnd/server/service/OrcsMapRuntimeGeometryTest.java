package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.MapEditorProjectImportDto;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class OrcsMapRuntimeGeometryTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void importedOrcsWorkspaceKeepsWallsInRuntimeLayout() throws Exception {
        MapEditorProjectImportDto dto = readOrcsMap();
        assertNotNull(dto.getWallLayer(), "Orcs map.json must deserialize wallLayer");

        SessionService sessionService = new SessionService();
        GameSession session = sessionService.createSession();
        session.setGrid(dto.getGrid());
        session.setWallLayer(WallLayerNormalizer.normalize(dto.getWallLayer()));

        MapBattleRulesService rules = new MapBattleRulesService(new MapLayoutAssembler());
        MapLayoutUpdateDto layout = rules.buildMapLayout(session, null);

        assertNotNull(layout.getWallLayer());
        assertTrue(layout.getWallLayer().path("paths").size() > 0);
    }

    @Test
    void importedOrcsWallsBlockTokenMovementAcrossWallSegment() throws Exception {
        MapEditorProjectImportDto dto = readOrcsMap();

        SessionService sessionService = new SessionService();
        GameSession session = sessionService.createSession();
        session.setGrid(dto.getGrid());
        session.setWallLayer(WallLayerNormalizer.normalize(dto.getWallLayer()));

        Token token = new Token("token-a", "Token", 0, 8, null, session.getId());
        token.setGridSize(1);
        session.getTokens().put(token.getId(), token);

        MapBattleRulesService rules = new MapBattleRulesService(new MapLayoutAssembler());

        assertFalse(rules.isTokenMoveAllowed(session, token, 1, 8));
    }

    private MapEditorProjectImportDto readOrcsMap() throws Exception {
        Path path = Path.of("uploads", "maps", "finished", "Orcs", "map.json");
        assertTrue(Files.exists(path), "Expected local Orcs map workspace at " + path.toAbsolutePath());
        return mapper.readValue(Files.readString(path), MapEditorProjectImportDto.class);
    }
}
