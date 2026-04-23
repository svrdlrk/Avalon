package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.BattleProjectExportDto;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ProjectRepository {

    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .findAndRegisterModules();

    public void save(Path path, MapProject project) throws IOException {
        if (project == null) {
            return;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), BattleProjectMapper.toDto(project));
    }

    public MapProject load(Path path) throws IOException {
        try {
            BattleProjectExportDto dto = mapper.readValue(path.toFile(), BattleProjectExportDto.class);
            return BattleProjectMapper.fromDto(dto);
        } catch (Exception ex) {
            return mapper.readValue(path.toFile(), MapProject.class);
        }
    }

    public Path saveFinished(MapProject project) throws IOException {
        return saveCanonical(project, finishedDir(), projectFileName(project, "finished"));
    }

    public Path saveBackup(MapProject project) throws IOException {
        String stamp = BACKUP_FORMAT.format(LocalDateTime.now());
        return saveCanonical(project, backupsDir(), projectFileName(project, "backup") + "_" + stamp);
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

    private Path saveCanonical(MapProject project, Path dir, String stem) throws IOException {
        if (project == null) {
            throw new IllegalArgumentException("project is null");
        }
        Files.createDirectories(dir);
        Path out = dir.resolve(stem + ".json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), BattleProjectMapper.toDto(project));
        return out;
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
