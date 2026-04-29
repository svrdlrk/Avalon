package com.avalon.dnd.mapeditor;

import com.avalon.dnd.mapeditor.model.AssetCatalog;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.service.AssetCatalogLoader;
import com.avalon.dnd.mapeditor.service.ProjectRepository;
import com.avalon.dnd.mapeditor.ui.MapEditorPane;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class MapEditorApplication extends Application {

    private final ProjectRepository repository = new ProjectRepository();
    private final Map<Path, Tab> tabsByRoot = new LinkedHashMap<>();
    private AssetCatalog catalog;
    private TabPane tabPane;

    @Override
    public void start(Stage stage) {
        String assetsDir = getParameters().getNamed().get("assets");
        if (assetsDir != null && !assetsDir.isBlank()) {
            System.setProperty("avalon.assets.dir", assetsDir);
        }

        catalog = AssetCatalogLoader.loadDefault();
        tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        BorderPane root = new BorderPane();
        root.setTop(buildAppToolbar(stage));
        root.setCenter(tabPane);

        Scene scene = new Scene(root, 1440, 900);
        stage.setTitle("Avalon Map Editor");
        stage.setScene(scene);
        stage.show();

        String initialProject = getParameters().getNamed().get("project");
        if (initialProject != null && !initialProject.isBlank()) {
            try {
                openDocument(Path.of(initialProject));
            } catch (Exception ex) {
                showError("Open project failed", ex);
                newBlankTab();
            }
        } else {
            newBlankTab();
        }
    }

    private ToolBar buildAppToolbar(Stage owner) {
        Button newWorkspace = new Button("New workspace");
        Button openWorkspace = new Button("Open workspace");
        Button closeTab = new Button("Close tab");
        Button newBlank = new Button("New blank tab");

        newWorkspace.setOnAction(e -> newBlankTab());
        newBlank.setOnAction(e -> newBlankTab());
        openWorkspace.setOnAction(e -> chooseWorkspaceAndOpen(owner));
        closeTab.setOnAction(e -> closeSelectedTab());

        ToolBar toolbar = new ToolBar(
                new Label("Map documents"),
                new Separator(),
                newWorkspace,
                newBlank,
                openWorkspace,
                closeTab
        );
        toolbar.setPadding(new Insets(6));
        return toolbar;
    }

    private void chooseWorkspaceAndOpen(Stage owner) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Open map workspace");
        var dir = chooser.showDialog(owner);
        if (dir == null) {
            return;
        }
        openDocument(dir.toPath());
    }

    private void newBlankTab() {
        MapProject project = MapProject.createBlank(null, "Untitled Map");
        Path root = null;
        openTab(root, project);
    }

    private void openDocument(Path path) {
        if (path == null) {
            newBlankTab();
            return;
        }

        Path normalized = path.toAbsolutePath().normalize();
        Path root = Files.isDirectory(normalized) ? normalized : normalized.getParent();
        if (root == null) {
            root = normalized;
        }
        Path key = root.toAbsolutePath().normalize();
        Tab existing = tabsByRoot.get(key);
        if (existing != null) {
            tabPane.getSelectionModel().select(existing);
            return;
        }

        try {
            MapProject project = repository.load(normalized);
            openTab(key, project);
        } catch (Exception ex) {
            showError("Open workspace failed", ex);
        }
    }

    private void openTab(Path root, MapProject project) {
        MapEditorPane pane = new MapEditorPane(project, catalog, root, this::openDocument);
        Tab tab = new Tab();
        tab.setContent(pane);
        tab.textProperty().bind(pane.documentTitleProperty());

        pane.documentRootProperty().addListener((obs, oldValue, newValue) -> {
            if (oldValue != null) {
                tabsByRoot.remove(oldValue.toAbsolutePath().normalize());
            }
            if (newValue != null) {
                tabsByRoot.put(newValue.toAbsolutePath().normalize(), tab);
            }
        });

        tab.setOnClosed(e -> {
            Path currentRoot = pane.getDocumentRoot();
            if (currentRoot != null) {
                tabsByRoot.remove(currentRoot.toAbsolutePath().normalize());
            }
        });
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        Path currentRoot = pane.getDocumentRoot();
        if (currentRoot != null) {
            tabsByRoot.put(currentRoot.toAbsolutePath().normalize(), tab);
        }
    }

    private void closeSelectedTab() {
        Tab selected = tabPane.getSelectionModel().getSelectedItem();
        if (selected != null) {
            tabPane.getTabs().remove(selected);
        }
    }

    private void showError(String title, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(ex.getMessage());
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
