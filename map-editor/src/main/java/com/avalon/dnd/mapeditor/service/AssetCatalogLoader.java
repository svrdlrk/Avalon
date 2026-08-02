package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.AssetCatalog;
import com.avalon.dnd.mapeditor.model.AssetDefinition;
import com.avalon.dnd.mapeditor.model.PlacementKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.avalon.dnd.shared.uploads.AssetCatalogFolderManifestSupport;
import com.avalon.dnd.shared.uploads.AssetCatalogPathSupport;
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
import java.util.stream.Stream;
import static com.avalon.dnd.shared.uploads.AssetCatalogJsonSupport.*;

public final class AssetCatalogLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AssetCatalogLoader() {}

    public static AssetCatalog loadDefault() {
        AssetCatalog merged = new AssetCatalog();

        String configured = System.getProperty("avalon.assets.dir");
        if (configured != null && !configured.isBlank()) {
            mergeInto(merged, tryLoad(Path.of(configured)));
        }

        for (Path candidate : resolveCatalogCandidates()) {
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

    private static java.util.List<Path> resolveCatalogCandidates() {
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

    private static java.util.List<Path> resolveProjectRoots() {
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

    private static void addProjectRoot(java.util.Set<Path> roots, String raw) {
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


    private static Path findProjectRoot(Path start) {
        if (start == null) {
            return null;
        }
        for (Path current = start.toAbsolutePath().normalize(); current != null; current = current.getParent()) {
            if ((Files.exists(current.resolve("settings.gradle"))
                    || Files.exists(current.resolve("settings.gradle.kts"))
                    || Files.exists(current.resolve("gradlew"))
                    || Files.exists(current.resolve("gradlew.bat")))
                    && Files.isDirectory(current.resolve("uploads/assets"))) {
                return current;
            }
        }
        return null;
    }


    public static AssetCatalog loadFromJson(Path jsonPath) throws IOException {
        JsonNode root = MAPPER.readTree(Files.newInputStream(jsonPath));
        if (root != null && root.isObject() && root.has("folders")) {
            return loadFromFolderManifest(jsonPath, root);
        }
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
                    if (AssetCatalogSupport.isExcludedAssetPath(path)) {
                        return;
                    }
                    String filename = path.getFileName().toString();
                    String lower = filename.toLowerCase(Locale.ROOT);
                    if (AssetCatalogSupport.isNamesFile(filename)) {
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
                mergeInto(catalog, loadManifest(manifest));
            }

            images.sort(Comparator.comparing(Path::toString));
            for (Path image : images) {
                addIfMissing(catalog, fromImageFile(root, image, toWebUrl(image), names));
            }
        } catch (IOException ignored) {
        }

        return catalog;
    }

    private static AssetCatalog loadManifest(Path jsonPath) {
        try {
            JsonNode root = MAPPER.readTree(Files.newInputStream(jsonPath));
            if (root != null && root.isObject() && root.has("folders")) {
                return loadFromFolderManifest(jsonPath, root);
            }
            return loadFromJson(jsonPath);
        } catch (Exception ignored) {
            return new AssetCatalog();
        }
    }

    private static AssetCatalog loadFromFolderManifest(Path jsonPath, JsonNode manifestRoot) throws IOException {
        AssetCatalog catalog = new AssetCatalog();
        Path baseDir = jsonPath.getParent();

        Map<String, String> names = new HashMap<>();
        if (baseDir != null && Files.isDirectory(baseDir)) {
            collectNamesFromDirectory(baseDir, names);
        }
        collectNames(manifestRoot, names);

        JsonNode defaults = manifestRoot.path("defaults");
        JsonNode folders = manifestRoot.path("folders");
        if (folders != null && folders.isArray()) {
            for (JsonNode folderNode : folders) {
                if (folderNode == null || folderNode.isNull() || !folderNode.isObject()) {
                    continue;
                }
                String folderPath = firstText(folderNode, "path", "folder", "name", "directory");
                if (folderPath == null || folderPath.isBlank()) {
                    continue;
                }
                Path resolvedFolder = resolveRelativePath(folderPath, baseDir);
                if (resolvedFolder == null || !Files.exists(resolvedFolder)) {
                    continue;
                }

                try (Stream<Path> walk = Files.walk(resolvedFolder)) {
                    walk.filter(Files::isRegularFile)
                            .filter(AssetCatalogSupport::isImageFile)
                            .forEach(image -> {
                                AssetDefinition asset = fromFolderImage(baseDir, image, names, defaults, folderNode);
                                addIfMissing(catalog, asset);
                            });
                }
            }
        }

        if (catalog.getAssets().isEmpty() && baseDir != null && Files.isDirectory(baseDir)) {
            try (Stream<Path> walk = Files.walk(baseDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(AssetCatalogSupport::isImageFile)
                        .forEach(image -> addIfMissing(catalog, fromImageFile(baseDir, image, toWebUrl(image), names)));
            }
        }

        return catalog;
    }

    private static AssetDefinition fromFolderImage(Path baseDir,
                                                   Path image,
                                                   Map<String, String> names,
                                                   JsonNode defaults,
                                                   JsonNode folderNode) {
        String imageUrl = toWebUrl(image);
        String category = AssetCatalogFolderManifestSupport.relativeCategory(baseDir, image);
        if (category == null || category.isBlank()) {
            String folderCategory = firstText(folderNode, "category");
            if (folderCategory != null && !folderCategory.isBlank()) {
                category = AssetCatalogFolderManifestSupport.normalizeCategoryPath(folderCategory);
            }
        }

        String fileName = image.getFileName().toString();
        String baseName = AssetCatalogSupport.stripExtension(fileName);
        String name = resolveName(null, baseName, imageUrl, names);
        if (name == null) {
            name = AssetCatalogSupport.humanize(baseName);
        }

        PlacementKind kind = parseKind(folderNode, category, name, imageUrl);
        int[] size = AssetCatalogFolderManifestSupport.readFolderSize(folderNode, defaults);
        boolean blocksMovement = AssetCatalogFolderManifestSupport.readFolderBoolean(folderNode, defaults, kind == PlacementKind.WALL || kind == PlacementKind.DOOR,
                "blocksMovement", "blocksMove", "movementBlock", "solid");
        boolean blocksSight = AssetCatalogFolderManifestSupport.readFolderBoolean(folderNode, defaults, blocksMovement,
                "blocksSight", "blocksVision", "visionBlock", "opaque");

        if ((kind == PlacementKind.TOKEN || kind == PlacementKind.SPAWN) && size[0] == 1 && size[1] == 1) {
            int inferred = AssetCatalogSupport.inferGridSizeFromPath(baseDir, imageUrl);
            if (inferred > 1) {
                size = new int[] { inferred, inferred };
            }
        }

        String idPrefix = category == null || category.isBlank() ? "asset" : category;
        String id = AssetCatalogSupport.toId(idPrefix + "-" + baseName + "-" + AssetCatalogSupport.lastPathSegment(imageUrl));
        if (id.isBlank()) {
            id = AssetCatalogSupport.toId(imageUrl);
        }

        return new AssetDefinition(id, name, category, imageUrl, size[0], size[1], blocksMovement, blocksSight, kind);
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
                                if (AssetCatalogSupport.isNamesFile(filename)) {
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
                mergeInto(catalog, loadManifest(manifest));
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

    private static AssetDefinition fromImageFile(Path root, Path image, String imageUrl, Map<String, String> names) {
        String fileName = image.getFileName().toString();
        String baseName = AssetCatalogSupport.stripExtension(fileName);
        int[] size = AssetCatalogFolderManifestSupport.parseSizeFromName(baseName);
        int inferred = AssetCatalogSupport.inferGridSizeFromPath(root, imageUrl);
        if (inferred > 0 && size[0] == 1 && size[1] == 1) {
            size = new int[] { inferred, inferred };
        }
        String category = AssetCatalogFolderManifestSupport.relativeCategory(root, image);
        if (category == null || category.isBlank()) {
            category = AssetCatalogSupport.deriveCategory(root, imageUrl);
        }
        PlacementKind kind = inferKind(baseName, category);
        boolean blocksMovement = kind == PlacementKind.WALL || kind == PlacementKind.DOOR
                || containsAny(baseName, "wall", "fence", "rampart", "door", "hatch");
        boolean blocksSight = blocksMovement && !containsAny(baseName, "window", "arrowslit");
        String name = resolveName(null, baseName, imageUrl, names);
        if (name == null) {
            name = AssetCatalogSupport.humanize(baseName);
        }
        int[] vision = inferVisionFromPath(root, imageUrl, kind);
        String id = AssetCatalogSupport.toId((category == null ? "asset" : category) + "-" + baseName);
        return new AssetDefinition(id, name, category, imageUrl, size[0], size[1], blocksMovement, blocksSight, kind, vision[0], vision[1]);
    }

    private static String toWebUrl(Path file) {
        return localToWebUrl(file);
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
        String assetId = AssetCatalogSupport.normalizeKey(asset.getId());
        String assetUrl = AssetCatalogSupport.normalizeKey(asset.getImageUrl());
        String assetName = AssetCatalogSupport.normalizeKey(asset.getName());
        for (AssetDefinition existing : catalog.getAssets()) {
            if (!assetId.isBlank() && assetId.equals(AssetCatalogSupport.normalizeKey(existing.getId()))) return;
            if (!assetUrl.isBlank() && assetUrl.equals(AssetCatalogSupport.normalizeKey(existing.getImageUrl()))) return;
            if (!assetName.isBlank() && assetName.equals(AssetCatalogSupport.normalizeKey(existing.getName()))) return;
        }
        catalog.add(asset);
    }

    private static void collectNamesFromDirectory(Path dir, Map<String, String> names) {
        try {
            Files.walk(dir)
                    .filter(Files::isRegularFile)
                    .filter(path -> AssetCatalogSupport.isNamesFile(path.getFileName().toString()))
                    .forEach(path -> readNamesFile(path, names));
        } catch (IOException ignored) {
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
            if (asset != null && isAllowedCatalogAsset(asset.getImageUrl())) {
                catalog.add(asset);
            }
            return;
        }

        node.fields().forEachRemaining(entry -> collectAssets(entry.getValue(), baseDir, names, catalog));
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

        int dayVision = AssetCatalogFolderManifestSupport.readFolderInt(node, node, 0, "dayVision", "visionDay", "visionRadiusDay", "daySight");
        int nightVision = AssetCatalogFolderManifestSupport.readFolderInt(node, node, 0, "nightVision", "visionNight", "visionRadiusNight", "nightSight");

        return new AssetDefinition(id, resolvedName, category, imageUrl, width, height, blocksMovement, blocksSight, kind, dayVision, nightVision);
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
        for (String key : nameLookupKeys(id, imageUrl)) {
            String normalized = AssetCatalogSupport.normalizeKey(key);
            String value = names.get(normalized);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return explicitName != null && !explicitName.isBlank() ? explicitName : null;
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

    private static boolean isAllowedCatalogAsset(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return false;
        }
        String cleaned = imageUrl.replace('\\', '/');
        String noSlash = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
        String lower = noSlash.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".tif") || lower.endsWith(".tiff"))) {
            return false;
        }
        return noSlash.startsWith("uploads/assets/tokens/") || noSlash.startsWith("uploads/assets/objects/");
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


    private static boolean isImageFile(Path path) {
        if (path == null) {
            return false;
        }
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return filename.endsWith(".png")
                || filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")
                || filename.endsWith(".gif")
                || filename.endsWith(".webp")
                || filename.endsWith(".bmp")
                || filename.endsWith(".tif")
                || filename.endsWith(".tiff");
    }

    private static int[] inferVisionFromPath(Path root, String imageUrl, PlacementKind kind) {
        boolean tokenLike = kind == PlacementKind.TOKEN || kind == PlacementKind.SPAWN;
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
