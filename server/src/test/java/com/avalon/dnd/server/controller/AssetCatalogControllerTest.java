package com.avalon.dnd.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AssetCatalogControllerTest {

    @Test
    void catalogUsesRussianNamesAndPhysicalFolderHierarchy() {
        JsonNode catalog = new AssetCatalogController().getCatalog();
        JsonNode goblin = findByImageSuffix(catalog.path("tokens"), "/goblin_archer.png");

        assertNotNull(goblin, "The packaged goblin token must be available through the server catalog");
        assertEquals("Гоблин-лучник", goblin.path("name").asText());
        assertEquals("medium/creatures", goblin.path("category").asText());
    }

    private JsonNode findByImageSuffix(JsonNode assets, String suffix) {
        if (!assets.isArray()) {
            return null;
        }
        for (JsonNode asset : assets) {
            if (asset.path("imageUrl").asText().replace('\\', '/').endsWith(suffix)) {
                return asset;
            }
        }
        return null;
    }
}
