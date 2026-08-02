package com.avalon.dnd.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.avalon.dnd.shared.uploads.AssetCatalogFolderManifestSupport;
import com.avalon.dnd.shared.uploads.AssetCatalogPathSupport;
import com.avalon.dnd.shared.uploads.AssetCatalogSupport;
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
import java.util.stream.Stream;
import static com.avalon.dnd.shared.uploads.AssetCatalogJsonSupport.*;

@RestController
@RequestMapping("/api/assets")
public class AssetCatalogController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
            for (Path candidate : resolveCatalogCandidates()) {
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

    private java.util.List<Path> resolveCatalogCandidates() {
        java.util.LinkedHashSet<Path> candidates = new java.util.LinkedHashSet<>();

        for (Path root : resolveProjectRoots()) {
            Path assetsRoot = root.resolve("uploads/assets").toAbsolutePath().normalize();
            if (!Files.isDirectory(assetsRoot)) {
                continue;
            }

            Path tokensCatalog = assetsRoot.resolve("tokens/catalog.json");
            Path objectsCatalog = assetsRoot.resolve("objects/catalog.json");
            Path tokensDir = assetsRoot.resolve("tokens");
            Path objectsDir = assetsRoot.resolve("objects");

            if (Files.isRegularFile(tokensCatalog)) candidates.add(tokensCatalog);
            if (Files.isRegularFile(objectsCatalog)) candidates.add(objectsCatalog);
            if (Files.isDirectory(tokensDir)) candidates.add(tokensDir);
            if (Files.isDirectory(objectsDir)) candidates.add(objectsDir);
        }

        return new java.util.ArrayList<>(candidates);
    }

    private java.util.List<Path> resolveProjectRoots() {
        java.util.LinkedHashSet<Path> roots = new java.util.LinkedHashSet<>();
        addProjectRoot(roots, System.getProperty("avalon.project.root"));
        addProjectRoot(roots, System.getenv("AVALON_PROJECT_ROOT"));

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path projectRoot = findProjectRoot(cwd);
        if (projectRoot != null) {
            roots.add(projectRoot);
        }

        return new java.util.ArrayList<>(roots);
    }

    private void addProjectRoot(java.util.Set<Path> roots, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(raw).toAbsolutePath().normalize();
            Path projectRoot = findProjectRoot(path);
            if (projectRoot != null) {
                roots.add(projectRoot);
            }
        } catch (Exception ignored) {
        }
    }

    private Path findProjectRoot(Path start) {
        if (start == null) {
            return null;
        }
        for (Path current = start.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
            if (looksLikeProjectRoot(current) && Files.isDirectory(current.resolve("uploads/assets"))) {
                return current;
            }
        }
        return null;
    }

    private boolean looksLikeProjectRoot(Path dir) {
        return Files.exists(dir.resolve("settings.gradle"))
                || Files.exists(dir.resolve("settings.gradle.kts"))
                || Files.exists(dir.resolve("gradlew"))
                || Files.exists(dir.resolve("gradlew.bat"));
    }


    private void scanDirectory(Path root, ObjectNode out, ArrayNode tokens, ArrayNode objects) throws IOException {

        Map<String, String> names = new HashMap<>();
        List<Path> manifests = new ArrayList<>();
        List<Path> images = new ArrayList<>();

        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                if (AssetCatalogSupport.isExcludedAssetPath(path)) {
                    return;
                }
                String filename = path.getFileName().toString();
                String lower = filename.toLowerCase(Locale.ROOT);
                if (AssetCatalogSupport.isNamesFile(filename)) {
                    readNamesFile(path, names);
                } else if (lower.endsWith(".json")) {
                    manifests.add(path);
                } else if (AssetCatalogSupport.isImageFile(path)) {
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
                        .filter(path -> AssetCatalogSupport.isNamesFile(path.getFileName().toString()))
                        .forEach(path -> readNamesFile(path, names));
            }
        }

        try (InputStream is = Files.newInputStream(jsonPath)) {
            JsonNode root = MAPPER.readTree(is);
            if (root != null && root.isObject() && root.has("folders")) {
                scanFolderManifest(jsonPath, root, tokens, objects, seen);
                return;
            }
            collectCatalogNodes(root, baseDir, names, tokens, objects, seen);
            if (root != null && root.isObject()) {
                root.fields().forEachRemaining(entry -> collectCatalogNodes(entry.getValue(), baseDir, names, tokens, objects, seen));
            }
        }
    }

    private void scanFolderManifest(Path jsonPath, JsonNode manifestRoot,
                                    ArrayNode tokens, ArrayNode objects, Set<String> seen) throws IOException {
        Path baseDir = jsonPath.getParent();
        Map<String, String> names = new HashMap<>();
        if (baseDir != null && Files.isDirectory(baseDir)) {
            try (Stream<Path> walk = Files.walk(baseDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(path -> AssetCatalogSupport.isNamesFile(path.getFileName().toString()))
                        .forEach(path -> readNamesFile(path, names));
            }
        }
        collectNames(manifestRoot, names);

        JsonNode defaults = manifestRoot.path("defaults");
        JsonNode folders = manifestRoot.path("folders");
        if (folders == null || !folders.isArray()) {
            return;
        }

        for (JsonNode folderNode : folders) {
            if (folderNode == null || !folderNode.isObject()) {
                continue;
            }
            String folderPath = firstText(folderNode, "path", "folder", "name", "directory");
            if (folderPath == null || folderPath.isBlank()) {
                continue;
            }
            Path resolvedFolder = resolveRelativePath(folderPath, baseDir);
            if (resolvedFolder == null || !Files.isDirectory(resolvedFolder)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(resolvedFolder)) {
                walk.filter(Files::isRegularFile)
                        .filter(AssetCatalogSupport::isImageFile)
                        .forEach(image -> addManifestFolderAsset(baseDir, image, names, defaults, folderNode, tokens, objects, seen));
            }
        }
    }

    private void addManifestFolderAsset(Path baseDir, Path image, Map<String, String> names,
                                        JsonNode defaults, JsonNode folderNode,
                                        ArrayNode tokens, ArrayNode objects, Set<String> seen) {
        String imageUrl = toWebUrl(image);
        // The folder tree is the source of truth for browsing.  A manifest
        // category is a semantic label (for example, "creature"), not a
        // replacement for the physical hierarchy shown in the asset picker.
        String category = AssetCatalogFolderManifestSupport.relativeCategory(baseDir, image);
        if (category == null || category.isBlank()) {
            category = AssetCatalogFolderManifestSupport.normalizeCategoryPath(
                    firstText(folderNode, "category", "group", "pack"));
        }

        String baseName = AssetCatalogSupport.stripExtension(image.getFileName().toString());
        String name = resolveName(null, baseName, imageUrl, names);
        if (name == null) {
            name = AssetCatalogSupport.humanize(baseName);
        }

        String kindText = firstText(folderNode, "kind", "type", "placementKind");
        if (kindText == null) {
            kindText = firstText(defaults, "kind", "type", "placementKind");
        }
        PlacementKind kind = parseKind((kindText == null ? "" : kindText) + " " + category + " " + imageUrl + " " + name);
        int[] size = AssetCatalogFolderManifestSupport.readFolderSize(folderNode, defaults);
        if ((kind == PlacementKind.TOKEN || kind == PlacementKind.SPAWN) && size[0] == 1 && size[1] == 1) {
            int inferred = AssetCatalogSupport.inferGridSizeFromPath(baseDir, imageUrl);
            if (inferred > 1) {
                size = new int[] { inferred, inferred };
            }
        }

        boolean blocksMovement = AssetCatalogFolderManifestSupport.readFolderBoolean(folderNode, defaults,
                kind == PlacementKind.WALL || kind == PlacementKind.DOOR,
                "blocksMovement", "blocksMove", "movementBlock", "solid");
        boolean blocksSight = AssetCatalogFolderManifestSupport.readFolderBoolean(folderNode, defaults, blocksMovement,
                "blocksSight", "blocksVision", "visionBlock", "opaque");

        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", AssetCatalogSupport.toId((category == null ? "asset" : category) + "-" + baseName + "-" + AssetCatalogSupport.lastPathSegment(imageUrl)));
        node.put("name", name);
        if (category != null) {
            node.put("category", category);
        }
        node.put("imageUrl", imageUrl);
        node.put("width", size[0]);
        node.put("height", size[1]);
        node.put("defaultWidth", size[0]);
        node.put("defaultHeight", size[1]);
        node.put("gridSize", Math.max(size[0], size[1]));
        node.put("size", sizeLabelFor(kind, size[0], size[1]));
        node.put("kind", kind.name());
        node.put("blocksMovement", blocksMovement);
        node.put("blocksSight", blocksSight);
        node.put("dayVision", AssetCatalogFolderManifestSupport.readFolderInt(folderNode, defaults, 0,
                "dayVision", "visionDay", "visionRadiusDay", "daySight"));
        node.put("nightVision", AssetCatalogFolderManifestSupport.readFolderInt(folderNode, defaults, 0,
                "nightVision", "visionNight", "visionRadiusNight", "nightSight"));

        String sig = signature(node);
        if (!seen.add(sig)) {
            return;
        }
        if (kind == PlacementKind.TOKEN || kind == PlacementKind.SPAWN || isTokenLike(node)) {
            tokens.add(node);
        } else {
            objects.add(node);
        }
    }

    private void collectCatalogNodes(JsonNode node, ArrayNode tokens, ArrayNode objects, Set<String> seen) {
        collectCatalogNodes(node, null, Map.of(), tokens, objects, seen);
    }

    private void collectCatalogNodes(JsonNode node, Path baseDir, ArrayNode tokens, ArrayNode objects, Set<String> seen) {
        collectCatalogNodes(node, baseDir, Map.of(), tokens, objects, seen);
    }

    private void collectCatalogNodes(JsonNode node, Path baseDir, Map<String, String> names,
                                     ArrayNode tokens, ArrayNode objects, Set<String> seen) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectCatalogNodes(item, baseDir, names, tokens, objects, seen);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }

        if (looksLikeAssetNode(node)) {
            ObjectNode normalized = normalizeAssetNode(node, baseDir, names);
            if (isAllowedCatalogAsset(normalized)) {
                String sig = signature(normalized);
                if (seen.add(sig)) {
                    if (isTokenLike(normalized)) {
                        tokens.add(normalized);
                    } else {
                        objects.add(normalized);
                    }
                }
            }
        }

        node.fields().forEachRemaining(entry -> collectCatalogNodes(entry.getValue(), baseDir, names, tokens, objects, seen));
    }

    private ObjectNode normalizeAssetNode(JsonNode node, Path baseDir) {
        return normalizeAssetNode(node, baseDir, Map.of());
    }

    private ObjectNode normalizeAssetNode(JsonNode node, Path baseDir, Map<String, String> names) {
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
            int inferred = AssetCatalogSupport.inferGridSizeFromPath(baseDir, imageUrl);
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
            int inferred = AssetCatalogSupport.inferGridSizeFromPath(baseDir, imageUrl);
            if (gridSize <= 1 && inferred > 1) {
                gridSize = inferred;
            }
            width = gridSize;
            height = gridSize;
        }

        boolean blocksMovement = readBoolean(node, false, "blocksMovement", "blocksMove", "movementBlock", "solid");
        boolean blocksSight = readBoolean(node, blocksMovement, "blocksSight", "blocksVision", "visionBlock", "opaque");

        if (imageUrl != null) {
            imageUrl = AssetCatalogSupport.normalizeImageUrl(imageUrl, baseDir);
        }

        String resolvedName = resolveName(rawName, rawId, imageUrl, names);
        if (resolvedName == null) {
            resolvedName = AssetCatalogSupport.humanize(rawId != null ? rawId : AssetCatalogSupport.stripExtension(AssetCatalogSupport.lastPathSegment(imageUrl)));
        }
        String id = rawId != null ? AssetCatalogSupport.toId(rawId) : AssetCatalogSupport.toId((resolvedName != null ? resolvedName : "asset") + "-" + AssetCatalogSupport.lastPathSegment(imageUrl));
        if (id.isBlank()) {
            id = AssetCatalogSupport.toId(AssetCatalogSupport.lastPathSegment(imageUrl));
        }
        if (category == null || category.isBlank()) {
            category = AssetCatalogSupport.deriveCategory(baseDir, imageUrl);
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
        int dayVision = readDimension(node, 0, "dayVision", "visionDay", "visionRadiusDay", "daySight");
        int nightVision = readDimension(node, 0, "nightVision", "visionNight", "visionRadiusNight", "nightSight");

        out.put("blocksMovement", blocksMovement);
        out.put("blocksSight", blocksSight);
        out.put("dayVision", dayVision);
        out.put("nightVision", nightVision);
        return out;
    }

    private boolean isAllowedCatalogAsset(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        String imageUrl = text(node, "imageUrl", "image", "path", "file", "src", "url", "filename", "fileName", "imagePath", "assetPath", "sprite", "thumbnail");
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        String cleaned = imageUrl.replace('\\', '/');
        String noSlash = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
        String lower = noSlash.toLowerCase(java.util.Locale.ROOT);
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".tif") || lower.endsWith(".tiff"))) {
            return false;
        }
        return noSlash.startsWith("uploads/assets/tokens/") || noSlash.startsWith("uploads/assets/objects/");
    }

    private PlacementKind parseKind(String probe) {
        String lower = probe == null ? "" : probe.toLowerCase(Locale.ROOT);
        if (lower.contains("token") || lower.contains("hero") || lower.contains("npc") || lower.contains("player")) return PlacementKind.TOKEN;
        if (lower.contains("spawn")) return PlacementKind.SPAWN;
        if (lower.contains("door") || lower.contains("hatch")) return PlacementKind.DOOR;
        if (lower.contains("wall") || lower.contains("fence") || lower.contains("rampart") || lower.contains("barrier")) return PlacementKind.WALL;
        return PlacementKind.OBJECT;
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
        String baseName = AssetCatalogSupport.stripExtension(fileName);
        int[] size = AssetCatalogFolderManifestSupport.parseSizeFromName(baseName);
        int inferred = AssetCatalogSupport.inferGridSizeFromPath(root, imageUrl);
        if (inferred > 0 && size[0] == 1 && size[1] == 1) {
            size = new int[] { inferred, inferred };
        }
        String category = AssetCatalogSupport.deriveCategory(root, imageUrl);
        String name = resolveName(null, baseName, imageUrl, names);
        if (name == null) {
            name = AssetCatalogSupport.humanize(baseName);
        }

        ObjectNode node = MAPPER.createObjectNode();
        node.put("id", AssetCatalogSupport.toId((category == null ? "asset" : category) + "-" + baseName));
        node.put("name", name);
        if (category != null) {
            node.put("category", category);
        }
        node.put("imageUrl", imageUrl);
        node.put("width", size[0]);
        node.put("height", size[1]);
        node.put("gridSize", Math.max(size[0], size[1]));
        boolean tokenLike = isTokenLike(node);
        node.put("kind", tokenLike ? "TOKEN" : "OBJECT");
        int[] vision = inferVisionFromPath(root, imageUrl, tokenLike);
        node.put("dayVision", vision[0]);
        node.put("nightVision", vision[1]);

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

    private int[] inferVisionFromPath(Path root, String imageUrl, boolean tokenLike) {
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

    private String resolveName(String explicitName, String id, String imageUrl) {
        if (explicitName != null && !explicitName.isBlank()) {
            return explicitName;
        }
        String candidate = AssetCatalogSupport.lastPathSegment(imageUrl);
        if (candidate.isBlank()) {
            candidate = id;
        }
        return AssetCatalogSupport.humanize(AssetCatalogSupport.stripExtension(candidate));
    }

    private String resolveName(String explicitName, String id, String imageUrl, Map<String, String> names) {
        for (String key : List.of(id, AssetCatalogSupport.stripExtension(AssetCatalogSupport.lastPathSegment(id)), AssetCatalogSupport.lastPathSegment(imageUrl), AssetCatalogSupport.stripExtension(AssetCatalogSupport.lastPathSegment(imageUrl)))) {
            String value = names.get(AssetCatalogSupport.normalizeKey(key));
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return explicitName != null && !explicitName.isBlank() ? explicitName : null;
    }

    private void readNamesFile(Path path, Map<String, String> names) {
        try (InputStream is = Files.newInputStream(path)) {
            JsonNode root = MAPPER.readTree(is);
            collectNames(root, names);
        } catch (Exception ignored) {
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
                        .filter(path -> !AssetCatalogSupport.isExcludedAssetPath(path))
                        .forEach(path -> {
                            String filename = path.getFileName().toString();
                            String lower = filename.toLowerCase(Locale.ROOT);
                            if (AssetCatalogSupport.isNamesFile(filename)) {
                                readNamesFile(path, names);
                            } else if (lower.endsWith(".json")) {
                                manifests.add(path);
                            } else if (AssetCatalogSupport.isImageFile(path)) {
                                images.add(path);
                            }
                        });
            }

            Set<String> seen = new HashSet<>();
            manifests.sort(Comparator.comparing(Path::toString));
            for (Path manifest : manifests) {
                try (InputStream is = Files.newInputStream(manifest)) {
                    JsonNode node = MAPPER.readTree(is);
                    collectCatalogNodes(node, manifest.getParent(), names, tokens, objects, seen);
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
        if (url != null && !url.isBlank()) {
            return "url:" + url.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        }
        return "id:" + (id == null ? "" : id.trim().toLowerCase(Locale.ROOT));
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
}
