package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.AssetCatalog;
import com.avalon.dnd.mapeditor.model.AssetDefinition;
import com.avalon.dnd.mapeditor.model.PlacementKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.avalon.dnd.shared.uploads.AssetCatalogSupport;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class AssetCatalogLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SIZE_PATTERN = Pattern.compile("(?i).*(\\d+)\\s*[x×х]\\s*(\\d+).*");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("(?i).*(\\d+).*");

    private AssetCatalogLoader() {}

    public static AssetCatalog loadDefault() {
        AssetCatalog merged = new AssetCatalog();

        String configured = System.getProperty("avalon.assets.dir");
        if (configured != null && !configured.isBlank()) {
            mergeInto(merged, tryLoad(Path.of(configured)));
        }

        for (Path candidate : resolveCandidates(List.of(
                Path.of("uploads/assets/tokens/catalog.json"),
                Path.of("uploads/assets/objects/catalog.json"),
                Path.of("uploads/assets/catalog.json"),
                Path.of("uploads/assets"),
                Path.of("uploads/maps/reference/catalog.json"),
                Path.of("uploads/maps/reference"),
                Path.of("uploads"),
                Path.of("map-editor/assets/catalog.json"),
                Path.of("assets/catalog.json"),
                Path.of("CastleWalls.zip"),
                Path.of("CastleWalls"),
                Path.of("CastleWalls/Highres"),
                Path.of("CastleWalls/Roll20")
        ))) {
            mergeInto(merged, tryLoad(candidate));
        }

        if (!merged.getAssets().isEmpty()) {
            return merged;
        }

        AssetCatalog fallback = new AssetCatalog();
        fallback.add(new AssetDefinition("sample-wall", "Sample Wall", "walls", null, 1, 1, true, true, PlacementKind.WALL));
        fallback.add(new AssetDefinition("sample-door", "Sample Door", "doors", null, 2, 1, true, true, PlacementKind.DOOR));
        fallback.add(new AssetDefinition("sample-token", "Sample Token", "tokens", null, 1, 1, false, false, PlacementKind.TOKEN));
        return fallback;
    }

    private static List<Path> resolveCandidates(List<Path> relativeCandidates) {
        java.util.LinkedHashSet<Path> resolved = new java.util.LinkedHashSet<>();
        for (Path root : projectRoots()) {
            for (Path candidate : relativeCandidates) {
                resolved.add(resolveAgainstRoot(root, candidate));
            }
        }
        for (Path candidate : relativeCandidates) {
            if (candidate.isAbsolute()) {
                resolved.add(candidate.toAbsolutePath().normalize());
            }
        }
        return new ArrayList<>(resolved);
    }

    private static List<Path> projectRoots() {
        java.util.LinkedHashSet<Path> roots = new java.util.LinkedHashSet<>();
        addRoot(roots, System.getProperty("avalon.project.root"));
        addRoot(roots, System.getenv("AVALON_PROJECT_ROOT"));

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path current = cwd;
        while (current != null) {
            if (looksLikeProjectRoot(current)) {
                roots.add(current);
            }
            current = current.getParent();
        }

        if (roots.isEmpty()) {
            roots.add(cwd);
        }
        return new ArrayList<>(roots);
    }

    private static void addRoot(java.util.Set<Path> roots, String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            Path p = Path.of(raw).toAbsolutePath().normalize();
            roots.add(p);
            Path current = p;
            while (current != null) {
                if (looksLikeProjectRoot(current)) {
                    roots.add(current);
                }
                current = current.getParent();
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean looksLikeProjectRoot(Path dir) {
        return Files.exists(dir.resolve("gradlew.bat"))
                || Files.exists(dir.resolve("settings.gradle"))
                || Files.exists(dir.resolve("build.gradle"))
                || Files.exists(dir.resolve("uploads"));
    }

    private static boolean isExcludedAssetPath(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/uploads/maps/finished/")
                || normalized.contains("/uploads/maps/backups/");
    }

    private static Path resolveAgainstRoot(Path root, Path candidate) {
        if (candidate.isAbsolute()) {
            return candidate.toAbsolutePath().normalize();
        }
        return root.resolve(candidate).toAbsolutePath().normalize();
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
        Path baseDir = jsonPath.getParent();

        Map<String, String> names = new HashMap<>();
        if (baseDir != null && Files.isDirectory(baseDir)) {
            collectNamesFromDirectory(baseDir, names);
        }
        collectNames(root, names);
        collectAssets(root, baseDir, names, catalog);
        return catalog;
    }

    public static AssetCatalog scanDirectory(Path root) {
        AssetCatalog catalog = new AssetCatalog();
        if (root == null || !Files.exists(root)) return catalog;

        try {
            Map<String, String> names = new HashMap<>();
            List<Path> manifests = new ArrayList<>();
            List<Path> images = new ArrayList<>();

            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile).forEach(path -> {
                    if (isExcludedAssetPath(path)) {
                        return;
                    }
                    String filename = path.getFileName().toString();
                    String lower = filename.toLowerCase(Locale.ROOT);
                    if (isNamesFile(filename)) {
                        readNamesFile(path, names);
                    } else if (lower.endsWith(".json")) {
                        manifests.add(path);
                    } else if (isImageFile(path)) {
                        images.add(path);
                    }
                });
            }

            manifests.sort(Comparator.comparing(Path::toString));
            for (Path manifest : manifests) {
                mergeInto(catalog, loadFromJson(manifest));
            }

            images.sort(Comparator.comparing(Path::toString));
            for (Path image : images) {
                addIfMissing(catalog, fromImageFile(root, image, toWebUrl(image), names));
            }
        } catch (IOException ignored) {
        }

        return catalog;
    }

    public static AssetCatalog scanZip(Path zipPath) {
        AssetCatalog catalog = new AssetCatalog();
        if (zipPath == null || !Files.isRegularFile(zipPath)) return catalog;

        try (java.nio.file.FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + zipPath.toUri()), Map.of())) {
            Map<String, String> names = new HashMap<>();
            List<Path> images = new ArrayList<>();
            List<Path> manifests = new ArrayList<>();
            for (Path root : fs.getRootDirectories()) {
                try (Stream<Path> walk = Files.walk(root)) {
                    walk.filter(Files::isRegularFile)
                            .forEach(path -> {
                                String filename = path.getFileName().toString();
                                String lower = filename.toLowerCase(Locale.ROOT);
                                if (isNamesFile(filename)) {
                                    readNamesFile(path, names);
                                } else if (lower.endsWith(".json")) {
                                    manifests.add(path);
                                } else if (isImageFile(path)) {
                                    images.add(path);
                                }
                            });
                }
            }

            manifests.sort(Comparator.comparing(Path::toString));
            for (Path manifest : manifests) {
                mergeInto(catalog, loadFromJson(manifest));
            }

            images.sort(Comparator.comparing(Path::toString));
            for (Path image : images) {
                String rel = image.toString().replace('\\', '/');
                catalog.add(fromImageFile(zipPath, image, toJarUrl(zipPath, rel), names));
            }
        } catch (Exception ignored) {
        }

        return catalog;
    }

    private static boolean isImageFile(Path path) {
        return AssetCatalogSupport.isImageFile(path);
    }

    private static boolean isNamesFile(String filename) {
        return AssetCatalogSupport.isNamesFile(filename);
    }

    private static AssetDefinition fromImageFile(Path root, Path image, String imageUrl, Map<String, String> names) {
        String fileName = image.getFileName().toString();
        String baseName = stripExtension(fileName);
        int[] size = parseSizeFromName(baseName);
        int inferred = inferGridSizeFromPath(root, imageUrl);
        if (inferred > 0 && size[0] == 1 && size[1] == 1) {
            size = new int[] { inferred, inferred };
        }
        String category = deriveCategory(root, imageUrl);
        PlacementKind kind = inferKind(baseName, category);
        boolean blocksMovement = kind == PlacementKind.WALL || kind == PlacementKind.DOOR
                || containsAny(baseName, "wall", "fence", "rampart", "door", "hatch");
        boolean blocksSight = blocksMovement && !containsAny(baseName, "window", "arrowslit");
        String name = resolveName(null, baseName, imageUrl, names);
        if (name == null) {
            name = humanize(baseName);
        }
        String id = toId((category == null ? "asset" : category) + "-" + baseName);
        return new AssetDefinition(id, name, category, imageUrl, size[0], size[1], blocksMovement, blocksSight, kind);
    }

    private static String toWebUrl(Path file) {
        return localToWebUrl(file);
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null) return null;
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private static void readNamesFile(Path path, Map<String, String> names) {
        try {
            JsonNode root = MAPPER.readTree(Files.newInputStream(path));
            collectNames(root, names);
        } catch (Exception ignored) {
        }
    }

    private static void mergeInto(AssetCatalog target, AssetCatalog source) {
        if (target == null || source == null) return;
        for (AssetDefinition asset : source.getAssets()) {
            addIfMissing(target, asset);
        }
    }

    private static void addIfMissing(AssetCatalog catalog, AssetDefinition asset) {
        if (catalog == null || asset == null) return;
        String assetId = normalizeKey(asset.getId());
        String assetUrl = normalizeKey(asset.getImageUrl());
        String assetName = normalizeKey(asset.getName());
        for (AssetDefinition existing : catalog.getAssets()) {
            if (!assetId.isBlank() && assetId.equals(normalizeKey(existing.getId()))) return;
            if (!assetUrl.isBlank() && assetUrl.equals(normalizeKey(existing.getImageUrl()))) return;
            if (!assetName.isBlank() && assetName.equals(normalizeKey(existing.getName()))) return;
        }
        catalog.add(asset);
    }

    private static void collectNamesFromDirectory(Path dir, Map<String, String> names) {
        try {
            Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .filter(path -> isNamesFile(path.getFileName().toString()))
                    .forEach(path -> readNamesFile(path, names));
        } catch (IOException ignored) {
        }
    }

    private static void collectNames(JsonNode node, Map<String, String> names) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            if (looksLikeNamesMap(node)) {
                node.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    if (value != null && value.isTextual()) {
                        names.putIfAbsent(normalizeKey(entry.getKey()), value.asText());
                    } else if (value != null && value.isObject()) {
                        String key = text(value, "id", "assetId", "key", "name", "file", "filename");
                        String name = text(value, "name", "title", "displayName", "label", "ru");
                        if (key != null && name != null) {
                            names.putIfAbsent(normalizeKey(key), name);
                        }
                    }
                });
            }
            node.fields().forEachRemaining(entry -> collectNames(entry.getValue(), names));
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                collectNames(item, names);
            }
        }
    }

    private static boolean looksLikeNamesMap(JsonNode node) {
        int stringValues = 0;
        int total = 0;
        for (var it = node.fields(); it.hasNext(); ) {
            Map.Entry<String, JsonNode> entry = it.next();
            total++;
            if (entry.getValue() != null && entry.getValue().isTextual()) {
                stringValues++;
            }
        }
        return total > 0 && stringValues >= Math.max(1, total / 2);
    }

    private static void collectAssets(JsonNode node, Path baseDir, Map<String, String> names, AssetCatalog catalog) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectAssets(item, baseDir, names, catalog);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if (looksLikeAssetNode(node)) {
            AssetDefinition asset = readAsset(node, baseDir, names);
            if (asset != null) {
                catalog.add(asset);
            }
            return;
        }

        node.fields().forEachRemaining(entry -> collectAssets(entry.getValue(), baseDir, names, catalog));
    }

    private static boolean looksLikeAssetNode(JsonNode node) {
        boolean hasImage = node.hasNonNull("imageUrl") || node.hasNonNull("image") || node.hasNonNull("path") || node.hasNonNull("file")
                || node.hasNonNull("src") || node.hasNonNull("url") || node.hasNonNull("filename") || node.hasNonNull("fileName")
                || node.hasNonNull("imagePath") || node.hasNonNull("assetPath") || node.hasNonNull("sprite") || node.hasNonNull("thumbnail");
        boolean hasSize = node.hasNonNull("width") || node.hasNonNull("height") || node.hasNonNull("gridSize")
                || node.hasNonNull("defaultWidth") || node.hasNonNull("defaultHeight")
                || node.hasNonNull("size") || node.hasNonNull("dimensions");
        boolean hasBehavior = node.hasNonNull("kind") || node.hasNonNull("blocksMovement") || node.hasNonNull("blocksSight")
                || node.hasNonNull("movementBlock") || node.hasNonNull("visionBlock") || node.hasNonNull("solid") || node.hasNonNull("opaque")
                || node.hasNonNull("category");
        return hasImage || hasSize || hasBehavior;
    }

    private static AssetDefinition readAsset(JsonNode node, Path baseDir, Map<String, String> names) {
        String rawId = firstText(node, "id", "assetId", "key", "slug", "name", "filename", "fileName");
        String rawName = firstText(node, "name", "title", "displayName", "label", "ru", "caption");
        String category = firstText(node, "category", "type", "group", "folder", "pack");
        String imageUrl = firstText(node, "imageUrl", "image", "path", "file", "src", "url", "filename", "fileName", "imagePath", "assetPath", "sprite", "thumbnail");
        PlacementKind kind = parseKind(node, category, rawName, imageUrl);

        int width = readDimension(node, 1, "width", "w", "sizeX", "gridWidth", "tileWidth", "cellWidth");
        int height = readDimension(node, 1, "height", "h", "sizeY", "gridHeight", "tileHeight", "cellHeight");
        int[] size = readSize(node);
        if (size == null && (kind == PlacementKind.TOKEN || kind == PlacementKind.SPAWN)) {
            int inferred = inferGridSizeFromPath(baseDir, imageUrl);
            if (inferred > 0) {
                size = new int[] { inferred, inferred };
            }
        }
        if (size != null) {
            width = size[0];
            height = size[1];
        }

        if ((kind == PlacementKind.TOKEN || kind == PlacementKind.SPAWN) && width == 1 && height == 1) {
            int gridSize = Math.max(readDimension(node, 1, "gridSize", "grid", "size"), Math.max(width, height));
            int inferred = inferGridSizeFromPath(baseDir, imageUrl);
            if (gridSize <= 1 && inferred > 1) {
                gridSize = inferred;
            }
            width = gridSize;
            height = gridSize;
        }

        boolean blocksMovement = readBoolean(node, false, "blocksMovement", "blocksMove", "movementBlock", "solid");
        boolean blocksSight = readBoolean(node, blocksMovement, "blocksSight", "blocksVision", "visionBlock", "opaque");

        if (imageUrl != null) {
            imageUrl = normalizeImageUrl(imageUrl, baseDir);
        }

        String resolvedName = resolveName(rawName, rawId, imageUrl, names);
        if (resolvedName == null) {
            resolvedName = humanize(rawId != null ? rawId : stripExtension(lastPathSegment(imageUrl)));
        }

        String id = rawId != null ? toId(rawId) : toId((resolvedName != null ? resolvedName : "asset") + "-" + lastPathSegment(imageUrl));
        if (id.isBlank()) {
            id = toId(lastPathSegment(imageUrl));
        }

        if (category == null || category.isBlank()) {
            category = deriveCategory(baseDir, imageUrl);
        }

        if (!blocksMovement && (kind == PlacementKind.WALL || kind == PlacementKind.DOOR)) {
            blocksMovement = true;
        }
        if (!blocksSight && blocksMovement && kind != PlacementKind.OBJECT) {
            blocksSight = true;
        }

        return new AssetDefinition(id, resolvedName, category, imageUrl, width, height, blocksMovement, blocksSight, kind);
    }

    private static String normalizeImageUrl(String imageUrl, Path baseDir) {
        return AssetCatalogSupport.normalizeImageUrl(imageUrl, baseDir);
    }

    private static Path resolveRelativePath(String relative, Path baseDir) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        String cleaned = relative.startsWith("/") ? relative.substring(1) : relative;
        List<Path> candidates = new ArrayList<>();
        if (baseDir != null) {
            candidates.add(baseDir);
            Path current = baseDir;
            for (int i = 0; i < 4 && current != null; i++) {
                current = current.getParent();
                if (current != null) {
                    candidates.add(current);
                }
            }
        }
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        candidates.add(cwd);
        Path current = cwd;
        for (int i = 0; i < 4 && current != null; i++) {
            current = current.getParent();
            if (current != null) {
                candidates.add(current);
            }
        }

        for (Path base : candidates) {
            Path candidate = base.resolve(cleaned).normalize();
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        Path direct = Paths.get(cleaned);
        if (Files.exists(direct)) {
            return direct.toAbsolutePath().normalize();
        }
        return null;
    }

    private static String localToWebUrl(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        for (int i = 0; i < normalized.getNameCount(); i++) {
            if ("uploads".equalsIgnoreCase(normalized.getName(i).toString())) {
                Path relative = normalized.subpath(i, normalized.getNameCount());
                return "/" + relative.toString().replace('\\', '/');
            }
        }
        return normalized.toUri().toString();
    }

    private static String resolveName(String explicitName, String id, String imageUrl, Map<String, String> names) {
        if (explicitName != null && !explicitName.isBlank()) {
            return explicitName;
        }
        for (String key : nameLookupKeys(id, imageUrl)) {
            String normalized = normalizeKey(key);
            String value = names.get(normalized);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<String> nameLookupKeys(String id, String imageUrl) {
        List<String> keys = new ArrayList<>();
        if (id != null && !id.isBlank()) {
            keys.add(id);
            keys.add(stripExtension(lastPathSegment(id)));
        }
        if (imageUrl != null && !imageUrl.isBlank()) {
            keys.add(lastPathSegment(imageUrl));
            keys.add(stripExtension(lastPathSegment(imageUrl)));
            String normalized = imageUrl.replace('\\', '/');
            keys.add(normalized);
            keys.add(stripExtension(normalized));
        }
        return keys;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (text != null && !text.isBlank()) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private static boolean readBoolean(JsonNode node, boolean defaultValue, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isBoolean()) {
                    return value.asBoolean();
                }
                return Boolean.parseBoolean(value.asText());
            }
        }
        return defaultValue;
    }

    private static int readDimension(JsonNode node, int defaultValue, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) {
                    return Math.max(1, value.asInt());
                }
                try {
                    return Math.max(1, Integer.parseInt(value.asText().trim()));
                } catch (Exception ignored) {
                }
            }
        }
        return defaultValue;
    }

    private static int[] readSize(JsonNode node) {
        JsonNode value = node.get("size");
        if (value != null) {
            if (value.isTextual()) {
                int[] parsed = parseSizeString(value.asText());
                if (parsed != null) return parsed;
            }
            if (value.isArray() && value.size() >= 2) {
                int w = Math.max(1, value.get(0).asInt(1));
                int h = Math.max(1, value.get(1).asInt(1));
                return new int[] { w, h };
            }
            if (value.isObject()) {
                int w = readDimension(value, 1, "width", "w", "x");
                int h = readDimension(value, 1, "height", "h", "y");
                if (w > 0 || h > 0) {
                    return new int[] { Math.max(1, w), Math.max(1, h) };
                }
            }
        }

        JsonNode dimensions = node.get("dimensions");
        if (dimensions != null) {
            if (dimensions.isArray() && dimensions.size() >= 2) {
                int w = Math.max(1, dimensions.get(0).asInt(1));
                int h = Math.max(1, dimensions.get(1).asInt(1));
                return new int[] { w, h };
            }
            if (dimensions.isObject()) {
                int w = readDimension(dimensions, 1, "width", "w", "x");
                int h = readDimension(dimensions, 1, "height", "h", "y");
                if (w > 0 || h > 0) {
                    return new int[] { Math.max(1, w), Math.max(1, h) };
                }
            }
        }

        JsonNode grid = node.get("gridSize");
        if (grid != null) {
            if (grid.isTextual()) {
                int[] parsed = parseSizeString(grid.asText());
                if (parsed != null) return parsed;
            }
            if (grid.isArray() && grid.size() >= 2) {
                int w = Math.max(1, grid.get(0).asInt(1));
                int h = Math.max(1, grid.get(1).asInt(1));
                return new int[] { w, h };
            }
            if (grid.isObject()) {
                int w = readDimension(grid, 1, "width", "w", "x");
                int h = readDimension(grid, 1, "height", "h", "y");
                if (w > 0 || h > 0) {
                    return new int[] { Math.max(1, w), Math.max(1, h) };
                }
            }
        }

        String gridSize = firstText(node, "gridSize", "grid", "tileSize");
        if (gridSize != null) {
            int[] parsed = parseSizeString(gridSize);
            if (parsed != null) return parsed;
            try {
                int size = Math.max(1, Integer.parseInt(gridSize.trim()));
                return new int[] { size, size };
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private static int[] parseSizeFromName(String baseName) {
        int[] parsed = parseSizeString(baseName);
        return parsed != null ? parsed : new int[] {1, 1};
    }

    private static int[] parseSizeString(String text) {
        if (text == null) return null;
        Matcher matcher = SIZE_PATTERN.matcher(text.trim());
        if (matcher.matches()) {
            try {
                return new int[] { Math.max(1, Integer.parseInt(matcher.group(1))), Math.max(1, Integer.parseInt(matcher.group(2))) };
            } catch (Exception ignored) {
            }
        }
        Matcher digits = DIGITS_PATTERN.matcher(text.trim());
        if (digits.matches()) {
            try {
                int size = Math.max(1, Integer.parseInt(digits.group(1)));
                return new int[] { size, size };
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static PlacementKind parseKind(JsonNode node, String category, String name, String imageUrl) {
        String kindText = firstText(node, "kind", "type", "placementKind");
        if (kindText != null) {
            try {
                return PlacementKind.valueOf(kindText.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
            }
        }

        String probe = (category == null ? "" : category + " ") + (name == null ? "" : name + " ") + (imageUrl == null ? "" : imageUrl);
        String lower = probe.toLowerCase(Locale.ROOT);
        if (lower.contains("token") || lower.contains("hero") || lower.contains("npc") || lower.contains("player")) return PlacementKind.TOKEN;
        if (lower.contains("spawn")) return PlacementKind.SPAWN;
        if (lower.contains("door") || lower.contains("hatch")) return PlacementKind.DOOR;
        if (lower.contains("wall") || lower.contains("fence") || lower.contains("rampart") || lower.contains("barrier")) return PlacementKind.WALL;
        return PlacementKind.OBJECT;
    }

    private static int inferGridSizeFromPath(Path baseDir, String imageUrl) {
        return AssetCatalogSupport.inferGridSizeFromPath(baseDir, imageUrl);
    }

    private static String deriveCategory(Path baseDir, String imageUrl) {
        return AssetCatalogSupport.deriveCategory(baseDir, imageUrl);
    }

    private static PlacementKind inferKind(String baseName, String category) {
        String lower = (baseName == null ? "" : baseName.toLowerCase(Locale.ROOT)) + " " + (category == null ? "" : category.toLowerCase(Locale.ROOT));
        if (lower.contains("door") || lower.contains("hatch")) return PlacementKind.DOOR;
        if (lower.contains("wall") || lower.contains("fence") || lower.contains("rampart") || lower.contains("barrier")) return PlacementKind.WALL;
        if (lower.contains("spawn")) return PlacementKind.SPAWN;
        if (lower.contains("token") || lower.contains("hero") || lower.contains("npc") || lower.contains("player") || lower.contains("creature")) return PlacementKind.TOKEN;
        return PlacementKind.OBJECT;
    }

    private static boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (lower.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String stripExtension(String fileName) {
        return AssetCatalogSupport.stripExtension(fileName);
    }

    private static String lastPathSegment(String value) {
        return AssetCatalogSupport.lastPathSegment(value);
    }

    private static String humanize(String text) {
        return AssetCatalogSupport.humanize(text);
    }

    private static String normalizeKey(String source) {
        return AssetCatalogSupport.normalizeKey(source);
    }

    private static String toId(String source) {
        return AssetCatalogSupport.toId(source);
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
}
