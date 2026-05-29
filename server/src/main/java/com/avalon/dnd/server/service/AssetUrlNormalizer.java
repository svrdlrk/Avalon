package com.avalon.dnd.server.service;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class AssetUrlNormalizer {

    private AssetUrlNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return null;

        String value = raw.trim().replace('\\', '/');
        if (value.isBlank()) return null;

        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:") || value.startsWith("jar:")) {
            return value;
        }

        String extracted = extractKnownWebPath(value);
        if (extracted != null) {
            return extracted;
        }

        if (value.startsWith("file:")) {
            String filePath = extractFileWebPath(value);
            return filePath != null ? filePath : value;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/maps/") || lower.startsWith("maps/")) {
            String noSlash = value.replaceFirst("^/+", "");
            return "/uploads/" + noSlash;
        }

        if (value.startsWith("/") && !looksLikeFilesystemPath(value)) {
            return value;
        }

        String filePath = extractFileWebPath(value);
        if (filePath != null) {
            return filePath;
        }

        if (looksLikeFilesystemPath(value)) {
            Path path = Path.of(value);
            Path fileName = path.getFileName();
            if (fileName != null) {
                String filename = fileName.toString();
                String matched = resolveFilesystemAssetUrl(filename);
                if (matched != null) {
                    return matched;
                }
                return "/uploads/assets/" + filename;
            }
        }

        String matched = resolveFilesystemAssetUrl(value.replaceFirst("^/+", ""));
        if (matched != null) {
            return matched;
        }

        return "/uploads/assets/" + value.replaceFirst("^/+", "");
    }

    public static String normalizeMapBackground(String backgroundUrl, com.fasterxml.jackson.databind.JsonNode referenceOverlayLayer) {
        String normalized = normalize(backgroundUrl);
        if (normalized != null) {
            return normalized;
        }
        return normalize(extractLayerImageUrl(referenceOverlayLayer));
    }

    public static String extractLayerImageUrl(com.fasterxml.jackson.databind.JsonNode layer) {
        if (layer == null || layer.isNull() || layer.isMissingNode()) {
            return null;
        }
        for (String key : new String[]{"imageUrl", "image", "path", "src", "url", "file", "imagePath", "assetPath", "backgroundUrl"}) {
            com.fasterxml.jackson.databind.JsonNode value = layer.get(key);
            if (value != null && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String extractKnownWebPath(String value) {
        String lower = value.toLowerCase(Locale.ROOT);

        for (String marker : new String[]{"/uploads/", "uploads/", "/assets/", "assets/"}) {
            int idx = lower.indexOf(marker);
            if (idx >= 0) {
                String slice = value.substring(idx).replaceFirst("^/+", "");
                return "/" + slice;
            }
        }
        return null;
    }

    private static String extractFileWebPath(String raw) {
        try {
            Path path = raw.startsWith("file:") ? Path.of(URI.create(raw)) : Path.of(raw);
            if (!Files.exists(path)) {
                return null;
            }
            Path normalized = path.toAbsolutePath().normalize();
            for (Path current = normalized; current != null; current = current.getParent()) {
                Path fileName = current.getFileName();
                if (fileName != null && "uploads".equalsIgnoreCase(fileName.toString())) {
                    Path rel = current.relativize(normalized);
                    String relPath = rel.toString().replace('\\', '/');
                    return "/uploads/" + relPath;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String resolveFilesystemAssetUrl(String cleaned) {
        if (cleaned == null || cleaned.isBlank()) {
            return null;
        }

        for (Path root : resolveProjectRoots()) {
            Path assetsRoot = root.resolve("uploads/assets").toAbsolutePath().normalize();
            Path[] candidates = new Path[] {
                    assetsRoot.resolve(cleaned),
                    assetsRoot.resolve("tokens").resolve(cleaned),
                    assetsRoot.resolve("objects").resolve(cleaned)
            };
            for (Path candidate : candidates) {
                try {
                    if (Files.exists(candidate)) {
                        String candidateText = candidate.toAbsolutePath().normalize().toString().replace('\\', '/');
                        int uploadsIdx = candidateText.toLowerCase(Locale.ROOT).indexOf("/uploads/");
                        if (uploadsIdx >= 0) {
                            return candidateText.substring(uploadsIdx);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    private static List<Path> resolveProjectRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        addProjectRoot(roots, System.getProperty("avalon.project.root"));
        addProjectRoot(roots, System.getenv("AVALON_PROJECT_ROOT"));

        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path current = cwd; current != null; current = current.getParent()) {
            addProjectRoot(roots, current);
        }
        return new java.util.ArrayList<>(roots);
    }

    private static void addProjectRoot(java.util.Set<Path> roots, String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            addProjectRoot(roots, Path.of(raw));
        } catch (Exception ignored) {
        }
    }

    private static void addProjectRoot(java.util.Set<Path> roots, Path candidate) {
        if (candidate == null) return;
        try {
            Path normalized = candidate.toAbsolutePath().normalize();
            if ((Files.exists(normalized.resolve("settings.gradle"))
                    || Files.exists(normalized.resolve("settings.gradle.kts"))
                    || Files.exists(normalized.resolve("gradlew"))
                    || Files.exists(normalized.resolve("gradlew.bat")))
                    && Files.isDirectory(normalized.resolve("uploads/assets"))) {
                roots.add(normalized);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean looksLikeFilesystemPath(String value) {
        return value.matches("^[a-zA-Z]:[\\\\/].*") || value.startsWith("/") || value.startsWith("\\\\");
    }
}
