package com.avalon.dnd.shared.uploads;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class AssetCatalogParsingSupport {

    private AssetCatalogParsingSupport() {
    }

    public static String parseKindText(String probe) {
        String lower = probe == null ? "" : probe.toLowerCase(Locale.ROOT);
        if (lower.contains("token") || lower.contains("hero") || lower.contains("npc") || lower.contains("player")) return "TOKEN";
        if (lower.contains("spawn")) return "SPAWN";
        if (lower.contains("door") || lower.contains("hatch")) return "DOOR";
        if (lower.contains("wall") || lower.contains("fence") || lower.contains("rampart") || lower.contains("barrier")) return "WALL";
        return "OBJECT";
    }

    public static String parseKindText(JsonNode node, String category, String name, String imageUrl) {
        String kindText = firstText(node, "kind", "type", "placementKind");
        if (kindText != null) {
            try {
                return kindText.trim().toUpperCase(Locale.ROOT);
            } catch (Exception ignored) {
            }
        }
        String probe = (category == null ? "" : category + " ") + (name == null ? "" : name + " ") + (imageUrl == null ? "" : imageUrl);
        return parseKindText(probe);
    }

    public static int readInt(JsonNode node, int defaultValue, String... fields) {
        if (node == null || fields == null || fields.length == 0) {
            return defaultValue;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asInt(defaultValue);
            }
            if (value.isTextual()) {
                try {
                    return Integer.parseInt(value.asText().trim());
                } catch (Exception ignored) {
                }
            }
            try {
                return value.asInt(defaultValue);
            } catch (Exception ignored) {
            }
        }
        return defaultValue;
    }

    public static int[] parseSizeFromName(String baseName) {
        int[] parsed = AssetCatalogSupport.parseSizeString(baseName);
        if (parsed != null) {
            return parsed;
        }
        return new int[] { 1, 1 };
    }

    public static String sizeLabelFor(String kindText, int width, int height) {
        if (isTokenKind(kindText)) {
            int size = Math.max(width, height);
            return switch (size) {
                case 1 -> "tiny";
                case 2 -> "small";
                case 3, 4 -> "medium";
                case 5 -> "large";
                default -> size >= 6 ? "huge" : Integer.toString(size);
            };
        }
        return width + "x" + height;
    }

    public static boolean isTokenKind(String kindText) {
        if (kindText == null) {
            return false;
        }
        String normalized = kindText.trim().toUpperCase(Locale.ROOT);
        return "TOKEN".equals(normalized) || "SPAWN".equals(normalized);
    }

    public static boolean isTokenLike(JsonNode node) {
        String probe = (firstText(node, "kind", "type", "placementKind", "category") + " "
                + firstText(node, "name", "title", "displayName", "label", "ru") + " "
                + firstText(node, "imageUrl", "image", "path", "file", "src", "url")).toLowerCase(Locale.ROOT);
        return probe.contains("token") || probe.contains("hero") || probe.contains("npc") || probe.contains("player")
                || probe.contains("creature") || probe.contains("spawn");
    }

    public static int[] inferVisionFromPath(Path root, String imageUrl, boolean tokenLike) {
        if (!tokenLike) {
            return new int[] { 0, 0 };
        }
        String probe = ((root == null ? "" : root.toString()) + " " + (imageUrl == null ? "" : imageUrl))
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
        boolean players = probe.contains("/players/") || probe.endsWith("/players") || probe.contains("player");
        boolean creatures = probe.contains("/creatures/") || probe.endsWith("/creatures") || probe.contains("creature");
        boolean npc = probe.contains("/npc/") || probe.endsWith("/npc") || probe.contains("npc");
        boolean huge = probe.contains("/huge/") || probe.endsWith("/huge") || probe.contains("huge");
        boolean large = probe.contains("/large/") || probe.endsWith("/large") || probe.contains("large");
        boolean medium = probe.contains("/medium/") || probe.endsWith("/medium") || probe.contains("medium");
        boolean small = probe.contains("/small/") || probe.endsWith("/small") || probe.contains("small");

        if (npc) return new int[] { 6, 3 };
        if (huge) return players ? new int[] { 12, 6 } : new int[] { 10, 5 };
        if (large) return players ? new int[] { 10, 5 } : new int[] { 8, 4 };
        if (medium) return players ? new int[] { 8, 4 } : new int[] { 6, 3 };
        if (small) return players ? new int[] { 8, 4 } : new int[] { 6, 3 };
        if (players) return new int[] { 8, 4 };
        if (creatures) return new int[] { 6, 3 };
        return new int[] { 6, 3 };
    }

    public static int[] inferVisionFromPath(Path root, String imageUrl, String kindText) {
        return inferVisionFromPath(root, imageUrl, isTokenKind(kindText));
    }

    public static String resolveName(String explicitName, String id, String imageUrl, Map<String, String> names) {
        if (explicitName != null && !explicitName.isBlank()) {
            return explicitName;
        }
        if (names == null || names.isEmpty()) {
            return null;
        }
        for (String key : nameLookupKeys(id, imageUrl)) {
            String normalized = AssetCatalogSupport.normalizeKey(key);
            String value = names.get(normalized);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    public static boolean looksLikeNamesMap(JsonNode node) {
        int stringValues = 0;
        int total = 0;
        for (var it = node.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            total++;
            if (entry.getValue() != null && entry.getValue().isTextual()) {
                stringValues++;
            }
        }
        return total > 0 && stringValues * 2 >= total;
    }

    public static String normalizeCategoryPath(String category) {
        if (category == null) return null;
        return category.replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    public static String signature(JsonNode node) {
        if (node == null || !node.isObject()) return String.valueOf(node);
        String id = firstText(node, "id", "assetId", "key", "name", "filename", "path", "url");
        String url = firstText(node, "imageUrl", "image", "path", "file", "src", "url");
        return (id == null ? "" : id) + "|" + (url == null ? "" : url);
    }

    public static boolean hasAnyAssets(JsonNode root) {
        return root != null
                && ((root.has("tokens") && root.get("tokens").isArray() && root.get("tokens").size() > 0)
                || (root.has("objects") && root.get("objects").isArray() && root.get("objects").size() > 0));
    }

    public static String toWebUrl(Path file) {
        if (file == null) {
            return null;
        }
        Path normalized = file.toAbsolutePath().normalize();
        for (int i = 0; i < normalized.getNameCount(); i++) {
            if ("uploads".equalsIgnoreCase(normalized.getName(i).toString())) {
                Path relative = normalized.subpath(i, normalized.getNameCount());
                return "/" + relative.toString().replace('\\', '/');
            }
        }
        return normalized.toUri().toString();
    }

    private static List<String> nameLookupKeys(String id, String imageUrl) {
        List<String> keys = new ArrayList<>();
        if (id != null && !id.isBlank()) {
            keys.add(id);
            keys.add(AssetCatalogSupport.stripExtension(AssetCatalogSupport.lastPathSegment(id)));
        }
        if (imageUrl != null && !imageUrl.isBlank()) {
            keys.add(AssetCatalogSupport.lastPathSegment(imageUrl));
            keys.add(AssetCatalogSupport.stripExtension(AssetCatalogSupport.lastPathSegment(imageUrl)));
            String normalized = imageUrl.replace('\\', '/');
            keys.add(normalized);
            keys.add(AssetCatalogSupport.stripExtension(normalized));
        }
        return keys;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }
}
