package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.shared.MapLayoutUpdateDto;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Thin persistence facade for map-editor. Extracts repository calls away from the UI layer
 * so the editor pane can focus on user interaction and refresh logic.
 */
public class MapEditorPersistenceCoordinator {

    private final ProjectRepository repository;

    public MapEditorPersistenceCoordinator(ProjectRepository repository) {
        this.repository = repository;
    }

    public Path saveFinished(MapProject project, String folderName) throws IOException {
        return repository.saveFinished(project, folderName);
    }

    public Path saveBackup(MapProject project) throws IOException {
        return repository.saveBackup(project);
    }

    public MapProject loadWorkspace(Path root) throws IOException {
        return repository.loadWorkspace(root);
    }

    public Path finishedDir() {
        return repository.finishedDir();
    }

    public void saveLayout(Path path, MapLayoutUpdateDto layout) throws IOException {
        repository.saveLayout(path, layout);
    }

    public MapLayoutUpdateDto loadLayout(Path path) throws IOException {
        return repository.loadLayout(path);
    }
}
