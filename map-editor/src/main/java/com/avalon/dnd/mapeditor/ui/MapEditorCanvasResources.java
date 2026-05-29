package com.avalon.dnd.mapeditor.ui;

import javafx.application.Platform;
import javafx.scene.image.Image;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Small image/path resolver extracted from MapEditorCanvas.
 */
public final class MapEditorCanvasResources {

    private MapEditorCanvasResources() {
    }

    public static Image loadImage(String url) {
        return loadImage(url, null);
    }

    public static Image loadImage(String url, Runnable onLoaded) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String resolved = resolveImageSource(url.trim());
        if (resolved == null || resolved.isBlank()) {
            return null;
        }
        try {
            Image image = new Image(resolved, true);
            if (onLoaded != null) {
                AtomicBoolean fired = new AtomicBoolean(false);
                Runnable fire = () -> {
                    if (fired.compareAndSet(false, true)) {
                        Platform.runLater(onLoaded);
                    }
                };
                image.progressProperty().addListener((obs, oldV, progress) -> {
                    if (progress != null && progress.doubleValue() >= 1.0) {
                        fire.run();
                    }
                });
                if (image.getProgress() >= 1.0) {
                    fire.run();
                }
            }
            return image;
        } catch (Exception ex) {
            return null;
        }
    }

    public static String resolveImageSource(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("jar:") || url.startsWith("file:") || url.startsWith("data:")) {
            return encodeUrl(url);
        }

        String cleaned = url.replace('\\', '/');
        String cleanedNoSlash = cleaned.startsWith("/") ? cleaned.substring(1) : cleaned;
        if (cleaned.startsWith("/uploads/") || cleaned.startsWith("uploads/")) {
            Path local = resolveProjectPath(cleanedNoSlash);
            if (local != null) {
                return local.toUri().toString();
            }
            return encodeUrl(cleaned.startsWith("/") ? cleaned : "/" + cleaned);
        }

        Path local = resolveProjectPath(cleanedNoSlash);
        if (local != null) {
            return local.toUri().toString();
        }

        Path assetLocal = resolveProjectPath("uploads/assets/" + cleanedNoSlash);
        if (assetLocal != null) {
            return assetLocal.toUri().toString();
        }

        return encodeUrl(cleaned);
    }

    public static Path resolveProjectPath(String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        String cleaned = relative.startsWith("/") ? relative.substring(1) : relative;

        Path projectRoot = findProjectRoot();
        if (projectRoot != null) {
            Path candidate = projectRoot.resolve(cleaned).normalize();
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        Path direct = Path.of(cleaned);
        if (Files.exists(direct)) {
            return direct.toAbsolutePath().normalize();
        }
        return null;
    }


    private static Path findProjectRoot() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (Path current = cwd; current != null; current = current.getParent()) {
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

    // no-op helper removed; project root resolution is handled directly above.

    public static String encodeUrl(String url) {
        if (url == null) {
            return null;
        }
        try {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd < 0) {
                return url;
            }
            int pathStart = url.indexOf('/', schemeEnd + 3);
            if (pathStart < 0) {
                return url;
            }

            String base = url.substring(0, pathStart);
            String path = url.substring(pathStart);

            String[] segments = path.split("/", -1);
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) {
                    sb.append('/');
                }
                sb.append(encodePathSegment(segments[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return url;
        }
    }

    private static String encodePathSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return segment == null ? "" : segment;
        }
        try {
            return new URI(null, null, "/" + segment, null).toASCIIString().substring(1);
        } catch (Exception e) {
            byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int v = b & 0xFF;
                if ((v >= 'A' && v <= 'Z') || (v >= 'a' && v <= 'z') ||
                        (v >= '0' && v <= '9') || v == '-' || v == '_' || v == '.' || v == '~' || v == '+') {
                    sb.append((char) v);
                } else {
                    sb.append(String.format("%%%02X", v));
                }
            }
            return sb.toString();
        }
    }
}
