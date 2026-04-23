package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.AssetCatalog;
import com.avalon.dnd.mapeditor.model.AssetDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class ReferenceImageLibrary {

    private static final String[] IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp"};

    private ReferenceImageLibrary() {
    }

    public static List<String> discoverReferenceImages() {
        Set<String> references = new LinkedHashSet<>();
        for (Path projectRoot : candidateProjectRoots()) {
            Path referenceRoot = projectRoot.resolve("uploads/maps/reference");
            if (!Files.isDirectory(referenceRoot)) {
                continue;
            }

            collectFromCatalog(projectRoot, references);
            if (references.isEmpty()) {
                collectFromDirectory(projectRoot, referenceRoot, references);
            }
        }
        return new ArrayList<>(references);
    }

    public static String findLatestReferenceImage() {
        ReferenceCandidate latest = null;
        for (Path projectRoot : candidateProjectRoots()) {
            Path referenceRoot = projectRoot.resolve("uploads/maps/reference");
            if (!Files.isDirectory(referenceRoot)) {
                continue;
            }

            List<Path> images = referenceImagesFromCatalog(projectRoot);
            if (images.isEmpty()) {
                images = referenceImagesFromDirectory(referenceRoot);
            }

            for (Path image : images) {
                ReferenceCandidate candidate = new ReferenceCandidate(
                        toProjectRelativePath(projectRoot, image),
                        lastModified(image)
                );
                if (latest == null || candidate.isNewerThan(latest)) {
                    latest = candidate;
                }
            }
        }
        return latest == null ? null : latest.path();
    }

    private static void collectFromCatalog(Path projectRoot, Set<String> references) {
        for (Path image : referenceImagesFromCatalog(projectRoot)) {
            references.add(toProjectRelativePath(projectRoot, image));
        }
    }

    private static List<Path> referenceImagesFromCatalog(Path projectRoot) {
        Path catalog = projectRoot.resolve("uploads/maps/reference/catalog.json");
        if (!Files.isRegularFile(catalog)) {
            return List.of();
        }
        try {
            AssetCatalog assetCatalog = AssetCatalogLoader.loadFromJson(catalog);
            List<Path> images = new ArrayList<>();
            for (AssetDefinition asset : assetCatalog.getAssets()) {
                Path resolved = resolveReferencePath(projectRoot, asset.getImageUrl());
                if (resolved != null && Files.isRegularFile(resolved)) {
                    images.add(resolved);
                }
            }
            images.sort(Comparator.comparing(Path::toString));
            return images;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static void collectFromDirectory(Path projectRoot, Path referenceRoot, Set<String> references) {
        try (Stream<Path> walk = Files.walk(referenceRoot)) {
            walk.filter(Files::isRegularFile)
                    .filter(ReferenceImageLibrary::isImageFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(image -> references.add(toProjectRelativePath(projectRoot, image)));
        } catch (IOException ignored) {
        }
    }

    private static List<Path> referenceImagesFromDirectory(Path referenceRoot) {
        try (Stream<Path> walk = Files.walk(referenceRoot)) {
            return walk.filter(Files::isRegularFile)
                    .filter(ReferenceImageLibrary::isImageFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private static List<Path> candidateProjectRoots() {
        java.util.LinkedHashSet<Path> roots = new java.util.LinkedHashSet<>();
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

    private static boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        for (String suffix : IMAGE_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static Path resolveReferencePath(Path projectRoot, String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String normalized = imageUrl.trim().replace('\\', '/');
        if (normalized.startsWith("http://") || normalized.startsWith("https://") || normalized.startsWith("jar:") || normalized.startsWith("file:")) {
            return null;
        }
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        Path local = projectRoot.resolve(normalized).normalize();
        return Files.exists(local) ? local : null;
    }

    private static String toProjectRelativePath(Path projectRoot, Path image) {
        try {
            return projectRoot.relativize(image.toAbsolutePath().normalize()).toString().replace('\\', '/');
        } catch (Exception e) {
            return image.toAbsolutePath().normalize().toString().replace('\\', '/');
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return Long.MIN_VALUE;
        }
    }

    private record ReferenceCandidate(String path, long modifiedAt) {
        boolean isNewerThan(ReferenceCandidate other) {
            if (other == null) {
                return true;
            }
            if (modifiedAt != other.modifiedAt) {
                return modifiedAt > other.modifiedAt;
            }
            return path.compareToIgnoreCase(other.path) < 0;
        }
    }
}
