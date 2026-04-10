package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ProjectRepository {

    private final ObjectMapper mapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .findAndRegisterModules();

    public void save(Path path, MapProject project) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), project);
    }

    public MapProject load(Path path) throws IOException {
        return mapper.readValue(path.toFile(), MapProject.class);
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
}
