package com.avalon.dnd.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/assets")
public class AssetCatalogController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern SIZE_PATTERN = Pattern.compile("(?i).*(\\d+)\\s*[x×х]\\s*(\\d+).*");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("(?i).*(\\d+).*");

    private enum PlacementKind {
        OBJECT,
        TOKEN,
        DECOR,
        WALL,
        DOOR,
        SPAWN
    }

    @GetMapping("/catalog")
    public JsonNode getCatalog() {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode tokens = MAPPER.createArrayNode();
        ArrayNode objects = MAPPER.createArrayNode();
        root.set("tokens", tokens);
        root.set("objects", objects);

        try {
            for (Path candidate : resolveCandidates(List.of(
                    Path.of("uploads/assets/tokens/catalog.json"),
                    Path.of("uploads/assets/objects/catalog.json"),
                    Path.of("uploads/assets/catalog.json"),
                    Path.of("uploads/assets/tokens"),
                    Path.of("uploads/assets/objects"),
                    Path.of("uploads/assets"),
                    Path.of("uploads/maps/reference/catalog.json"),
                    Path.of("uploads/maps/reference"),
                    Path.of("uploads")
            ))) {
                if (Files.isDirectory(candidate)) {
                    scanDirectory(candidate, root, tokens, objects);
                } else if (Files.isRegularFile(candidate) && candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
                    scanJsonFile(candidate, root, tokens, objects);
                }
            }

            if (!tokens.isEmpty() || !objects.isEmpty()) {
                return root;
            }

            ClassPathResource res = new ClassPathResource("assets/catalog.json");
            try (InputStream is = res.getInputStream()) {
                JsonNode node = MAPPER.readTree(is);
                collectCatalogNodes(node, tokens, objects, new HashSet<>());
            }
        } catch (Exception ignored) {
        }

        return root;
    }

    private void scanDirectory(Path root, ObjectNode out, ArrayNode tokens, ArrayNode objects) throws IOException {
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
        Set<String> seen = new HashSet<>();
        for (JsonNode node : tokens) seen.add(signature(node));
        for (JsonNode node : objects) seen.add(signature(node));

        for (Path manifest : manifests) {
            scanJsonFile(manifest, out, tokens, objects, seen);
        }

        images.sort(Comparator.comparing(Path::toString));
        for (Path image : images) {
            addSyntheticAsset(root, image, toWebUrl(image), names, tokens, objects, seen);
        }
    }

    private void scanJsonFile(Path jsonPath, ObjectNode out, ArrayNode tokens, ArrayNode objects) throws IOException {
        scanJsonFile(jsonPath, out, tokens, objects, new HashSet<>());
    }

    private void scanJsonFile(Path jsonPath, ObjectNode out, ArrayNode tokens, ArrayNode objects, Set<String> seen) throws IOException {
        Map<String, String> names = new HashMap<>();
        Path baseDir = jsonPath.getParent();
        if (baseDir != null && Files.isDirectory(baseDir)) {
            try (Stream<Path> walk = Files.walk(baseDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> isNamesFile(path.getFileName().toString()))
                        .forEach(path -> readNamesFile(path, names));
            }
        }

        try (InputStream is = Files.newInputStream(jsonPath)) {
            JsonNode root = MAPPER.readTree(is);
            collectCatalogNodes(root, baseDir, tokens, objects, seen);
            if (root != null && root.isObject()) {
                root.fields().forEachRemaining(entry -> collectCatalogNodes(entry.getValue(), baseDir, tokens, objects, seen));
            }
        }
    }

    private void collectCatalogNodes(JsonNode node, ArrayNode tokens, ArrayNode objects, Set<String> seen) {
        collectCatalogNodes(node, null, tokens, objects, seen);
    }

    private void collectCatalogNodes(JsonNode node, Path baseDir, ArrayNode tokens, ArrayNode objects, Set<String> seen) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectCatalogNodes(item, baseDir, tokens, objects, seen);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if (looksLikeAssetNode(node)) {
            ObjectNode normalized = normalizeAssetNode(node, baseDir);
            String sig = signature(normalized);
            if (seen.add(sig)) {
                if (isTokenLike(normalized)) {
                    tokens.add(normalized);
                } else {
                    objects.add(normalized);
                }
            }
        }

        node.fields().forEachRemaining(entry -> collectCatalogNodes(entry.getValue(), baseDir, tokens, objects, seen));
    }

    private ObjectNode normalizeAssetNode(JsonNode node, Path baseDir) {
        ObjectNode out = MAPPER.createObjectNode();
        String rawId = text(node, "id", "assetId", "key", "slug", "name", "filename", "fileName");
        String rawName = text(node, "name", "title", "displayName", "label", "ru", "caption");
        String category = text(node, "category", "type", "group", "folder", "pack");
        String imageUrl = text(node, "imageUrl", "image", "path", "file", "src", "url", "filename", "fileName", "imagePath", "assetPath", "sprite", "thumbnail");
        String kindText = text(node, "kind", "type", "placementKind");
        String lower = ((kindText == null ? "" : kindText) + " " + (category == null ? "" : category) + " " + (rawName == null ? "" : rawName) + " " + (imageUrl == null ? "" : imageUrl)).toLowerCase(Locale.ROOT);
        PlacementKind kind = parseKind(lower);

        int width = readDimension(node, 1, "width", "w", "sizeX", "gridWidth", "tileWidth", "cellWidth", "defaultWidth");
        int height = readDimension(node, 1, "height", "h", "sizeY", "gridHeight", "tileHeight", "cellHeight", "defaultHeight");
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

        String resolvedName = resolveName(rawName, rawId, imageUrl);
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

        out.put("id", id);
        out.put("name", resolvedName == null ? "Asset" : resolvedName);
        if (category != null) out.put("category", category);
        if (imageUrl != null) out.put("imageUrl", imageUrl);
        out.put("width", width);
        out.put("height", height);
        out.put("defaultWidth", width);
        out.put("defaultHeight", height);
        out.put("gridSize", Math.max(width, height));
        out.put("size", sizeLabelFor(kind, width, height));
        out.put("kind", kind.name());
        out.put("blocksMovement", blocksMovement);
        out.put("blocksSight", blocksSight);
        return out;
    }

    private PlacementKind parseKind(String probe) {
        String lower = probe == null ? "" : probe.toLowerCase(Locale.ROOT);
        if (lower.contains("token") || lower.contains("hero") || lower.contains("npc") || lower.contains("player")) return PlacementKind.TOKEN;
        if (lower.contains("spawn")) return PlacementKind.SPAWN;
        if (lower.contains("door") || lower.contains("hatch")) return PlacementKind.DOOR;
        if (lower.contains("wall") || lower.contains("fence") || lower.contains("rampart") || lower.contains("barrier")) return PlacementKind.WALL;
        return PlacementKind.OBJECT;
    }

    private int readDimension(JsonNode node, int defaultValue, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) return Math.max(1, value.asInt());
                try { return Math.max(1, Integer.parseInt(value.asText().trim())); } catch (Exception ignored) {}
            }
        }
        return defaultValue;
    }

    private int[] readSize(JsonNode node) {
        JsonNode value = node.get("size");
        if (value != null) {
            if (value.isTextual()) {
                int[] parsed = parseSizeString(value.asText());
                if (parsed != null) return parsed;
            }
            if (value.isArray() && value.size() >= 2) {
                return new int[] { Math.max(1, value.get(0).asInt(1)), Math.max(1, value.get(1).asInt(1)) };
            }
            if (value.isObject()) {
                int w = readDimension(value, 1, "width", "w", "x");
                int h = readDimension(value, 1, "height", "h", "y");
                if (w > 0 || h > 0) return new int[] { w, h };
            }
        }
        JsonNode dimensions = node.get("dimensions");
        if (dimensions != null) {
            if (dimensions.isArray() && dimensions.size() >= 2) {
                return new int[] { Math.max(1, dimensions.get(0).asInt(1)), Math.max(1, dimensions.get(1).asInt(1)) };
            }
            if (dimensions.isObject()) {
                int w = readDimension(dimensions, 1, "width", "w", "x");
                int h = readDimension(dimensions, 1, "height", "h", "y");
                if (w > 0 || h > 0) return new int[] { w, h };
            }
        }
        JsonNode grid = node.get("gridSize");
        if (grid != null) {
            if (grid.isTextual()) {
                int[] parsed = parseSizeString(grid.asText());
                if (parsed != null) return parsed;
            }
            if (grid.isArray() && grid.size() >= 2) {
                return new int[] { Math.max(1, grid.get(0).asInt(1)), Math.max(1, grid.get(1).asInt(1)) };
            }
            if (grid.isObject()) {
                int w = readDimension(grid, 1, "width", "w", "x");
                int h = readDimension(grid, 1, "height", "h", "y");
                if (w > 0 || h > 0) return new int[] { w, h };
            }
        }
        String gridSize = text(node, "gridSize", "grid", "tileSize");
        if (gridSize != null) {
            int[] parsed = parseSizeString(gridSize);
            if (parsed != null) return parsed;
            try {
                int size = Math.max(1, Integer.parseInt(gridSize.trim()));
                return new int[] { size, size };
            } catch (Exception ignored) {}
        }
        return null;
    }

    private int[] parseSizeString(String text) {
        if (text == null) return null;
        Matcher matcher = SIZE_PATTERN.matcher(text.trim());
        if (matcher.matches()) {
            try {
                return new int[] { Math.max(1, Integer.parseInt(matcher.group(1))), Math.max(1, Integer.parseInt(matcher.group(2))) };
            } catch (Exception ignored) {}
        }
        Matcher digits = DIGITS_PATTERN.matcher(text.trim());
        if (digits.matches()) {
            try {
                int size = Math.max(1, Integer.parseInt(digits.group(1)));
                return new int[] { size, size };
            } catch (Exception ignored) {}
        }
        return null;
    }

    private boolean readBoolean(JsonNode node, boolean defaultValue, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isBoolean()) return value.asBoolean();
                return Boolean.parseBoolean(value.asText());
            }
        }
        return defaultValue;
    }

    private String normalizeImageUrl(String imageUrl, Path baseDir) {
        String trimmed = imageUrl.trim().replace('\\', '/');
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("jar:") || trimmed.startsWith("file:")) {
            return trimmed;
        }
        if (trimmed.startsWith("/uploads/") || trimmed.startsWith("uploads/")) {
            return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
        }
        Path local = resolveRelativePath(trimmed, baseDir);
        if (local != null) {
            return toWebUrl(local); 
        }
        return trimmed;
    }

    private Path resolveRelativePath(String relative, Path baseDir) {
        if (relative == null || relative.isBlank()) return null;
        String cleaned = relative.startsWith("/") ? relative.substring(1) : relative;
        List<Path> candidates = new ArrayList<>();
        if (baseDir != null) {
            candidates.add(baseDir);
            Path current = baseDir;
            for (int i = 0; i < 4 && current != null; i++) {
                current = current.getParent();
                if (current != null) candidates.add(current);
            }
        }
        Path cwd = Paths.get("").toAbsolutePath().normalize();
        candidates.add(cwd);
        Path current = cwd;
        for (int i = 0; i < 4 && current != null; i++) {
            current = current.getParent();
            if (current != null) candidates.add(current);
        }
        for (Path base : candidates) {
            Path candidate = base.resolve(cleaned).normalize();
            if (Files.exists(candidate)) return candidate.toAbsolutePath().normalize();
        }
        Path direct = Paths.get(cleaned);
        if (Files.exists(direct)) return direct.toAbsolutePath().normalize();
        return null;
    }

    private String sizeLabelFor(PlacementKind kind, int width, int height) {
        if (kind == PlacementKind.TOKEN || kind == PlacementKind.SPAWN) {
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

    private boolean looksLikeAssetNode(JsonNode node) {
        boolean hasImage = node.hasNonNull("imageUrl") || node.hasNonNull("image") || node.hasNonNull("path") || node.hasNonNull("file")
                || node.hasNonNull("src") || node.hasNonNull("url") || node.hasNonNull("filename") || node.hasNonNull("fileName")
                || node.hasNonNull("imagePath") || node.hasNonNull("assetPath") || node.hasNonNull("sprite") || node.hasNonNull("thumbnail");
        boolean hasSize = node.hasNonNull("width") || node.hasNonNull("height") || node.hasNonNull("gridSize")
                || node.hasNonNull("defaultWidth") || node.hasNonNull("defaultHeight")
                || node.hasNonNull("size") || node.hasNonNull("dimensions");
        boolean hasBehavior = node.hasNonNull("kind") || node.hasNonNull("blocksMovement") || node.hasNonNull("blocksSight")
                || node.hasNonNull("movementBlock") || node.hasNonNull("visionBlock") || node.hasNonNull("solid") || node.hasNonNull("opaque");
        return hasImage || hasSize || hasBehavior;
    }

    private boolean isTokenLike(JsonNode node) {
        String probe = (text(node, "kind", "type", "placementKind", "category") + " "
                + text(node, "name", "title", "displayName", "label", "ru") + " "
                + text(node, "imageUrl", "image", "path", "file", "src", "url")).toLowerCase(Locale.ROOT);
        return probe.contains("token") || probe.contains("hero") || probe.contains("npc") || probe.contains("player")
                || probe.contains("creature") || probe.contains("spawn");
    }

    private void addSyntheticAsset(Path root, Path image, String imageUrl, Map<String, String> names,
                                   ArrayNode tokens, ArrayNode objects, Set<String> seen) {
        String fileName = image.getFileName().toString();
        String baseName = stripExtension(fileName);
        int[] size = parseSizeFromName(baseName);
        int inferred = inferGridSizeFromPath(root, imageUrl);
        if (inferred > 0 && size[0] == 1 && size[1] == 1) {
            size = new int[] { inferred, inferred };
        }
        String category = deriveCategory(root, imageUrl);
        String name = resolveName(null, baseName, imageUrl, names);
        if (name == null) {
            name = humanize(baseName);
        }

        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", toId((category == null ? "asset" : category) + "-" + baseName));
        node.put("name", name);
        if (category != null) {
            node.put("category", category);
        }
        node.put("imageUrl", imageUrl);
        node.put("width", size[0]);
        node.put("height", size[1]);
        node.put("gridSize", Math.max(size[0], size[1]));
        node.put("kind", isTokenLike(node) ? "TOKEN" : "OBJECT");

        String sig = signature(node);
        if (!seen.add(sig)) {
            return;
        }
        if (isTokenLike(node)) {
            tokens.add(node);
        } else {
            objects.add(node);
        }
    }

    private int[] parseSizeFromName(String baseName) {
        if (baseName == null) return new int[] {1, 1};
        Matcher matcher = SIZE_PATTERN.matcher(baseName.trim());
        if (matcher.matches()) {
            try {
                return new int[] { Math.max(1, Integer.parseInt(matcher.group(1))), Math.max(1, Integer.parseInt(matcher.group(2))) };
            } catch (Exception ignored) {
            }
        }
        Matcher digits = DIGITS_PATTERN.matcher(baseName.trim());
        if (digits.matches()) {
            try {
                int size = Math.max(1, Integer.parseInt(digits.group(1)));
                return new int[] { size, size };
            } catch (Exception ignored) {
            }
        }
        return new int[] {1, 1};
    }

    private String resolveName(String explicitName, String id, String imageUrl) {
        if (explicitName != null && !explicitName.isBlank()) {
            return explicitName;
        }
        String candidate = lastPathSegment(imageUrl);
        if (candidate.isBlank()) {
            candidate = id;
        }
        return humanize(stripExtension(candidate));
    }

    private String resolveName(String explicitName, String id, String imageUrl, Map<String, String> names) {
        if (explicitName != null && !explicitName.isBlank()) {
            return explicitName;
        }
        for (String key : List.of(id, stripExtension(lastPathSegment(id)), lastPathSegment(imageUrl), stripExtension(lastPathSegment(imageUrl)))) {
            String value = names.get(normalizeKey(key));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private void readNamesFile(Path path, Map<String, String> names) {
        try (InputStream is = Files.newInputStream(path)) {
            JsonNode root = MAPPER.readTree(is);
            collectNames(root, names);
        } catch (Exception ignored) {
        }
    }

    private void collectNames(JsonNode node, Map<String, String> names) {
        if (node == null || node.isNull()) return;
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
            for (JsonNode item : node) collectNames(item, names);
        }
    }

    private boolean looksLikeNamesMap(JsonNode node) {
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

    private List<Path> resolveCandidates(List<Path> relativeCandidates) {
        Set<Path> resolved = new java.util.LinkedHashSet<>();
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

    private List<Path> projectRoots() {
        Set<Path> roots = new java.util.LinkedHashSet<>();
        addRoot(roots, System.getProperty("avalon.project.root"));
        addRoot(roots, System.getenv("AVALON_PROJECT_ROOT"));

        Path cwd = Path.of("").toAbsolutePath().normalize();
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

    private void addRoot(Set<Path> roots, String raw) {
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

    private boolean looksLikeProjectRoot(Path dir) {
        return Files.exists(dir.resolve("gradlew.bat"))
                || Files.exists(dir.resolve("settings.gradle"))
                || Files.exists(dir.resolve("build.gradle"))
                || Files.exists(dir.resolve("uploads"));
    }

    private Path resolveAgainstRoot(Path root, Path candidate) {
        if (candidate.isAbsolute()) {
            return candidate.toAbsolutePath().normalize();
        }
        return root.resolve(candidate).toAbsolutePath().normalize();
    }

    private JsonNode readCatalogCandidate(Path candidate) {
        try {
            if (Files.isDirectory(candidate)) {
                return loadFromDirectory(candidate);
            }
            if (Files.isRegularFile(candidate) && candidate.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
                try (InputStream is = Files.newInputStream(candidate)) {
                    return MAPPER.readTree(is);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private JsonNode loadFromDirectory(Path root) {
        try {
            ObjectNode merged = MAPPER.createObjectNode();
            ArrayNode tokens = MAPPER.createArrayNode();
            ArrayNode objects = MAPPER.createArrayNode();
            merged.set("tokens", tokens);
            merged.set("objects", objects);

            List<Path> manifests = new ArrayList<>();
            List<Path> images = new ArrayList<>();
            Map<String, String> names = new HashMap<>();

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

            Set<String> seen = new HashSet<>();
            manifests.sort(Comparator.comparing(Path::toString));
            for (Path manifest : manifests) {
                try (InputStream is = Files.newInputStream(manifest)) {
                    JsonNode node = MAPPER.readTree(is);
                    collectCatalogNodes(node, tokens, objects, seen);
                }
            }

            images.sort(Comparator.comparing(Path::toString));
            for (Path image : images) {
                addSyntheticAsset(root, image, toWebUrl(image), names, tokens, objects, seen);
            }

            return merged;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean hasAnyAssets(JsonNode root) {
        return root != null
                && ((root.has("tokens") && root.get("tokens").isArray() && root.get("tokens").size() > 0)
                || (root.has("objects") && root.get("objects").isArray() && root.get("objects").size() > 0));
    }

    private String signature(JsonNode node) {
        if (node == null || !node.isObject()) return String.valueOf(node);
        String id = text(node, "id", "assetId", "key", "name", "filename", "path", "url");
        String url = text(node, "imageUrl", "image", "path", "file", "src", "url");
        return (id == null ? "" : id) + "|" + (url == null ? "" : url);
    }

    private String text(JsonNode node, String... fields) {
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

    private boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp");
    }

    private boolean isNamesFile(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.startsWith("names") && lower.endsWith(".json");
    }

    private String toWebUrl(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        for (int i = 0; i < normalized.getNameCount(); i++) {
            if ("uploads".equalsIgnoreCase(normalized.getName(i).toString())) {
                Path relative = normalized.subpath(i, normalized.getNameCount());
                return "/" + relative.toString().replace('\\', '/');
            }
        }
        return normalized.toUri().toString();
    }

    private String stripExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private String lastPathSegment(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    private int inferGridSizeFromPath(Path baseDir, String imageUrl) {
        String probe = (imageUrl == null ? "" : imageUrl).replace('\\', '/').toLowerCase(Locale.ROOT);
        if (probe.contains("/gargantuan/")) return 4;
        if (probe.contains("/huge/")) return 3;
        if (probe.contains("/large/")) return 2;
        if (probe.contains("/small/") || probe.contains("/medium/") || probe.contains("/npc/")) return 1;
        if (baseDir != null) {
            try {
                String path = baseDir.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (path.contains("/gargantuan/")) return 4;
                if (path.contains("/huge/")) return 3;
                if (path.contains("/large/")) return 2;
                if (path.contains("/small/") || path.contains("/medium/") || path.contains("/npc/")) return 1;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    private String deriveCategory(Path baseDir, String imageUrl) {
        String path = imageUrl == null ? null : imageUrl.replace('\\', '/');
        if (path != null) {
            int uploadsIdx = path.toLowerCase(Locale.ROOT).indexOf("/uploads/");
            if (uploadsIdx >= 0) {
                String tail = path.substring(uploadsIdx + "/uploads/".length());
                int slash = tail.indexOf('/');
                if (slash > 0) {
                    return tail.substring(0, slash).toLowerCase(Locale.ROOT);
                }
            }
        }
        if (baseDir != null) {
            try {
                Path normalized = baseDir.toAbsolutePath().normalize();
                for (int i = 0; i < normalized.getNameCount(); i++) {
                    if ("assets".equalsIgnoreCase(normalized.getName(i).toString()) && i + 1 < normalized.getNameCount()) {
                        return normalized.getName(i + 1).toString().toLowerCase(Locale.ROOT);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "misc";
    }

    private String humanize(String text) {
        if (text == null || text.isBlank()) return "Asset";
        String cleaned = text.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isBlank()) return "Asset";
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    private String normalizeKey(String source) {
        if (source == null) return "";
        return stripExtension(source.replace('\\', '/').toLowerCase(Locale.ROOT)).replaceAll("[^a-z0-9а-яё]+", "");
    }

    private String toId(String source) {
        return source == null ? "" : source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }
}
