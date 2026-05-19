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
 *  /uploads/**  — загруженные пользователем карты и ассеты
 *  /assets/**   — встроенные токены и объекты
 *
 * Поддерживает запуск как из корня репозитория, так и из подпроектов
 * (например server/, dm-client/), поэтому ищет uploads/ вверх по дереву.
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

            Path sourceAssets = root.resolve("server/src/main/resources/assets").toAbsolutePath().normalize();
            locations.add("file:" + ensureTrailingSlash(sourceAssets));
        }
        locations.add("classpath:/assets/");
        return locations.toArray(String[]::new);
    }

    private List<Path> resolveProjectRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        addRoot(roots, System.getProperty("avalon.project.root"));
        addRoot(roots, System.getenv("AVALON_PROJECT_ROOT"));

        Path cwd = Paths.get("").toAbsolutePath().normalize();
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

    private void addRoot(Set<Path> roots, String raw) {
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

    private boolean looksLikeProjectRoot(Path dir) {
        return Files.exists(dir.resolve("gradlew.bat"))
                || Files.exists(dir.resolve("settings.gradle"))
                || Files.exists(dir.resolve("build.gradle"))
                || Files.exists(dir.resolve("uploads"));
    }

    private String ensureTrailingSlash(Path path) {
        String normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/');
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }
}
