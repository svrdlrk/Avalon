package com.avalon.dnd.mapeditor.ui;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.LayerKind;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.service.MapEditorPersistenceCoordinator;
import com.avalon.dnd.mapeditor.service.SharedProjectMapper;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Encapsulates workspace-level actions for the map editor so the pane can stay focused on UI composition.
 */
public final class MapEditorWorkspaceActions {

    private final EditorState state;
    private final MapEditorPersistenceCoordinator persistenceCoordinator;
    private final Supplier<Window> ownerSupplier;
    private final Supplier<String> currentDocumentTitle;
    private final Consumer<Path> documentRootSetter;
    private final Runnable refreshAfterProjectChange;
    private final BiConsumer<String, Exception> showError;

    public MapEditorWorkspaceActions(EditorState state,
                                     MapEditorPersistenceCoordinator persistenceCoordinator,
                                     Supplier<Window> ownerSupplier,
                                     Supplier<String> currentDocumentTitle,
                                     Consumer<Path> documentRootSetter,
                                     Runnable refreshAfterProjectChange,
                                     BiConsumer<String, Exception> showError) {
        this.state = Objects.requireNonNull(state, "state");
        this.persistenceCoordinator = Objects.requireNonNull(persistenceCoordinator, "persistenceCoordinator");
        this.ownerSupplier = Objects.requireNonNull(ownerSupplier, "ownerSupplier");
        this.currentDocumentTitle = Objects.requireNonNull(currentDocumentTitle, "currentDocumentTitle");
        this.documentRootSetter = Objects.requireNonNull(documentRootSetter, "documentRootSetter");
        this.refreshAfterProjectChange = Objects.requireNonNull(refreshAfterProjectChange, "refreshAfterProjectChange");
        this.showError = Objects.requireNonNull(showError, "showError");
    }

    public void saveProject() {
        MapProject project = state.getProject();
        if (project == null) {
            return;
        }
        try {
            MapProject snapshot = project.copy();
            String defaultName = project.getName() != null && !project.getName().isBlank()
                    ? project.getName()
                    : currentDocumentTitle.get();
            TextInputDialog dialog = new TextInputDialog(defaultName == null || defaultName.isBlank() ? "finished" : defaultName);
            dialog.setTitle("Save finished map");
            dialog.setHeaderText("Название папки для карты");
            dialog.setContentText("Folder name:");
            Window owner = ownerSupplier.get();
            if (owner != null) {
                dialog.initOwner(owner);
            }
            var result = dialog.showAndWait();
            if (result.isEmpty()) {
                return;
            }
            String folderName = result.get().trim();
            Path targetRoot = persistenceCoordinator.saveFinished(snapshot, folderName);
            documentRootSetter.accept(targetRoot);
        } catch (Exception ex) {
            showError.accept("Save failed", ex);
        }
    }

    public void loadProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Load map workspace");
        Path finished = persistenceCoordinator.finishedDir();
        if (Files.isDirectory(finished)) {
            chooser.setInitialDirectory(finished.toFile());
        }
        Window owner = ownerSupplier.get();
        File dir = chooser.showDialog(owner);
        if (dir == null) {
            return;
        }
        try {
            Path loadedRoot = dir.toPath();
            MapProject loaded = persistenceCoordinator.loadWorkspace(loadedRoot);
            documentRootSetter.accept(loadedRoot);
            state.setProject(loaded);
            refreshAfterProjectChange.run();
        } catch (Exception ex) {
            showError.accept("Load failed", ex);
        }
    }

    public void exportLayout() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export shared layout");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showSaveDialog(ownerSupplier.get());
        if (file == null) {
            return;
        }
        try {
            MapLayoutUpdateDto layout = SharedProjectMapper.toLayoutDto(state.getProject());
            persistenceCoordinator.saveLayout(Path.of(file.toURI()), layout);
        } catch (Exception ex) {
            showError.accept("Export failed", ex);
        }
    }

    public void importLayout() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import shared layout");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = chooser.showOpenDialog(ownerSupplier.get());
        if (file == null) {
            return;
        }
        try {
            MapLayoutUpdateDto layout = persistenceCoordinator.loadLayout(Path.of(file.toURI()));
            state.setProject(SharedProjectMapper.fromLayoutDto("imported-project", "Imported Map", layout));
            refreshAfterProjectChange.run();
        } catch (Exception ex) {
            showError.accept("Import failed", ex);
        }
    }

    public void undo() {
        if (state.undo()) {
            refreshAfterProjectChange.run();
        }
    }

    public void redo() {
        if (state.redo()) {
            refreshAfterProjectChange.run();
        }
    }

    public void addLayer() {
        MapProject project = state.getProject();
        if (project == null) {
            return;
        }
        MapLayer newLayer = new MapLayer(
                java.util.UUID.randomUUID().toString(),
                "Layer " + (project.getLayers().size() + 1),
                LayerKind.OBJECTS
        );
        state.recordHistory();
        project.addLayer(newLayer);
        state.selectLayer(newLayer.getId());
        refreshAfterProjectChange.run();
    }

    public void removeSelectedLayer() {
        MapProject project = state.getProject();
        if (project == null) {
            return;
        }
        MapLayer selected = state.selectedLayer();
        if (selected == null || project.getLayers().size() <= 1) {
            return;
        }
        state.recordHistory();
        project.mutableLayers().remove(selected);
        MapLayer fallback = project.getLayers().isEmpty() ? null : project.getLayers().get(0);
        if (fallback != null) {
            state.selectLayer(fallback.getId());
        } else {
            state.setSelectedLayerId(null);
        }
        refreshAfterProjectChange.run();
    }

    public void toggleSelectedLayerVisible() {
        MapLayer selected = state.selectedLayer();
        if (selected == null) {
            return;
        }
        state.recordHistory();
        selected.setVisible(!selected.isVisible());
        refreshAfterProjectChange.run();
    }

    public void toggleSelectedLayerLocked() {
        MapLayer selected = state.selectedLayer();
        if (selected == null) {
            return;
        }
        state.recordHistory();
        selected.setLocked(!selected.isLocked());
        refreshAfterProjectChange.run();
    }

}
