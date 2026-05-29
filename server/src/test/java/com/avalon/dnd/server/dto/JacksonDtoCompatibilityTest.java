package com.avalon.dnd.server.dto;

import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.SessionStateDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JacksonDtoCompatibilityTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void sessionStateDtoAcceptsOpaqueLayerPayloads() throws Exception {
        String json = """
                {
                  "myPlayerId": "dm-1",
                  "grid": {"cols": 20, "rows": 15, "cellSize": 32},
                  "referenceOverlayLayer": {"visible": true},
                  "terrainLayer": {"cells": [{"x": 1, "y": 2}]},
                  "wallLayer": {"paths": []},
                  "fogSettings": {"enabled": true}
                }
                """;

        SessionStateDto dto = mapper.readValue(json, SessionStateDto.class);

        assertEquals("dm-1", dto.getMyPlayerId());
        assertNotNull(dto.getReferenceOverlayLayer());
        assertNotNull(dto.getTerrainLayer());
        assertNotNull(dto.getWallLayer());
        assertNotNull(dto.getFogSettings());
    }

    @Test
    void mapLayoutUpdateDtoAcceptsOpaqueLayerPayloads() throws Exception {
        String json = """
                {
                  "backgroundUrl": "/uploads/maps/bg.png",
                  "referenceOverlayLayer": {"visible": true},
                  "terrainLayer": {"cells": []},
                  "wallLayer": {"paths": []},
                  "fogSettings": {"enabled": false}
                }
                """;

        MapLayoutUpdateDto dto = mapper.readValue(json, MapLayoutUpdateDto.class);

        assertEquals("/uploads/maps/bg.png", dto.getBackgroundUrl());
        assertNotNull(dto.getReferenceOverlayLayer());
        assertNotNull(dto.getTerrainLayer());
        assertNotNull(dto.getWallLayer());
        assertNotNull(dto.getFogSettings());
    }
}
