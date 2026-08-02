package com.avalon.dnd.server.service;

import com.avalon.dnd.server.websocket.SessionWsController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class MapServiceWorkspaceImportTest {

    @TempDir
    Path tempDir;

    @Test
    void uploadingKnownFinishedMapImageImportsWorkspaceGeometry() throws Exception {
        SessionService sessionService = new SessionService();
        var session = sessionService.createSession();
        MapBattleRulesService rules = new MapBattleRulesService(new MapLayoutAssembler());
        MapService service = new MapService(
                sessionService,
                mock(SimpMessagingTemplate.class),
                mock(SessionWsController.class),
                rules,
                new MapWorkspaceImportService(),
                new ObjectMapper().findAndRegisterModules(),
                tempDir.toString()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "orcs.png",
                "image/png",
                new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0}
        );

        String url = service.uploadMap(session.getId(), file);

        assertTrue(url.startsWith("/uploads/maps/finished/"));
        assertEquals(100, session.getGrid().getCellSize());
        assertNotNull(session.getWallLayer());
        assertTrue(session.getWallLayer().path("paths").size() > 0);
    }
}
