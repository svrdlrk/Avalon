package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MapLayoutAssemblerTest {

    @Test
    void nonDmLayoutRedactsHiddenSceneLayers() {
        SessionService sessionService = new SessionService();
        GameSession session = sessionService.createSession();
        session.setGrid(new GridConfig(32, 12, 10));
        session.setBackgroundUrl("/assets/maps/orcs.png");
        session.setReferenceOverlayLayer(new com.fasterxml.jackson.databind.node.TextNode("overlay"));
        session.setTerrainLayer(new com.fasterxml.jackson.databind.node.TextNode("terrain"));
        session.setWallLayer(new com.fasterxml.jackson.databind.node.TextNode("walls"));
        session.setFogSettings(new com.fasterxml.jackson.databind.node.TextNode("fog"));
        session.setMicroLocations(java.util.List.of());
        session.setAssetPackIds(java.util.List.of("pack-a"));

        MapLayoutAssembler assembler = new MapLayoutAssembler();
        MapLayoutUpdateDto layout = assembler.build(session, null, "player-1", false);

        assertNotNull(layout);
        assertEquals("/assets/maps/orcs.png", layout.getBackgroundUrl());
        assertNull(layout.getReferenceOverlayLayer());
        assertNull(layout.getTerrainLayer());
        assertNull(layout.getWallLayer());
        assertNull(layout.getFogSettings());
        assertTrue(layout.getMicroLocations() == null || layout.getMicroLocations().isEmpty());
        assertTrue(layout.getAssetPackIds() == null || layout.getAssetPackIds().isEmpty());
    }

    @Test
    void dmLayoutKeepsSceneLayers() {
        SessionService sessionService = new SessionService();
        GameSession session = sessionService.createSession();
        session.setReferenceOverlayLayer(new com.fasterxml.jackson.databind.node.TextNode("overlay"));
        session.setTerrainLayer(new com.fasterxml.jackson.databind.node.TextNode("terrain"));
        session.setWallLayer(new com.fasterxml.jackson.databind.node.TextNode("walls"));
        session.setFogSettings(new com.fasterxml.jackson.databind.node.TextNode("fog"));
        session.setAssetPackIds(java.util.List.of("pack-a"));

        MapLayoutAssembler assembler = new MapLayoutAssembler();
        MapLayoutUpdateDto layout = assembler.build(session, null, "dm-1", true);

        assertNotNull(layout.getReferenceOverlayLayer());
        assertNotNull(layout.getTerrainLayer());
        assertNotNull(layout.getWallLayer());
        assertNotNull(layout.getFogSettings());
        assertEquals(1, layout.getAssetPackIds().size());
    }
}
