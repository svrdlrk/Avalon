package com.avalon.dnd.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WallLayerNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void normalizeKeepsCanonicalPathsUsableForClientsAndRules() throws Exception {
        JsonNode source = mapper.readTree("""
                {
                  "visible": false,
                  "defaultBlocksMovement": true,
                  "defaultBlocksSight": true,
                  "paths": [
                    {
                      "id": "wall-a",
                      "points": [{"x": 0, "y": 0}, {"x": 100, "y": 0}]
                    }
                  ]
                }
                """);

        JsonNode normalized = WallLayerNormalizer.normalize(source);

        assertTrue(normalized.path("visible").asBoolean());
        JsonNode path = normalized.path("paths").get(0);
        assertTrue(path.path("visible").asBoolean());
        assertTrue(path.path("blocksMovement").asBoolean());
        assertTrue(path.path("blocksSight").asBoolean());
        assertEquals(2, path.path("points").size());
    }

    @Test
    void normalizePromotesLegacyPathAndPointKeys() throws Exception {
        JsonNode source = mapper.readTree("""
                {
                  "segments": [
                    {
                      "id": "legacy-wall",
                      "vertices": [{"x": 10, "y": 10}, {"x": 20, "y": 20}]
                    }
                  ]
                }
                """);

        JsonNode normalized = WallLayerNormalizer.normalize(source);

        JsonNode path = normalized.path("paths").get(0);
        assertEquals("legacy-wall", path.path("id").asText());
        assertEquals(2, path.path("points").size());
        assertTrue(path.path("blocksMovement").asBoolean());
        assertTrue(path.path("blocksSight").asBoolean());
    }
}
