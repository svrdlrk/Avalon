package com.avalon.dnd.server.dto;

import com.avalon.dnd.shared.uploads.AssetCatalogJsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AssetCatalogJsonSupportTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void parseSizeFromNameUsesCatalogSupportRules() {
        assertArrayEquals(new int[] { 2, 3 }, AssetCatalogJsonSupport.parseSizeFromName("orc 2x3"));
        assertArrayEquals(new int[] { 4, 4 }, AssetCatalogJsonSupport.parseSizeFromName("giant 4"));
        assertNull(AssetCatalogJsonSupport.parseSizeFromName("unknown"));
    }

    @Test
    void readHelpersAcceptJsonNodes() throws Exception {
        var node = mapper.readTree("{\"enabled\":\"yes\",\"width\":\"7\",\"title\":\"Goblin\"}");

        assertEquals("Goblin", AssetCatalogJsonSupport.text(node, "name", "title"));
        assertTrue(AssetCatalogJsonSupport.readBoolean(node, false, "enabled"));
        assertEquals(7, AssetCatalogJsonSupport.readDimension(node, 2, "width"));
    }

    @Test
    void resolveRelativePathNormalizesRelativePaths() {
        Path resolved = AssetCatalogJsonSupport.resolveRelativePath("assets/img.png", Path.of("/tmp/base"));
        assertEquals(Path.of("/tmp/base/assets/img.png").normalize(), resolved);
    }
}
