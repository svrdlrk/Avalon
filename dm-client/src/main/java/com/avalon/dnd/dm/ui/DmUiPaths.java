package com.avalon.dnd.dm.ui;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Path helpers extracted from DM UI classes.
 */
public final class DmUiPaths {

    private DmUiPaths() {
    }

    public static File resolveProjectUploadsDir(String relative) {
        try {
            Path cleaned = Path.of(relative == null ? "" : relative);
            Path root = findProjectRoot();
            if (root != null) {
                Path candidate = root.resolve(cleaned).toAbsolutePath().normalize();
                if (Files.exists(candidate)) {
                    return candidate.toFile();
                }
            }
        } catch (Exception ignored) {
        }
        return new File(relative == null ? "" : relative);
    }

    private static Path findProjectRoot() {
        Path current = Paths.get("").toAbsolutePath().normalize();
        for (; current != null; current = current.getParent()) {
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
}
