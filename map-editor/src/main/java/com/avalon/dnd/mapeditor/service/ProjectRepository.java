package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.MicroLocationDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ProjectRepository {

    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .findAndRegisterModules();

    public void save(Path path, MapProject project) throws IOException {
        if (project == null || path == null) {
            return;
        }
        if (Files.isDirectory(path)) {
            saveWorkspace(path, project);
            return;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), BattleProjectMapper.toDto(project));
    }

    public MapProject load(Path path) throws IOException {
        if (path == null) {
            return MapProject.createBlank(null, "Untitled Map");
        }
        if (Files.isDirectory(path)) {
            return loadWorkspace(path);
        }

        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        Path parent = path.getParent();
        if ("map.json".equalsIgnoreCase(fileName) && parent != null) {
            Path microLocations = parent.resolve("microLocations.json");
            Path interiors = parent.resolve("interiors");
            if (Files.exists(microLocations) || Files.isDirectory(interiors)) {
                return loadWorkspace(parent);
            }
        }

        try {
            BattleProjectDto dto = mapper.readValue(path.toFile(), BattleProjectDto.class);
            return BattleProjectMapper.fromDto(dto);
        } catch (Exception ex) {
            return mapper.readValue(path.toFile(), MapProject.class);
        }
    }

    public MapProject loadWorkspace(Path rootOrMapFile) throws IOException {
        Path root = workspaceRoot(rootOrMapFile);
        Path mapFile = root.resolve("map.json");
        if (!Files.exists(mapFile)) {
            if (Files.exists(rootOrMapFile) && Files.isRegularFile(rootOrMapFile)) {
                return load(rootOrMapFile);
            }
            throw new IOException("map.json not found in workspace: " + root);
        }

        BattleProjectDto dto = mapper.readValue(mapFile.toFile(), BattleProjectDto.class);
        MapProject project = BattleProjectMapper.fromDto(dto);

        Path microLocationsFile = root.resolve("microLocations.json");
        if (Files.exists(microLocationsFile)) {
            project.setMicroLocations(readMicroLocations(microLocationsFile));
        }
        return project;
    }

    public Path saveWorkspace(Path rootDir, MapProject project) throws IOException {
        if (project == null) {
            throw new IllegalArgumentException("project is null");
        }
        Path root = workspaceRoot(rootDir);
        Files.createDirectories(root);
        Files.createDirectories(root.resolve("interiors"));

        BattleProjectDto dto = BattleProjectMapper.toDto(project);
        dto.setMicroLocations(new ArrayList<>());

        Path mapFile = root.resolve("map.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(mapFile.toFile(), dto);

        Path microLocationsFile = root.resolve("microLocations.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(microLocationsFile.toFile(), new ArrayList<>(project.getMicroLocations()));
        return root;
    }

    public Path saveFinished(MapProject project) throws IOException {
        return saveWorkspace(finishedDir().resolve(projectFileName(project, "finished")), project);
    }

    public Path saveBackup(MapProject project) throws IOException {
        String stamp = BACKUP_FORMAT.format(LocalDateTime.now());
        return saveWorkspace(backupsDir().resolve(projectFileName(project, "backup") + "_" + stamp), project);
    }

    public void saveLayout(Path path, MapLayoutUpdateDto layout) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), layout);
    }

    public MapLayoutUpdateDto loadLayout(Path path) throws IOException {
        return mapper.readValue(path.toFile(), MapLayoutUpdateDto.class);
    }

    public Path resolveChild(Path rootDir, String relativeOrAbsolute) {
        if (relativeOrAbsolute == null || relativeOrAbsolute.isBlank()) {
            return null;
        }
        Path path = Paths.get(relativeOrAbsolute);
        if (path.isAbsolute() || rootDir == null) {
            return path.normalize();
        }
        return workspaceRoot(rootDir).resolve(path).normalize();
    }

    public Path toWorkspaceRelative(Path rootDir, Path child) {
        if (child == null) {
            return null;
        }
        Path root = workspaceRoot(rootDir);
        if (root == null) {
            return child;
        }
        try {
            return root.relativize(child.toAbsolutePath().normalize());
        } catch (Exception ex) {
            return child;
        }
    }

    private List<MicroLocationDto> readMicroLocations(Path path) throws IOException {
        JavaType type = mapper.getTypeFactory().constructCollectionType(List.class, MicroLocationDto.class);
        List<MicroLocationDto> list = mapper.readValue(path.toFile(), type);
        return list == null ? List.of() : list;
    }

    private Path workspaceRoot(Path path) {
        if (path == null) {
            return Paths.get("").toAbsolutePath().normalize();
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            return normalized;
        }
        Path parent = normalized.getParent();
        return parent == null ? normalized : parent;
    }

    private Path finishedDir() {
        return Paths.get("uploads/maps/finished").toAbsolutePath().normalize();
    }

    private Path backupsDir() {
        return Paths.get("uploads/maps/backups").toAbsolutePath().normalize();
    }

    private String projectFileName(MapProject project, String fallback) {
        if (project == null) {
            return fallback;
        }
        String id = safeStem(project.getId());
        String name = safeStem(project.getName());
        if (!name.isBlank() && !name.equals(id)) {
            return id + "_" + name;
        }
        return !id.isBlank() ? id : fallback;
    }

    private String safeStem(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String stem = value.trim().replaceAll("[\\/]+", "_");
        stem = stem.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}._-]+", "_");
        stem = stem.replaceAll("_+", "_").replaceAll("^_+|_+$", "");
        return stem;
    }
}
