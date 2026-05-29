package com.avalon.dnd.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Раздаёт статические ресурсы:
 *  /uploads/** — данные карт и сессий из корня проекта
 *  /assets/**   — общие ассеты из корня проекта
 *
 * Поддерживает запуск из любого подпроекта, поэтому ищет корень проекта
 * вверх по дереву по наличию uploads/assets и Gradle wrapper файлов.
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(resolveUploadLocations())
                .setCachePeriod(3600);

        registry.addResourceHandler("/assets/**")
                .addResourceLocations(resolveAssetLocations())
                .setCachePeriod(86400);
    }

    private String[] resolveUploadLocations() {
        List<String> locations = new ArrayList<>();
        for (Path root : resolveProjectRoots()) {
            Path uploadDir = root.resolve("uploads").toAbsolutePath().normalize();
            locations.add("file:" + ensureTrailingSlash(uploadDir));
        }
        locations.add("classpath:/uploads/");
        return locations.toArray(String[]::new);
    }

    private String[] resolveAssetLocations() {
        List<String> locations = new ArrayList<>();
        for (Path root : resolveProjectRoots()) {
            Path uploadAssets = root.resolve("uploads/assets").toAbsolutePath().normalize();
            locations.add("file:" + ensureTrailingSlash(uploadAssets));
        }
        locations.add("classpath:/assets/");
        return locations.toArray(String[]::new);
    }

    private List<Path> resolveProjectRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        addRoot(roots, System.getProperty("avalon.project.root"));
        addRoot(roots, System.getenv("AVALON_PROJECT_ROOT"));

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        Path projectRoot = findProjectRoot(cwd);
        if (projectRoot != null) {
            roots.add(projectRoot);
        }

        return new ArrayList<>(roots);
    }

    private void addRoot(Set<Path> roots, String raw) {
        if (raw == null || raw.isBlank()) return;
        try {
            Path p = Path.of(raw).toAbsolutePath().normalize();
            Path projectRoot = findProjectRoot(p);
            if (projectRoot != null) {
                roots.add(projectRoot);
            }
        } catch (Exception ignored) {
        }
    }

    private Path findProjectRoot(Path start) {
        if (start == null) return null;
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

    private String ensureTrailingSlash(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
