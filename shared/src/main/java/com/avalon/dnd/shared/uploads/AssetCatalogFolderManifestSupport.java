package com.avalon.dnd.shared.uploads;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Manifest parsing helpers shared by asset catalog loader/controller implementations.
 */
public final class AssetCatalogFolderManifestSupport {

    private AssetCatalogFolderManifestSupport() {
    }

    public static int[] readFolderSize(JsonNode folderNode, JsonNode defaults) {
        int defaultWidth = readFolderInt(defaults, defaults, 1, "defaultWidth", "width", "w", "sizeX", "gridWidth", "tileWidth", "cellWidth");
        int defaultHeight = readFolderInt(defaults, defaults, 1, "defaultHeight", "height", "h", "sizeY", "gridHeight", "tileHeight", "cellHeight");
        int width = readFolderInt(folderNode, defaults, defaultWidth, "defaultWidth", "width", "w", "sizeX", "gridWidth", "tileWidth", "cellWidth");
        int height = readFolderInt(folderNode, defaults, defaultHeight, "defaultHeight", "height", "h", "sizeY", "gridHeight", "tileHeight", "cellHeight");
        int gridSize = readFolderInt(folderNode, defaults, 1, "gridSize", "grid", "size");
        if (width <= 1 && height <= 1 && gridSize > 1) {
            width = gridSize;
            height = gridSize;
        }
        return new int[] { Math.max(1, width), Math.max(1, height) };
    }

    public static int readFolderInt(JsonNode folderNode, JsonNode defaults, int defaultValue, String... fields) {
        if (hasAnyField(folderNode, fields)) {
            return AssetCatalogJsonSupport.readDimension(folderNode, defaultValue, fields);
        }
        if (hasAnyField(defaults, fields)) {
            return AssetCatalogJsonSupport.readDimension(defaults, defaultValue, fields);
        }
        return defaultValue;
    }

    public static boolean readFolderBoolean(JsonNode folderNode, JsonNode defaults, boolean defaultValue, String... fields) {
        if (hasAnyField(folderNode, fields)) {
            return AssetCatalogJsonSupport.readBoolean(folderNode, defaultValue, fields);
        }
        if (hasAnyField(defaults, fields)) {
            return AssetCatalogJsonSupport.readBoolean(defaults, defaultValue, fields);
        }
        return defaultValue;
    }

    public static boolean hasAnyField(JsonNode node, String... fields) {
        if (node == null) return false;
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeCategoryPath(String category) {
        if (category == null) return null;
        return category.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    public static String relativeCategory(Path baseDir, Path image) {
        if (baseDir != null && image != null) {
            try {
                Path normalizedBase = baseDir.toAbsolutePath().normalize();
                Path normalizedImage = image.toAbsolutePath().normalize();
                Path parent = normalizedImage.getParent();
                if (parent != null && parent.startsWith(normalizedBase)) {
                    Path relative = normalizedBase.relativize(parent);
                    String text = relative.toString().replace('\\', '/').trim();
                    if (!text.isBlank()) {
                        return text.toLowerCase(Locale.ROOT);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return AssetCatalogSupport.deriveCategory(baseDir, image == null ? null : image.toString());
    }

    public static int[] parseSizeFromName(String baseName) {
        return AssetCatalogSupport.parseSizeString(baseName);
    }
}
