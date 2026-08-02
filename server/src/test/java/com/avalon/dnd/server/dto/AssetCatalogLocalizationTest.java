package com.avalon.dnd.server.dto;

import com.avalon.dnd.shared.uploads.AssetCatalogJsonSupport;
import com.avalon.dnd.shared.uploads.AssetCatalogSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetCatalogLocalizationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void readsFlatNamesRuDictionaryByFilenameStem() throws Exception {
        Map<String, String> names = new HashMap<>();
        AssetCatalogJsonSupport.collectNames(mapper.readTree("""
                { "goblin_archer": "Гоблин-лучник" }
                """), names);

        assertEquals("Гоблин-лучник", names.get(AssetCatalogSupport.normalizeKey("goblin_archer")));
    }
}
