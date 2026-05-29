package com.avalon.dnd.shared.uploads;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.Map;
import java.util.Locale;

public final class AssetCatalogJsonSupport {

    private AssetCatalogJsonSupport() {
    }

    public static String text(JsonNode node, String... fields) {
        if (node == null || fields == null) return null;
        for (String field : fields) {
            if (field == null || !node.has(field)) continue;
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    public static String firstText(JsonNode node, String... fields) {
        return text(node, fields);
    }

    public static boolean readBoolean(JsonNode node, boolean defaultValue, String... fields) {
        if (node == null) return defaultValue;
        for (String field : fields) {
            if (field == null || !node.has(field)) continue;
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isBoolean()) {
                    return value.asBoolean();
                }
                if (value.isTextual()) {
                    String text = value.asText().trim().toLowerCase(Locale.ROOT);
                    if (text.equals("true") || text.equals("yes") || text.equals("1")) {
                        return true;
                    }
                    if (text.equals("false") || text.equals("no") || text.equals("0")) {
                        return false;
                    }
                }
            }
        }
        return defaultValue;
    }

    public static int readDimension(JsonNode node, int defaultValue, String... fields) {
        if (node == null) return defaultValue;
        for (String field : fields) {
            if (field == null || !node.has(field)) continue;
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) {
                    return Math.max(1, value.asInt(defaultValue));
                }
                if (value.isTextual()) {
                    try {
                        return Math.max(1, Integer.parseInt(value.asText().trim()));
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return defaultValue;
    }

    public static int[] readSize(JsonNode node) {
        if (node == null) return null;
        if (node.isArray() && node.size() >= 2) {
            try {
                int w = Math.max(1, node.get(0).asInt());
                int h = Math.max(1, node.get(1).asInt());
                return new int[] { w, h };
            } catch (Exception ignored) {
            }
        }
        if (node.isNumber()) {
            int size = Math.max(1, node.asInt());
            return new int[] { size, size };
        }
        if (node.isTextual()) {
            return parseSizeFromName(node.asText());
        }
        return null;
    }

    public static int[] parseSizeFromName(String baseName) {
        return AssetCatalogSupport.parseSizeString(baseName);
    }

    public static Path resolveRelativePath(String relative, Path baseDir) {
        if (relative == null || relative.isBlank()) return null;
        Path path = Path.of(relative);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        if (baseDir != null) {
            return baseDir.resolve(path).normalize();
        }
        return path.normalize();
    }

    public static void collectNames(JsonNode node, Map<String, String> names) {
        if (node == null || names == null) return;
        if (node.isObject()) {
            String image = text(node, "image", "imageUrl", "url", "path");
            String name = text(node, "name", "title", "label");
            if (image != null && name != null) {
                names.putIfAbsent(image, name);
            }
            node.fields().forEachRemaining(entry -> collectNames(entry.getValue(), names));
        } else if (node.isArray()) {
            for (JsonNode element : node) {
                collectNames(element, names);
            }
        }
    }

    public static boolean looksLikeAssetNode(JsonNode node) {
        if (node == null || !node.isObject()) return false;
        return node.has("image") || node.has("imageUrl") || node.has("url") || node.has("path")
                || node.has("name") || node.has("title") || node.has("label");
    }

    public static boolean looksLikeRenderableImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return false;
        String lower = imageUrl.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".gif");
    }
}
