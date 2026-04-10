package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.AssetCatalog;
import com.avalon.dnd.mapeditor.model.AssetDefinition;
import com.avalon.dnd.mapeditor.model.PlacementKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class AssetCatalogLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SIZE_PATTERN = Pattern.compile(".*\\((\\d+)x(\\d+)\\).*", Pattern.CASE_INSENSITIVE);

    private AssetCatalogLoader() {}

    public static AssetCatalog loadDefault() {
        String configured = System.getProperty("avalon.assets.dir");
        if (configured != null && !configured.isBlank()) {
            AssetCatalog catalog = tryLoad(Path.of(configured));
            if (!catalog.getAssets().isEmpty()) return catalog;
        }

        List<Path> candidates = List.of(
                Path.of("uploads/assets/catalog.json"),
                Path.of("map-editor/assets/catalog.json"),
                Path.of("assets/catalog.json"),
                Path.of("CastleWalls.zip"),
                Path.of("CastleWalls"),
                Path.of("CastleWalls/Highres"),
                Path.of("CastleWalls/Roll20")
        );

        for (Path candidate : candidates) {
            AssetCatalog catalog = tryLoad(candidate);
            if (!catalog.getAssets().isEmpty()) return catalog;
        }

        AssetCatalog fallback = new AssetCatalog();
        fallback.add(new AssetDefinition("sample-wall", "Sample Wall", "walls", null, 1, 1, true, true, PlacementKind.WALL));
        fallback.add(new AssetDefinition("sample-door", "Sample Door", "doors", null, 2, 1, true, true, PlacementKind.DOOR));
        fallback.add(new AssetDefinition("sample-token", "Sample Token", "tokens", null, 1, 1, false, false, PlacementKind.TOKEN));
        return fallback;
    }

    private static AssetCatalog tryLoad(Path path) {
        try {
            if (Files.isDirectory(path)) {
                return scanDirectory(path);
            }
            String filename = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (Files.isRegularFile(path) && filename.endsWith(".json")) {
                return loadFromJson(path);
            }
            if (Files.isRegularFile(path) && filename.endsWith(".zip")) {
                return scanZip(path);
            }
        } catch (Exception ignored) {
        }
        return new AssetCatalog();
    }

    public static AssetCatalog loadFromJson(Path jsonPath) throws IOException {
        JsonNode root = MAPPER.readTree(Files.newInputStream(jsonPath));
        AssetCatalog catalog = new AssetCatalog();

        JsonNode assets = root.path("assets");
        if (assets.isArray()) {
            for (JsonNode node : assets) {
                catalog.add(readAsset(node, jsonPath.getParent()));
            }
        }

        return catalog;
    }

    public static AssetCatalog scanDirectory(Path root) {
        AssetCatalog catalog = new AssetCatalog();
        if (root == null || !Files.exists(root)) return catalog;

        try {
            List<Path> images = new ArrayList<>();
            Files.walk(root)
                    .filter(Files::isRegularFile)
                    .filter(AssetCatalogLoader::isImageFile)
                    .forEach(images::add);

            images.sort(Comparator.comparing(Path::toString));

            for (Path image : images) {
                catalog.add(fromImageFile(root, image, toRelativeUrl(root, image)));
            }
        } catch (IOException ignored) {
        }

        return catalog;
    }

    public static AssetCatalog scanZip(Path zipPath) {
        AssetCatalog catalog = new AssetCatalog();
        if (zipPath == null || !Files.isRegularFile(zipPath)) return catalog;

        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zipPath.toUri()), Map.of())) {
            List<Path> images = new ArrayList<>();
            for (Path root : fs.getRootDirectories()) {
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                            .filter(AssetCatalogLoader::isImageFile)
                            .forEach(images::add);
                }
            }

            images.sort(Comparator.comparing(Path::toString));
            for (Path image : images) {
                String rel = image.toString().replace('\\', '/');
                catalog.add(fromImageFile(zipPath, image, toJarUrl(zipPath, rel)));
            }
        } catch (Exception ignored) {
        }

        return catalog;
    }

    private static boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
    }

    private static AssetDefinition fromImageFile(Path root, Path image, String imageUrl) {
        String fileName = image.getFileName().toString();
        String baseName = stripExtension(fileName);
        int[] size = parseSizeFromName(baseName);
        String category = deriveCategory(root, image);
        String id = toId(category + "-" + baseName);
        PlacementKind kind = inferKind(baseName, category);
        boolean blocksMovement = kind == PlacementKind.WALL || kind == PlacementKind.DOOR
                || containsAny(baseName, "wall", "fence", "rampart", "door", "hatch");
        boolean blocksSight = blocksMovement && !containsAny(baseName, "window", "arrowslit");

        return new AssetDefinition(
                id,
                baseName,
                category,
                imageUrl,
                size[0],
                size[1],
                blocksMovement,
                blocksSight,
                kind
        );
    }

    private static AssetDefinition readAsset(JsonNode node, Path baseDir) {
        String id = text(node, "id");
        String name = text(node, "name");
        String category = text(node, "category");
        String imageUrl = text(node, "imageUrl");
        int width = node.path("width").asInt(1);
        int height = node.path("height").asInt(1);
        boolean blocksMovement = node.path("blocksMovement").asBoolean(false);
        boolean blocksSight = node.path("blocksSight").asBoolean(false);
        PlacementKind kind = PlacementKind.valueOf(node.path("kind").asText("OBJECT").toUpperCase(Locale.ROOT));

        if (imageUrl != null && baseDir != null && !imageUrl.startsWith("/")) {
            imageUrl = baseDir.resolve(imageUrl).toString();
        }

        return new AssetDefinition(id, name, category, imageUrl, width, height, blocksMovement, blocksSight, kind);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static int[] parseSizeFromName(String baseName) {
        Matcher matcher = SIZE_PATTERN.matcher(baseName);
        if (matcher.matches()) {
            try {
                return new int[] {
                        Integer.parseInt(matcher.group(1)),
                        Integer.parseInt(matcher.group(2))
                };
            } catch (NumberFormatException ignored) {
            }
        }
        return new int[] {1, 1};
    }

    private static String deriveCategory(Path root, Path image) {
        try {
            Path relative = root.relativize(image);
            if (relative.getNameCount() <= 1) return "misc";
            return relative.getName(0).toString().toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "misc";
        }
    }

    private static PlacementKind inferKind(String baseName, String category) {
        String name = baseName.toLowerCase(Locale.ROOT);
        if (name.contains("door") || name.contains("hatch")) return PlacementKind.DOOR;
        if (name.contains("wall") || name.contains("fence") || name.contains("rampart")) return PlacementKind.WALL;
        if (name.contains("tower") || name.contains("platform") || name.contains("walkway") || name.contains("stairs") || name.contains("ladder")) {
            return PlacementKind.OBJECT;
        }
        if (category.contains("token")) return PlacementKind.TOKEN;
        return PlacementKind.OBJECT;
    }

    private static boolean containsAny(String text, String... needles) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (lower.contains(needle)) return true;
        }
        return false;
    }

    private static String toRelativeUrl(Path root, Path file) {
        return file.toUri().toString();
    }

    private static String toJarUrl(Path zipPath, String entryPath) {
        String normalized = entryPath.startsWith("/") ? entryPath.substring(1) : entryPath;
        return "jar:" + zipPath.toUri() + "!/" + encodeUrlPath(normalized);
    }

    private static String encodeUrlPath(String path) {
        StringBuilder sb = new StringBuilder();
        for (char ch : path.toCharArray()) {
            if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') ||
                    (ch >= '0' && ch <= '9') || ch == '/' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append(ch);
            } else {
                sb.append(String.format("%%%02X", (int) ch));
            }
        }
        return sb.toString();
    }

    private static String toId(String source) {
        return source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
