package com.avalon.dnd.shared.uploads;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class UploadsManifestLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String[] IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".gif"};

    private UploadsManifestLoader() {
    }

    public static List<UploadAssetEntry> loadDefault() {
        LinkedHashMap<String, UploadAssetEntry> assets = new LinkedHashMap<>();
        for (Path root : candidateProjectRoots()) {
            merge(assets, loadFromProjectRoot(root));
        }
        return new ArrayList<>(assets.values());
    }

    public static List<UploadAssetEntry> loadFromProjectRoot(Path projectRoot) {
        Path uploadsRoot = resolveUploadsRoot(projectRoot);
        if (uploadsRoot == null || !Files.isDirectory(uploadsRoot)) {
            return List.of();
        }

        Map<Path, Map<String, String>> nameOverrides = discoverNameOverrideFiles(uploadsRoot);

        LinkedHashMap<String, UploadAssetEntry> assets = new LinkedHashMap<>();
        List<Path> manifests = discoverManifestFiles(uploadsRoot);
        for (Path manifest : manifests) {
            merge(assets, loadManifest(projectRoot, manifest, findNameOverrides(manifest, nameOverrides)));
        }

        if (assets.isEmpty()) {
            merge(assets, scanImages(projectRoot, uploadsRoot, nameOverrides));
        }

        return new ArrayList<>(assets.values());
    }

    public static List<Path> discoverManifestFiles(Path uploadsRoot) {
        if (uploadsRoot == null || !Files.isDirectory(uploadsRoot)) {
            return List.of();
        }

        Path assetsRoot = uploadsRoot.resolve("assets");
        if (!Files.isDirectory(assetsRoot)) {
            return List.of();
        }

        Set<Path> manifests = new LinkedHashSet<>();
        try (Stream<Path> walk = Files.walk(assetsRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null && path.getFileName().toString().equalsIgnoreCase("catalog.json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> manifests.add(path.toAbsolutePath().normalize()));
        } catch (IOException ignored) {
        }

        return new ArrayList<>(manifests);
    }

    public static List<UploadAssetEntry> loadManifest(Path projectRoot, Path manifest) {
        return loadManifest(projectRoot, manifest, Map.of());
    }

    private static List<UploadAssetEntry> loadManifest(Path projectRoot, Path manifest, Map<String, String> nameOverrides) {
        if (projectRoot == null || manifest == null || !Files.isRegularFile(manifest)) {
            return List.of();
        }

        LinkedHashMap<String, UploadAssetEntry> assets = new LinkedHashMap<>();
        try (var in = Files.newInputStream(manifest)) {
            JsonNode root = MAPPER.readTree(in);
            if (root == null) {
                return List.of();
            }
            if (root.isArray()) {
                for (JsonNode node : root) {
                    addAssetNode(projectRoot, manifest, node, nameOverrides, assets);
                }
            } else if (root.isObject()) {
                boolean loaded = false;
                loaded |= loadAssetArray(projectRoot, manifest, root.path("assets"), nameOverrides, assets);
                loaded |= loadAssetArray(projectRoot, manifest, root.path("tokens"), nameOverrides, assets);
                loaded |= loadAssetArray(projectRoot, manifest, root.path("objects"), nameOverrides, assets);
                loaded |= loadAssetArray(projectRoot, manifest, root.path("items"), nameOverrides, assets);
                if (!loaded && looksLikeAsset(root)) {
                    addAssetNode(projectRoot, manifest, root, nameOverrides, assets);
                }
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>(assets.values());
    }

    private static boolean loadAssetArray(Path projectRoot, Path manifest, JsonNode array, Map<String, String> nameOverrides, LinkedHashMap<String, UploadAssetEntry> assets) {
        if (array == null || !array.isArray()) {
            return false;
        }
        boolean loaded = false;
        for (JsonNode item : array) {
            addAssetNode(projectRoot, manifest, item, nameOverrides, assets);
            loaded = true;
        }
        return loaded;
    }

    private static void addAssetNode(Path projectRoot,
                                     Path manifest,
                                     JsonNode node,
                                     Map<String, String> nameOverrides,
                                     LinkedHashMap<String, UploadAssetEntry> assets) {
        if (node == null || !node.isObject()) {
            return;
        }

        String id = firstText(node, "id", "assetId", "key");
        String name = firstText(node, "name", "title", "label");
        String category = firstText(node, "category", "group", "folder");
        String kindText = firstText(node, "kind", "type");
        UploadAssetKind kind = UploadAssetKind.fromText(kindText, category, name);
        String imageRef = firstText(node, "imageUrl", "image", "path", "file", "src", "url");
        Path imagePath = resolveReference(projectRoot, manifest, imageRef);
        String relativePath = toRelativeProjectPath(projectRoot, imagePath);

        if (id == null || id.isBlank()) {
            id = deriveId(kind, category, name, imagePath);
        }
        if (name == null || name.isBlank()) {
            name = imagePath == null ? id : stripExtension(imagePath.getFileName().toString());
        }
        name = resolveNameOverride(nameOverrides, name, id, imagePath, relativePath);
        if (category == null || category.isBlank()) {
            category = deriveCategory(projectRoot, imagePath);
        }

        int[] dimensions = readDimensions(node);
        int width = dimensions[0];
        int height = dimensions[1];
        boolean blocksMovement = boolValue(node, kind.blocksByDefault(), "blocksMovement", "solid", "blocksMove");
        boolean blocksSight = boolValue(node, blocksMovement, "blocksSight", "blocksVision", "opaque");

        UploadAssetEntry entry = new UploadAssetEntry(
                id,
                name,
                category,
                kind,
                imagePath,
                relativePath,
                width,
                height,
                blocksMovement,
                blocksSight,
                manifest.toAbsolutePath().normalize()
        );
        assets.putIfAbsent(entry.deduplicationKey(), entry);
    }

    private static List<UploadAssetEntry> scanImages(Path projectRoot, Path uploadsRoot, Map<Path, Map<String, String>> nameOverridesByDir) {
        LinkedHashMap<String, UploadAssetEntry> assets = new LinkedHashMap<>();
        Path assetsRoot = uploadsRoot.resolve("assets");
        if (!Files.isDirectory(assetsRoot)) {
            return List.of();
        }

        try (Stream<Path> walk = Files.walk(assetsRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(UploadsManifestLoader::isImageFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(image -> {
                        String relativePath = toRelativeProjectPath(projectRoot, image);
                        String baseName = stripExtension(image.getFileName().toString());
                        String category = deriveCategory(projectRoot, image);
                        UploadAssetKind kind = UploadAssetKind.fromText(null, category, baseName);
                        Map<String, String> nameOverrides = findNameOverrides(image, nameOverridesByDir);
                        String resolvedName = resolveNameOverride(nameOverrides, baseName, deriveId(kind, category, baseName, image), image, relativePath);
                        UploadAssetEntry entry = new UploadAssetEntry(
                                deriveId(kind, category, baseName, image),
                                resolvedName,
                                category,
                                kind,
                                image.toAbsolutePath().normalize(),
                                relativePath,
                                parseSizeFromName(baseName)[0],
                                parseSizeFromName(baseName)[1],
                                kind.blocksByDefault(),
                                kind.blocksByDefault(),
                                null
                        );
                        assets.putIfAbsent(entry.deduplicationKey(), entry);
                    });
        } catch (IOException ignored) {
        }

        return new ArrayList<>(assets.values());
    }

    private static boolean looksLikeAsset(JsonNode node) {
        return node != null && node.isObject() && (
                node.hasNonNull("imageUrl") || node.hasNonNull("image") || node.hasNonNull("path") ||
                node.hasNonNull("file") || node.hasNonNull("src") || node.hasNonNull("url")
        );
    }

    private static boolean isImageFile(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        for (String suffix : IMAGE_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveUploadsRoot(Path projectRoot) {
        if (projectRoot == null) {
            return null;
        }

        Path root = projectRoot.toAbsolutePath().normalize();
        if (Files.isDirectory(root.resolve("uploads"))) {
            return root.resolve("uploads");
        }
        if (root.getFileName() != null && root.getFileName().toString().equalsIgnoreCase("uploads")) {
            return root;
        }
        if (Files.isDirectory(root) && Files.isDirectory(root.resolve("assets")) && Files.isDirectory(root.resolve("maps"))) {
            return root;
        }
        return null;
    }

    private static List<Path> candidateProjectRoots() {
        List<Path> roots = new ArrayList<>();
        Path cwd = Path.of("").toAbsolutePath().normalize();
        roots.add(cwd);
        if (cwd.getParent() != null) {
            roots.add(cwd.getParent());
            if (cwd.getParent().getParent() != null) {
                roots.add(cwd.getParent().getParent());
            }
        }
        return roots;
    }

    private static void merge(LinkedHashMap<String, UploadAssetEntry> target, List<UploadAssetEntry> entries) {
        for (UploadAssetEntry entry : entries) {
            if (entry != null) {
                target.putIfAbsent(entry.deduplicationKey(), entry);
            }
        }
    }

    private static String firstText(JsonNode node, String... fields) {
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

    private static boolean boolValue(JsonNode node, boolean defaultValue, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value.asBoolean(defaultValue);
            }
        }
        return defaultValue;
    }

    private static Map<String, String> findNameOverrides(Path path, Map<Path, Map<String, String>> overrides) {
        if (path == null || overrides == null || overrides.isEmpty()) {
            return Map.of();
        }

        Path current = path.getParent();
        while (current != null) {
            Map<String, String> names = overrides.get(current.toAbsolutePath().normalize());
            if (names != null && !names.isEmpty()) {
                return names;
            }
            current = current.getParent();
        }
        return Map.of();
    }

    private static Map<Path, Map<String, String>> discoverNameOverrideFiles(Path uploadsRoot) {
        Map<Path, Map<String, String>> overrides = new HashMap<>();
        if (uploadsRoot == null || !Files.isDirectory(uploadsRoot)) {
            return overrides;
        }

        Path assetsRoot = uploadsRoot.resolve("assets");
        if (!Files.isDirectory(assetsRoot)) {
            return overrides;
        }

        try (Stream<Path> walk = Files.walk(assetsRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> {
                        String filename = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
                        return filename.startsWith("names") && filename.endsWith(".json");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> {
                        Map<String, String> names = loadNameOverrides(path);
                        if (!names.isEmpty()) {
                            overrides.put(path.getParent().toAbsolutePath().normalize(), names);
                        }
                    });
        } catch (IOException ignored) {
        }

        return overrides;
    }

    private static Map<String, String> loadNameOverrides(Path file) {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        try (var in = Files.newInputStream(file)) {
            JsonNode root = MAPPER.readTree(in);
            collectNameOverrides(root, names, null);
        } catch (Exception ignored) {
        }
        return names;
    }

    private static void collectNameOverrides(JsonNode node, Map<String, String> names, String prefix) {
        if (node == null || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            String directName = firstText(node, "name", "title", "label", "text", "ru");
            if (directName != null && prefix != null) {
                addNameAlias(names, prefix, directName);
            }
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                String nextPrefix = prefix == null ? key : prefix + "/" + key;
                if (value != null && value.isValueNode()) {
                    String text = value.asText();
                    if (text != null && !text.isBlank()) {
                        addNameAlias(names, nextPrefix, text);
                    }
                } else if (value != null && value.isObject()) {
                    String nestedName = firstText(value, "name", "title", "label", "text", "ru");
                    if (nestedName != null) {
                        addNameAlias(names, nextPrefix, nestedName);
                        addNameAlias(names, key, nestedName);
                    }
                    collectNameOverrides(value, names, nextPrefix);
                } else if (value != null && value.isArray()) {
                    collectNameOverrides(value, names, nextPrefix);
                }
            });
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                if (item != null && item.isObject()) {
                    String key = firstText(item, "id", "key", "file", "path", "src", "image", "imageUrl");
                    String value = firstText(item, "name", "title", "label", "text", "ru");
                    if (key != null && value != null) {
                        addNameAlias(names, key, value);
                        continue;
                    }
                }
                collectNameOverrides(item, names, prefix);
            }
        }
    }

    private static void addNameAlias(Map<String, String> names, String key, String value) {
        String normalizedValue = value == null ? null : value.trim();
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return;
        }
        for (String alias : nameAliases(key)) {
            if (alias != null && !alias.isBlank()) {
                names.put(normalizeLookupKey(alias), normalizedValue);
            }
        }
    }

    private static List<String> nameAliases(String key) {
        if (key == null || key.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        String normalized = key.replace('\\', '/').trim();
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        aliases.add(normalized);
        aliases.add(stripExtension(normalized));
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash < normalized.length() - 1) {
            String tail = normalized.substring(slash + 1);
            aliases.add(tail);
            aliases.add(stripExtension(tail));
        }
        return new ArrayList<>(aliases);
    }

    private static String resolveNameOverride(Map<String, String> nameOverrides,
                                              String currentName,
                                              String id,
                                              Path imagePath,
                                              String relativePath) {
        if (nameOverrides == null || nameOverrides.isEmpty()) {
            return currentName;
        }

        List<String> candidates = new ArrayList<>();
        if (relativePath != null && !relativePath.isBlank()) {
            candidates.add(relativePath);
        }
        if (imagePath != null && imagePath.getFileName() != null) {
            candidates.add(imagePath.getFileName().toString());
        }
        if (id != null && !id.isBlank()) {
            candidates.add(id);
        }
        if (currentName != null && !currentName.isBlank()) {
            candidates.add(currentName);
        }

        for (String candidate : candidates) {
            String override = nameOverrides.get(normalizeLookupKey(candidate));
            if (override != null && !override.isBlank()) {
                return override;
            }
        }
        return currentName;
    }

    private static String normalizeLookupKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.replace('\\', '/').trim().toLowerCase(Locale.ROOT);
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static int intValue(JsonNode node, int defaultValue, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                Integer parsed = readInt(value);
                if (parsed != null) {
                    return Math.max(1, parsed);
                }
            }
        }
        return defaultValue;
    }

    private static int[] readDimensions(JsonNode node) {
        int width = intValue(node, 1, "width", "w", "gridWidth", "cols", "tilesX", "tileWidth");
        int height = intValue(node, 1, "height", "h", "gridHeight", "rows", "tilesY", "tileHeight");

        int[] size = parseSizeNode(firstNode(node, "size", "gridSize", "dimensions", "dimension", "tileSize"));
        if (size != null) {
            if (width <= 1 && height <= 1) {
                width = size[0];
                height = size[1];
            } else {
                if (width <= 1) {
                    width = size[0];
                }
                if (height <= 1) {
                    height = size[1];
                }
            }
        }

        if (width <= 1 && height > 1) {
            width = height;
        }
        if (height <= 1 && width > 1) {
            height = width;
        }

        return new int[] {Math.max(1, width), Math.max(1, height)};
    }

    private static Integer readInt(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong() || node.isShort() || node.isBigInteger() || node.isNumber()) {
            return node.asInt();
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            int idx = text.indexOf('x');
            if (idx < 0) {
                idx = text.indexOf('X');
            }
            if (idx > 0) {
                try {
                    return Integer.parseInt(text.substring(0, idx).trim());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static JsonNode firstNode(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private static int[] parseSizeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            int value = Math.max(1, node.asInt(1));
            return new int[] {value, value};
        }
        if (node.isArray() && node.size() >= 2) {
            Integer first = readInt(node.get(0));
            Integer second = readInt(node.get(1));
            if (first != null || second != null) {
                int a = Math.max(1, first == null ? 1 : first);
                int b = Math.max(1, second == null ? a : second);
                return new int[] {a, b};
            }
        }
        if (node.isObject()) {
            int w = intValue(node, 1, "width", "w", "x", "cols", "tilesX", "tileWidth");
            int h = intValue(node, 1, "height", "h", "y", "rows", "tilesY", "tileHeight");
            if (w > 1 || h > 1) {
                if (w <= 1) w = h;
                if (h <= 1) h = w;
                return new int[] {Math.max(1, w), Math.max(1, h)};
            }
            JsonNode nested = firstNode(node, "size", "gridSize", "dimensions", "dimension");
            if (nested != null && nested != node) {
                return parseSizeNode(nested);
            }
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (!text.isEmpty()) {
                String normalized = text.toLowerCase(Locale.ROOT).replace('\u00D7', 'x');
                java.util.regex.Matcher matcher = Pattern.compile("(\\d+)\\s*[x\\u0445]\\s*(\\d+)").matcher(normalized);
                if (matcher.find()) {
                    try {
                        return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
                    } catch (NumberFormatException ignored) {
                    }
                }
                try {
                    int value = Integer.parseInt(normalized);
                    value = Math.max(1, value);
                    return new int[] {value, value};
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private static Path resolveReference(Path projectRoot, Path manifest, String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }

        String normalizedReference = reference.replace('\\', '/');
        Path refPath = Paths.get(normalizedReference);
        if (refPath.isAbsolute()) {
            return refPath.normalize();
        }

        if (normalizedReference.startsWith("uploads/")) {
            return projectRoot.toAbsolutePath().normalize().resolve(refPath).normalize();
        }

        Path manifestDir = manifest == null ? projectRoot.toAbsolutePath().normalize() : manifest.getParent();
        if (manifestDir == null) {
            manifestDir = projectRoot.toAbsolutePath().normalize();
        }
        return manifestDir.resolve(refPath).normalize();
    }

    private static String deriveId(UploadAssetKind kind, String category, String name, Path imagePath) {
        String source = String.join("-",
                kind == null ? "asset" : kind.name().toLowerCase(Locale.ROOT),
                category == null ? "misc" : category,
                name == null ? (imagePath == null ? "asset" : stripExtension(imagePath.getFileName().toString())) : name);
        return toId(source);
    }

    private static String deriveCategory(Path projectRoot, Path path) {
        if (path == null) {
            return "misc";
        }
        Path relative = relativize(projectRoot, path);
        if (relative == null || relative.getNameCount() < 2) {
            return "misc";
        }
        if (relative.getNameCount() >= 3 && relative.getName(0).toString().equalsIgnoreCase("uploads")
                && relative.getName(1).toString().equalsIgnoreCase("assets")) {
            return relative.getName(2).toString().toLowerCase(Locale.ROOT);
        }
        Path parent = path.getParent();
        if (parent != null) {
            Path parentRelative = relativize(projectRoot, parent);
            if (parentRelative != null && parentRelative.getNameCount() >= 3
                    && parentRelative.getName(0).toString().equalsIgnoreCase("uploads")
                    && parentRelative.getName(1).toString().equalsIgnoreCase("assets")) {
                return parentRelative.getName(parentRelative.getNameCount() - 1).toString().toLowerCase(Locale.ROOT);
            }
        }
        return relative.getName(0).toString().toLowerCase(Locale.ROOT);
    }

    private static Path relativize(Path root, Path path) {
        if (root == null || path == null) {
            return null;
        }
        try {
            return root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        } catch (Exception e) {
            return null;
        }
    }

    private static String toRelativeProjectPath(Path projectRoot, Path path) {
        Path relative = relativize(projectRoot, path);
        return relative == null ? null : relative.toString().replace('\\', '/');
    }

    private static int[] parseSizeFromName(String baseName) {
        if (baseName == null) {
            return new int[] {1, 1};
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(".*\\((\\d+)x(\\d+)\\).*", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(baseName);
        if (matcher.matches()) {
            try {
                return new int[] {Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))};
            } catch (NumberFormatException ignored) {
            }
        }
        return new int[] {1, 1};
    }

    private static String stripExtension(String fileName) {
        if (fileName == null) {
            return "asset";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    private static String toId(String source) {
        return source == null ? "asset" : source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    public enum UploadAssetKind {
        TOKEN(true),
        OBJECT(false),
        TERRAIN(true),
        WALL(true),
        OTHER(false);

        private final boolean blocksByDefault;

        UploadAssetKind(boolean blocksByDefault) {
            this.blocksByDefault = blocksByDefault;
        }

        public boolean blocksByDefault() {
            return blocksByDefault;
        }

        public static UploadAssetKind fromText(String kind, String category, String name) {
            StringBuilder source = new StringBuilder();
            if (kind != null) source.append(kind).append(' ');
            if (category != null) source.append(category).append(' ');
            if (name != null) source.append(name);
            String text = source.toString().toLowerCase(Locale.ROOT);
            if (text.contains("token") || text.contains("creature") || text.contains("player") || text.contains("npc")) {
                return TOKEN;
            }
            if (text.contains("wall") || text.contains("door") || text.contains("fence")) {
                return WALL;
            }
            if (text.contains("terrain") || text.contains("ground") || text.contains("floor")) {
                return TERRAIN;
            }
            if (text.contains("object") || text.contains("prop") || text.contains("furniture")) {
                return OBJECT;
            }
            if (kind != null) {
                try {
                    return UploadAssetKind.valueOf(kind.trim().toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return OTHER;
        }
    }

    public record UploadAssetEntry(
            String id,
            String name,
            String category,
            UploadAssetKind kind,
            Path imagePath,
            String relativePath,
            int width,
            int height,
            boolean blocksMovement,
            boolean blocksSight,
            Path manifestPath
    ) {
        public String deduplicationKey() {
            if (id != null && !id.isBlank()) {
                return id;
            }
            if (relativePath != null && !relativePath.isBlank()) {
                return relativePath;
            }
            if (imagePath != null) {
                return imagePath.toAbsolutePath().normalize().toString();
            }
            return name == null ? null : name.toLowerCase(Locale.ROOT);
        }
    }
}
