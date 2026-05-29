package com.avalon.dnd.shared.uploads;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shared project-root discovery helpers used by asset catalog loaders/controllers.
 */
public final class AssetCatalogPathSupport {

    private AssetCatalogPathSupport() {
    }

    public static List<Path> resolveCandidates(List<Path> relativeCandidates) {
        Set<Path> resolved = new java.util.LinkedHashSet<>();
        for (Path root : projectRoots()) {
            for (Path expandedRoot : expandCandidateRoots(root)) {
                for (Path candidate : relativeCandidates) {
                    resolved.add(resolveAgainstRoot(expandedRoot, candidate));
                }
            }
        }
        for (Path candidate : relativeCandidates) {
            if (candidate.isAbsolute()) {
                resolved.add(candidate.toAbsolutePath().normalize());
            }
        }
        return new ArrayList<>(resolved);
    }

    public static List<Path> projectRoots() {
        Set<Path> roots = new java.util.LinkedHashSet<>();
        addRoot(roots, System.getProperty("avalon.project.root"));
        addRoot(roots, System.getenv("AVALON_PROJECT_ROOT"));

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path current = cwd;
        while (current != null) {
            addDiscoveredRoot(roots, current);
            current = current.getParent();
        }

        if (roots.isEmpty()) {
            roots.add(cwd);
        }
        return new ArrayList<>(roots);
    }

    public static Path resolveAgainstRoot(Path root, Path candidate) {
        if (candidate.isAbsolute()) {
            return candidate.toAbsolutePath().normalize();
        }
        return root.resolve(candidate).toAbsolutePath().normalize();
    }

    private static List<Path> expandCandidateRoots(Path root) {
        List<Path> roots = new ArrayList<>();
        if (root == null) {
            return roots;
        }
        try {
            Path normalized = root.toAbsolutePath().normalize();
            roots.add(normalized);

            Path serverDir = normalized.resolve("server");
            if (Files.isDirectory(serverDir)) {
                roots.add(serverDir);
                Path serverResources = serverDir.resolve("src/main/resources");
                if (Files.isDirectory(serverResources)) {
                    roots.add(serverResources);
                }
            }

            Path mapEditorDir = normalized.resolve("map-editor");
            if (Files.isDirectory(mapEditorDir)) {
                roots.add(mapEditorDir);
            }

            Path dmClientDir = normalized.resolve("dm-client");
            if (Files.isDirectory(dmClientDir)) {
                roots.add(dmClientDir);
            }

            Path playerClientDir = normalized.resolve("player-client");
            if (Files.isDirectory(playerClientDir)) {
                roots.add(playerClientDir);
            }
        } catch (Exception ignored) {
        }
        return roots;
    }

    private static void addRoot(Set<Path> roots, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
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

    private static void addDiscoveredRoot(Set<Path> roots, Path dir) {
        if (dir == null) {
            return;
        }
        try {
            Path normalized = dir.toAbsolutePath().normalize();
            if (looksLikeProjectRoot(normalized) || hasRuntimeAssetDirs(normalized)) {
                roots.add(normalized);
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean hasRuntimeAssetDirs(Path dir) {
        return Files.exists(dir.resolve("uploads"))
                || Files.exists(dir.resolve("assets"))
                || Files.exists(dir.resolve("server/uploads"))
                || Files.exists(dir.resolve("server/src/main/resources/assets"));
    }

    private static boolean looksLikeProjectRoot(Path dir) {
        return Files.exists(dir.resolve("gradlew.bat"))
                || Files.exists(dir.resolve("settings.gradle"))
                || Files.exists(dir.resolve("build.gradle"))
                || Files.exists(dir.resolve("uploads"));
    }
}
