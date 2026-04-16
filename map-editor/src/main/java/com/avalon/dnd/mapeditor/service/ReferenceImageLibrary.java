package com.avalon.dnd.mapeditor.service;

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

            try (Stream<Path> walk = Files.walk(referenceRoot)) {
                walk.filter(Files::isRegularFile)
                        .filter(ReferenceImageLibrary::isImageFile)
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(image -> references.add(toProjectRelativePath(projectRoot, image)));
            } catch (IOException ignored) {
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

            try (Stream<Path> walk = Files.walk(referenceRoot)) {
                for (Path image : walk.filter(Files::isRegularFile).filter(ReferenceImageLibrary::isImageFile).toList()) {
                    ReferenceCandidate candidate = new ReferenceCandidate(
                            toProjectRelativePath(projectRoot, image),
                            lastModified(image)
                    );
                    if (latest == null || candidate.isNewerThan(latest)) {
                        latest = candidate;
                    }
                }
            } catch (IOException ignored) {
            }
        }
        return latest == null ? null : latest.path();
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

    private static boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        for (String suffix : IMAGE_SUFFIXES) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
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
