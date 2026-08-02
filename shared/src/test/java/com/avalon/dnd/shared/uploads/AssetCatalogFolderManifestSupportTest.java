package com.avalon.dnd.shared.uploads;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssetCatalogFolderManifestSupportTest {

    @Test
    void preservesPhysicalFolderNamesAndCase() {
        Path root = Path.of("uploads", "assets", "tokens");
        Path image = root.resolve(Path.of("Large", "Creatures", "Ogre.png"));

        assertEquals("Large/Creatures", AssetCatalogFolderManifestSupport.relativeCategory(root, image));
        assertEquals("Large/Creatures", AssetCatalogFolderManifestSupport.normalizeCategoryPath("/Large\\Creatures/"));
    }
}
