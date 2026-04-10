package com.avalon.dnd.mapeditor;

import com.avalon.dnd.mapeditor.model.AssetCatalog;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.service.AssetCatalogLoader;
import com.avalon.dnd.mapeditor.service.ProjectRepository;
import com.avalon.dnd.mapeditor.ui.MapEditorPane;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.nio.file.Path;

public class MapEditorApplication extends Application {

    @Override
    public void start(Stage stage) {
        String assetsDir = getParameters().getNamed().get("assets");
        if (assetsDir != null && !assetsDir.isBlank()) {
            System.setProperty("avalon.assets.dir", assetsDir);
        }

        AssetCatalog catalog = AssetCatalogLoader.loadDefault();
        ProjectRepository repository = new ProjectRepository();
        MapProject project = MapProject.createBlank("new-project", "Untitled Map");

        String initialProject = getParameters().getNamed().get("project");
        if (initialProject != null && !initialProject.isBlank()) {
            try {
                project = repository.load(Path.of(initialProject));
            } catch (Exception ex) {
                System.err.println("Failed to load project: " + ex.getMessage());
            }
        }

        MapEditorPane root = new MapEditorPane(project, catalog);

        Scene scene = new Scene(root, 1440, 900);
        stage.setTitle("Avalon Map Editor");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
