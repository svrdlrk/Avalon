package com.avalon.dnd.mapeditor.ui;

import com.avalon.dnd.mapeditor.model.*;
import com.avalon.dnd.mapeditor.service.GridAlignmentService;
import com.avalon.dnd.mapeditor.service.ProjectRepository;
import com.avalon.dnd.mapeditor.service.SharedProjectMapper;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MicroLocationDto;
import com.avalon.dnd.mapeditor.tool.BrushTool;
import com.avalon.dnd.mapeditor.tool.EraseTool;
import com.avalon.dnd.mapeditor.tool.PanTool;
import com.avalon.dnd.mapeditor.tool.MoveTool;
import com.avalon.dnd.mapeditor.tool.ReferenceOverlayTool;
import com.avalon.dnd.mapeditor.tool.PlaceAssetTool;
import com.avalon.dnd.mapeditor.tool.TokenPlaceTool;
import com.avalon.dnd.mapeditor.tool.SelectTool;
import com.avalon.dnd.mapeditor.tool.TerrainBrushTool;
import com.avalon.dnd.mapeditor.tool.WallBrushTool;
import com.avalon.dnd.mapeditor.tool.WallEditTool;
import com.avalon.dnd.mapeditor.tool.Tool;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ColorPicker;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

public class MapEditorPane extends BorderPane {

    private final EditorState state = new EditorState();
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final MapEditorCanvas canvas;
    private final AssetCatalog assetCatalog;
    private final TextField assetSearch = new TextField();
    private final Label selectionLabel = new Label("Nothing selected");
    private final Label layerLabel = new Label("No layer selected");
    private final ListView<MapLayer> layerList = new ListView<>();
    private final ProjectRepository repository = new ProjectRepository();
    private final PauseTransition backupAutosaveDebounce = new PauseTransition(Duration.millis(1200));
    private final SimpleStringProperty documentTitle = new SimpleStringProperty("Untitled Map");

    private final ObjectProperty<Path> documentRootProperty = new SimpleObjectProperty<>();
    private Path documentRoot;
    private Consumer<Path> openDocumentCallback = path -> {};

    private final TextField backgroundUrlField = new TextField();
    private final ComboBox<BackgroundMode> backgroundModeCombo = new ComboBox<>();
    private final CheckBox backgroundVisibleCheck = new CheckBox("Visible");
    private final Spinner<Double> backgroundScaleSpinner = new Spinner<>();
    private final Spinner<Integer> backgroundOffsetXSpinner = new Spinner<>();
    private final Spinner<Integer> backgroundOffsetYSpinner = new Spinner<>();
    private final Slider backgroundOpacitySlider = new Slider(0.0, 1.0, 1.0);

    private final ComboBox<AssetDefinition> referenceAssetCombo = new ComboBox<>();
    private final CheckBox referenceVisibleCheck = new CheckBox("Visible");
    private final CheckBox referenceLockedCheck = new CheckBox("Locked");
    private final Slider referenceOpacitySlider = new Slider(0.0, 1.0, 0.65);
    private final Spinner<Double> referenceScaleSpinner = new Spinner<>();
    private final Spinner<Double> referenceRotationSpinner = new Spinner<>();
    private final Spinner<Integer> referenceOffsetXSpinner = new Spinner<>();
    private final Spinner<Integer> referenceOffsetYSpinner = new Spinner<>();

    private final CheckBox terrainVisibleCheck = new CheckBox("Visible");
    private final CheckBox terrainLockedCheck = new CheckBox("Locked");
    private final Slider terrainOpacitySlider = new Slider(0.0, 1.0, 0.85);
    private final ComboBox<String> terrainTypeCombo = new ComboBox<>();

    private final CheckBox wallVisibleCheck = new CheckBox("Visible");
    private final CheckBox wallLockedCheck = new CheckBox("Locked");
    private final Slider wallOpacitySlider = new Slider(0.0, 1.0, 0.9);
    private final Spinner<Double> wallThicknessSpinner = new Spinner<>();
    private final CheckBox wallBlocksMoveCheck = new CheckBox("Blocks movement");
    private final CheckBox wallBlocksSightCheck = new CheckBox("Blocks sight");

    private final CheckBox fogEnabledCheck = new CheckBox("Enabled");
    private final CheckBox fogRevealFromTokensCheck = new CheckBox("Reveal from tokens");
    private final CheckBox fogRevealFromSelectedCheck = new CheckBox("Reveal selected placement");
    private final CheckBox fogRetainExploredCheck = new CheckBox("Keep explored");
    private final Slider fogOpacitySlider = new Slider(0.0, 1.0, 0.72);
    private final Spinner<Integer> fogRadiusSpinner = new Spinner<>();

    private final ListView<MicroLocationDto> microLocationList = new ListView<>();
    private final TextField microLocationIdField = new TextField();
    private final TextField microLocationNameField = new TextField();
    private final TextField microLocationHintField = new TextField();
    private final TextField microLocationInteriorPathField = new TextField();
    private final Spinner<Integer> microLocationColSpinner = new Spinner<>();
    private final Spinner<Integer> microLocationRowSpinner = new Spinner<>();
    private final Spinner<Integer> microLocationWidthSpinner = new Spinner<>();
    private final Spinner<Integer> microLocationHeightSpinner = new Spinner<>();
    private final CheckBox microLocationLockedCheck = new CheckBox("Locked");
    private final Label microLocationStatusLabel = new Label();

    private final TextField placementNameField = new TextField();
    private final ComboBox<MapLayer> placementLayerCombo = new ComboBox<>();
    private final TextField placementMicroLocationField = new TextField();
    private final Spinner<Integer> placementColSpinner = new Spinner<>();
    private final Spinner<Integer> placementRowSpinner = new Spinner<>();
    private final Spinner<Integer> placementWidthSpinner = new Spinner<>();
    private final Spinner<Integer> placementHeightSpinner = new Spinner<>();
    private final Spinner<Integer> placementGridSizeSpinner = new Spinner<>();
    private final Spinner<Double> placementRotationSpinner = new Spinner<>();

    private final Spinner<Integer> gridCellSizeSpinner = new Spinner<>();
    private final Spinner<Integer> gridColsSpinner = new Spinner<>();
    private final Spinner<Integer> gridRowsSpinner = new Spinner<>();
    private final Spinner<Integer> gridOffsetXSpinner = new Spinner<>();
    private final Spinner<Integer> gridOffsetYSpinner = new Spinner<>();
    private final CheckBox placementBlocksMoveCheck = new CheckBox("Blocks movement");
    private final CheckBox placementBlocksSightCheck = new CheckBox("Blocks sight");
    private final CheckBox placementLockedCheck = new CheckBox("Locked");

    private boolean syncingLayerSelection = false;
    private boolean syncingPlacementForm = false;
    private boolean syncingBackgroundForm = false;
    private boolean syncingReferenceForm = false;
    private boolean syncingTerrainForm = false;
    private boolean syncingGridForm = false;
    private boolean syncingWallForm = false;
    private boolean syncingFogForm = false;
    private boolean syncingMicroLocationForm = false;
    private String selectedMicroLocationId;

    public MapEditorPane(MapProject project, AssetCatalog catalog) {
        this(project, catalog, null, null);
    }

    public MapEditorPane(MapProject project, AssetCatalog catalog, Path documentRoot, Consumer<Path> openDocumentCallback) {
        this.assetCatalog = catalog;
        setDocumentRoot(documentRoot);
        this.openDocumentCallback = openDocumentCallback == null ? path -> {} : openDocumentCallback;
        state.setAssetCatalog(catalog);
        state.setProject(project);

        tools.put("select", new SelectTool());
        tools.put("move", new MoveTool());
        tools.put("reference", new ReferenceOverlayTool());
        tools.put("place", new PlaceAssetTool());
        tools.put("token", new TokenPlaceTool());
        tools.put("brush", new BrushTool());
        tools.put("terrain", new TerrainBrushTool());
        tools.put("wall", new WallBrushTool());
        tools.put("wallEdit", new WallEditTool());
        tools.put("erase", new EraseTool());
        tools.put("pan", new PanTool());

        state.setActiveTool(tools.get("select"));

        canvas = new MapEditorCanvas(state);
        setCenter(new ScrollPane(canvas));

        setTop(buildToolbar());
        setLeft(buildAssetPanel(catalog));
        setRight(buildRightPanel());

        backupAutosaveDebounce.setOnFinished(e -> saveBackupSnapshot());
        state.addListener(evt -> {
            refreshSelection();
            if (EditorState.PROP_HISTORY.equals(evt.getPropertyName())) {
                scheduleBackupSave();
            }
        });
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                installAccelerators(newScene);
            }
        });
        refreshSelection();
        refreshLayerList();
        updateDocumentTitle();
    }

    public ReadOnlyStringProperty documentTitleProperty() {
        return documentTitle;
    }

    public ObjectProperty<Path> documentRootProperty() {
        return documentRootProperty;
    }

    public void setDocumentRoot(Path documentRoot) {
        this.documentRoot = documentRoot == null ? null : documentRoot.toAbsolutePath().normalize();
        documentRootProperty.set(this.documentRoot);
        updateDocumentTitle();
    }

    public void setOpenDocumentCallback(Consumer<Path> openDocumentCallback) {
        this.openDocumentCallback = openDocumentCallback == null ? path -> {} : openDocumentCallback;
    }

    public Path getDocumentRoot() {
        return documentRoot;
    }

    private void updateDocumentTitle() {
        String title = state.getProject() != null && state.getProject().getName() != null && !state.getProject().getName().isBlank()
                ? state.getProject().getName()
                : "Untitled Map";
        if (documentRoot != null && documentRoot.getFileName() != null) {
            title = documentRoot.getFileName().toString();
        }
        documentTitle.set(title);
    }

    private GridConfig grid() {
        return state.grid();
    }
    private Tool activeTool(String id) {
        return tools.getOrDefault(id, tools.get("select"));
    }

    private ToggleButton buttonFor(Tool tool, ToggleGroup group) {
        ToggleButton button = new ToggleButton(tool.getDisplayName());
        button.setToggleGroup(group);
        button.setOnAction(e -> state.setActiveTool(tool));
        return button;
    }

    private ToolBar buildToolbar() {
        ToggleGroup toolGroup = new ToggleGroup();

        ToggleButton select = buttonFor(tools.get("select"), toolGroup);
        ToggleButton move = buttonFor(tools.get("move"), toolGroup);
        ToggleButton reference = buttonFor(tools.get("reference"), toolGroup);
        ToggleButton place = buttonFor(tools.get("place"), toolGroup);
        ToggleButton token = buttonFor(tools.get("token"), toolGroup);
        ToggleButton brush = buttonFor(tools.get("brush"), toolGroup);
        ToggleButton terrain = buttonFor(tools.get("terrain"), toolGroup);
        ToggleButton wall = buttonFor(tools.get("wall"), toolGroup);
        ToggleButton wallEdit = buttonFor(tools.get("wallEdit"), toolGroup);
        ToggleButton erase = buttonFor(tools.get("erase"), toolGroup);
        ToggleButton pan = buttonFor(tools.get("pan"), toolGroup);

        select.setSelected(true);

        Button newProject = new Button("New");
        newProject.setOnAction(e -> {
            setDocumentRoot(null);
            state.setProject(MapProject.createBlank(null, "Untitled Map"));
            state.selectAsset(null);
            state.selectTokenAsset(null);
            state.selectObjectAsset(null);
            canvas.requestRender();
            refreshSelection();
            refreshBackgroundForm();
            refreshReferenceForm();
            refreshTerrainForm();
            refreshWallForm();
            refreshFogForm();
            refreshGridForm();
            refreshLayerList();
            updateDocumentTitle();
        });

        Button undo = new Button("Undo");
        undo.setOnAction(e -> undo());

        Button redo = new Button("Redo");
        redo.setOnAction(e -> redo());

        Button save = new Button("Save workspace");
        save.setOnAction(e -> saveProject());

        Button load = new Button("Load workspace");
        load.setOnAction(e -> loadProject());

        Button exportLayout = new Button("Export Layout");
        exportLayout.setOnAction(e -> exportLayout());

        Button importLayout = new Button("Import Layout");
        importLayout.setOnAction(e -> importLayout());

        ToggleButton snap = new ToggleButton("Snap");
        snap.setSelected(true);
        snap.setOnAction(e -> state.setSnapToGrid(snap.isSelected()));

        ToggleButton fog = new ToggleButton("Fog preview");
        fog.setSelected(true);
        fog.setOnAction(e -> state.setFogPreviewEnabled(fog.isSelected()));

        Button resetView = new Button("Reset View");
        resetView.setOnAction(e -> {
            state.setViewOffset(0, 0);
            state.setZoom(1.0);
            canvas.requestRender();
        });

        Label title = new Label("Map Editor");

        ToolBar bar = new ToolBar(
                title,
                new Separator(Orientation.VERTICAL),
                select, move, reference, place, token, brush, terrain, wall, wallEdit, erase, pan,
                new Separator(Orientation.VERTICAL),
                newProject, undo, redo, save, load, exportLayout, importLayout, resetView,
                new Separator(Orientation.VERTICAL),
                snap, fog
        );
        bar.setPadding(new Insets(6));
        return bar;
    }

    private Node buildAssetPanel(AssetCatalog catalog) {
        VBox content = new VBox(12);
        content.setPadding(new Insets(12));

        Label title = new Label("Assets");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Label tokenLabel = new Label("Tokens: none selected");
        Label objectLabel = new Label("Objects: none selected");
        Label hint = new Label("Open the catalog windows to choose tokens and objects separately.");
        hint.setWrapText(true);

        Button openTokens = new Button("Open token explorer");
        openTokens.setMaxWidth(Double.MAX_VALUE);
        openTokens.setOnAction(e -> openTokenBrowser());

        Button openObjects = new Button("Open object window");
        openObjects.setMaxWidth(Double.MAX_VALUE);
        openObjects.setOnAction(e -> openObjectBrowser());

        Button useReferenceAsAsset = new Button("Use selected asset for reference");
        useReferenceAsAsset.setMaxWidth(Double.MAX_VALUE);
        useReferenceAsAsset.setOnAction(e -> {
            if (state.selectedAsset() != null) {
                state.selectAsset(state.selectedAsset().getId());
                refreshSelection();
            }
        });

        state.addListener(evt -> {
            if (EditorState.PROP_SELECTED_TOKEN_ASSET.equals(evt.getPropertyName()) || EditorState.PROP_SELECTED_ASSET.equals(evt.getPropertyName())) {
                AssetDefinition asset = state.selectedTokenAsset();
                tokenLabel.setText(asset == null ? "Tokens: none selected" : "Tokens: " + asset.getName());
            }
            if (EditorState.PROP_SELECTED_OBJECT_ASSET.equals(evt.getPropertyName()) || EditorState.PROP_SELECTED_ASSET.equals(evt.getPropertyName())) {
                AssetDefinition asset = state.selectedObjectAsset();
                objectLabel.setText(asset == null ? "Objects: none selected" : "Objects: " + asset.getName());
            }
        });

        tokenLabel.setText(state.selectedTokenAsset() == null ? "Tokens: none selected" : "Tokens: " + state.selectedTokenAsset().getName());
        objectLabel.setText(state.selectedObjectAsset() == null ? "Objects: none selected" : "Objects: " + state.selectedObjectAsset().getName());

        content.getChildren().addAll(title, tokenLabel, objectLabel, hint, openTokens, openObjects, new Separator(), useReferenceAsAsset);
        VBox.setVgrow(hint, Priority.NEVER);
        VBoxWrapper wrapper = new VBoxWrapper("Asset selection", content);
        ScrollPane scroll = new ScrollPane(wrapper);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(280);
        return scroll;
    }

    private Node buildRightPanel() {

        VBox backgroundBox = buildBackgroundPanel();
        VBox referenceBox = buildReferencePanel();
        VBox gridBox = buildGridPanel();
        VBox terrainBox = buildTerrainPanel();
        VBox wallBox = buildWallPanel();
        VBox fogBox = buildFogPanel();
        VBox microLocationBox = buildMicroLocationPanel();
        VBox selectionBox = buildPropertiesPanel();
        VBox layersBox = buildLayerPanel();

        VBox outer = new VBox(12, backgroundBox, referenceBox, gridBox, terrainBox, wallBox, fogBox, microLocationBox, selectionBox, layersBox);
        outer.setPadding(new Insets(12));
        outer.setPrefWidth(320);

        ScrollPane scroll = new ScrollPane(outer);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(320);
        return scroll;
    }

    private VBox buildLayerPanel() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setPrefWidth(240);

        Label title = new Label("Layers");

        Button addLayerButton = new Button("Add layer");
        Button removeLayerButton = new Button("Remove layer");
        Button toggleVisibleButton = new Button("Toggle visible");
        Button toggleLockedButton = new Button("Toggle locked");

        addLayerButton.setMaxWidth(Double.MAX_VALUE);
        removeLayerButton.setMaxWidth(Double.MAX_VALUE);
        toggleVisibleButton.setMaxWidth(Double.MAX_VALUE);
        toggleLockedButton.setMaxWidth(Double.MAX_VALUE);

        addLayerButton.setOnAction(e -> onAddLayer());
        removeLayerButton.setOnAction(e -> onRemoveSelectedLayer());
        toggleVisibleButton.setOnAction(e -> onToggleSelectedLayerVisible());
        toggleLockedButton.setOnAction(e -> onToggleSelectedLayerLocked());

        box.getChildren().addAll(
                title,
                layerList,
                addLayerButton,
                removeLayerButton,
                toggleVisibleButton,
                toggleLockedButton
        );

        VBox.setVgrow(layerList, Priority.ALWAYS);
        return box;
    }

    private VBox buildBackgroundPanel() {
        VBoxWrapper box = new VBoxWrapper("Background", new Label("Map background"));

        backgroundUrlField.setPromptText("Background image URL");
        backgroundModeCombo.getItems().setAll(BackgroundMode.values());
        backgroundVisibleCheck.setSelected(true);

        backgroundUrlField.setOnAction(e -> commitBackgroundEdit(() -> setBackgroundUrl(backgroundUrlField.getText())));
        backgroundUrlField.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV) {
                commitBackgroundEdit(() -> setBackgroundUrl(backgroundUrlField.getText()));
            }
        });
        backgroundModeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingBackgroundForm || newV == null) return;
            commitBackgroundEdit(() -> background().setMode(newV));
        });
        backgroundVisibleCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingBackgroundForm) return;
            commitBackgroundEdit(() -> background().setVisible(Boolean.TRUE.equals(newV)));
        });

        backgroundScaleSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1, 10.0, 1.0, 0.1));
        backgroundOffsetXSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-100000, 100000, 0));
        backgroundOffsetYSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-100000, 100000, 0));
        backgroundOpacitySlider.setShowTickLabels(true);
        backgroundOpacitySlider.setShowTickMarks(true);
        backgroundOpacitySlider.setBlockIncrement(0.05);

        backgroundScaleSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingBackgroundForm || newV == null) return;
            commitBackgroundEdit(() -> background().setScale(newV));
        });
        backgroundOffsetXSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingBackgroundForm || newV == null) return;
            commitBackgroundEdit(() -> background().setOffsetX(newV));
        });
        backgroundOffsetYSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingBackgroundForm || newV == null) return;
            commitBackgroundEdit(() -> background().setOffsetY(newV));
        });
        backgroundOpacitySlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingBackgroundForm || newV == null) return;
            commitBackgroundEdit(() -> background().setOpacity(newV.doubleValue()));
        });

        Button useSelectedAsset = new Button("Use selected asset");
        useSelectedAsset.setOnAction(e -> {
            if (state.selectedAsset() != null) {
                commitBackgroundEdit(() -> setBackgroundUrl(state.selectedAsset().getImageUrl()));
            }
        });

        Button clearBackground = new Button("Clear background");
        clearBackground.setOnAction(e -> commitBackgroundEdit(() -> setBackgroundUrl(null)));

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Image URL"), backgroundUrlField);
        form.addRow(1, new Label("Mode"), backgroundModeCombo);
        form.addRow(2, new Label("Visible"), backgroundVisibleCheck);
        form.addRow(3, new Label("Scale"), backgroundScaleSpinner);
        form.addRow(4, new Label("Offset X"), backgroundOffsetXSpinner);
        form.addRow(5, new Label("Offset Y"), backgroundOffsetYSpinner);
        form.addRow(6, new Label("Opacity"), backgroundOpacitySlider);

        box.getChildren().addAll(
                new Label("Base layer"),
                form,
                useSelectedAsset,
                clearBackground
        );

        refreshBackgroundForm();
        return box;
    }

    private VBox buildReferencePanel() {
        VBoxWrapper box = new VBoxWrapper("Reference", new Label("Reference overlay image"));

        referenceAssetCombo.setPromptText("Select reference image");
        referenceAssetCombo.setMaxWidth(Double.MAX_VALUE);
        referenceAssetCombo.getItems().setAll(referenceAssets());
        referenceAssetCombo.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(AssetDefinition item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : referenceAssetLabel(item));
            }
        });
        referenceAssetCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(AssetDefinition item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select reference image" : referenceAssetLabel(item));
            }
        });

        referenceVisibleCheck.setSelected(true);
        referenceLockedCheck.setSelected(false);
        referenceOpacitySlider.setShowTickLabels(true);
        referenceOpacitySlider.setShowTickMarks(true);
        referenceOpacitySlider.setBlockIncrement(0.05);
        referenceScaleSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1, 10.0, 1.0, 0.1));
        referenceRotationSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(-180.0, 180.0, 0.0, 5.0));
        referenceOffsetXSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-100000, 100000, 0));
        referenceOffsetYSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-100000, 100000, 0));

        referenceAssetCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingReferenceForm || newV == null) return;
            commitReferenceEdit(() -> reference().setImageUrl(newV.getImageUrl()));
        });
        referenceVisibleCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingReferenceForm) return;
            commitReferenceEdit(() -> reference().setVisible(Boolean.TRUE.equals(newV)));
        });
        referenceLockedCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingReferenceForm) return;
            commitReferenceEdit(() -> reference().setLocked(Boolean.TRUE.equals(newV)));
        });
        referenceOpacitySlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingReferenceForm || newV == null) return;
            commitReferenceEdit(() -> reference().setOpacity(newV.doubleValue()));
        });
        referenceScaleSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingReferenceForm || newV == null) return;
            commitReferenceEdit(() -> reference().setScale(newV));
        });
        referenceRotationSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingReferenceForm || newV == null) return;
            commitReferenceEdit(() -> reference().setRotation(newV));
        });
        referenceOffsetXSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingReferenceForm || newV == null) return;
            commitReferenceEdit(() -> reference().setOffsetX(newV));
        });
        referenceOffsetYSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingReferenceForm || newV == null) return;
            commitReferenceEdit(() -> reference().setOffsetY(newV));
        });

        Button useSelectedAsset = new Button("Use selected asset");
        useSelectedAsset.setOnAction(e -> {
            AssetDefinition selected = state.selectedAsset();
            if (selected != null) {
                AssetDefinition matching = referenceAssets().stream()
                        .filter(asset -> Objects.equals(asset.getImageUrl(), selected.getImageUrl()))
                        .findFirst()
                        .orElse(null);
                if (matching != null) {
                    referenceAssetCombo.getSelectionModel().select(matching);
                }
            }
        });

        Button clearReference = new Button("Clear reference");
        clearReference.setOnAction(e -> commitReferenceEdit(() -> reference().setImageUrl(null)));

        Button resetTransform = new Button("Reset transform");
        resetTransform.setOnAction(e -> commitReferenceEdit(() -> {
            ReferenceOverlay overlay = reference();
            overlay.setScale(1.0);
            overlay.setRotation(0.0);
            overlay.setOffsetX(0.0);
            overlay.setOffsetY(0.0);
        }));

        Button fitGrid = new Button("Fit grid to reference");
        fitGrid.setMaxWidth(Double.MAX_VALUE);
        fitGrid.setOnAction(e -> fitGridToReference());

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Reference"), referenceAssetCombo);
        form.addRow(1, new Label("Visible"), referenceVisibleCheck);
        form.addRow(2, new Label("Locked"), referenceLockedCheck);
        form.addRow(3, new Label("Opacity"), referenceOpacitySlider);
        form.addRow(4, new Label("Scale"), referenceScaleSpinner);
        form.addRow(5, new Label("Rotation"), referenceRotationSpinner);
        form.addRow(6, new Label("Offset X"), referenceOffsetXSpinner);
        form.addRow(7, new Label("Offset Y"), referenceOffsetYSpinner);

        box.getChildren().addAll(
                new Label("Reference overlay"),
                form,
                useSelectedAsset,
                clearReference,
                resetTransform,
                fitGrid
        );

        refreshReferenceForm();
        return box;
    }



    private VBox buildGridPanel() {
        VBoxWrapper box = new VBoxWrapper("Grid", new Label("Grid alignment and canvas size"));

        gridCellSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(4, 512, 64));
        gridColsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 2000, 40));
        gridRowsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 2000, 30));
        gridOffsetXSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-100000, 100000, 0));
        gridOffsetYSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-100000, 100000, 0));

        gridCellSizeSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingGridForm || newV == null) return;
            commitGridEdit(() -> grid().setCellSize(newV));
        });
        gridColsSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingGridForm || newV == null) return;
            commitGridEdit(() -> grid().setCols(newV));
        });
        gridRowsSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingGridForm || newV == null) return;
            commitGridEdit(() -> grid().setRows(newV));
        });
        gridOffsetXSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingGridForm || newV == null) return;
            commitGridEdit(() -> grid().setOffsetX(newV));
        });
        gridOffsetYSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingGridForm || newV == null) return;
            commitGridEdit(() -> grid().setOffsetY(newV));
        });

        Button fitToReference = new Button("Fit to reference image");
        fitToReference.setMaxWidth(Double.MAX_VALUE);
        fitToReference.setOnAction(e -> fitGridToReference());

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Cell size"), gridCellSizeSpinner);
        form.addRow(1, new Label("Columns"), gridColsSpinner);
        form.addRow(2, new Label("Rows"), gridRowsSpinner);
        form.addRow(3, new Label("Offset X"), gridOffsetXSpinner);
        form.addRow(4, new Label("Offset Y"), gridOffsetYSpinner);

        box.getChildren().addAll(new Label("Map grid"), form, fitToReference);
        refreshGridForm();
        return box;
    }

    private VBox buildTerrainPanel() {
        VBoxWrapper box = new VBoxWrapper("Terrain", new Label("Paintable terrain layer"));

        terrainTypeCombo.getItems().setAll("grass", "stone", "dirt", "sand", "water");
        terrainVisibleCheck.setSelected(true);
        terrainLockedCheck.setSelected(false);
        terrainOpacitySlider.setShowTickLabels(true);
        terrainOpacitySlider.setShowTickMarks(true);
        terrainOpacitySlider.setBlockIncrement(0.05);

        terrainTypeCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingTerrainForm || newV == null) return;
            commitTerrainEdit(() -> terrain().setPaintType(newV));
        });
        terrainVisibleCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingTerrainForm) return;
            commitTerrainEdit(() -> terrain().setVisible(Boolean.TRUE.equals(newV)));
        });
        terrainLockedCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingTerrainForm) return;
            commitTerrainEdit(() -> terrain().setLocked(Boolean.TRUE.equals(newV)));
        });
        terrainOpacitySlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingTerrainForm || newV == null) return;
            commitTerrainEdit(() -> terrain().setOpacity(newV.doubleValue()));
        });

        Button clearTerrain = new Button("Clear terrain");
        clearTerrain.setOnAction(e -> commitTerrainEdit(() -> terrain().setCells(null)));

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Type"), terrainTypeCombo);
        form.addRow(1, new Label("Visible"), terrainVisibleCheck);
        form.addRow(2, new Label("Locked"), terrainLockedCheck);
        form.addRow(3, new Label("Opacity"), terrainOpacitySlider);

        box.getChildren().addAll(new Label("Terrain layer"), form, clearTerrain);
        refreshTerrainForm();
        return box;
    }

    private VBox buildWallPanel() {
        VBoxWrapper box = new VBoxWrapper("Walls", new Label("Blockers and cliff lines"));

        wallVisibleCheck.setSelected(true);
        wallLockedCheck.setSelected(false);
        wallOpacitySlider.setShowTickLabels(true);
        wallOpacitySlider.setShowTickMarks(true);
        wallOpacitySlider.setBlockIncrement(0.05);
        wallThicknessSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.5, 20.0, 2.5, 0.5));

        wallVisibleCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingWallForm) return;
            commitWallEdit(() -> wall().setVisible(Boolean.TRUE.equals(newV)));
        });
        wallLockedCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingWallForm) return;
            commitWallEdit(() -> wall().setLocked(Boolean.TRUE.equals(newV)));
        });
        wallOpacitySlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingWallForm || newV == null) return;
            commitWallEdit(() -> wall().setOpacity(newV.doubleValue()));
        });
        wallThicknessSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingWallForm || newV == null) return;
            commitWallEdit(() -> wall().setDefaultThickness(newV));
        });
        wallBlocksMoveCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingWallForm) return;
            commitWallEdit(() -> wall().setDefaultBlocksMovement(Boolean.TRUE.equals(newV)));
        });
        wallBlocksSightCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingWallForm) return;
            commitWallEdit(() -> wall().setDefaultBlocksSight(Boolean.TRUE.equals(newV)));
        });

        Button splitWall = new Button("Split wall");
        splitWall.setOnAction(e -> splitSelectedWall());

        Button mergeWall = new Button("Merge wall");
        mergeWall.setOnAction(e -> mergeSelectedWall());

        Button clearWalls = new Button("Clear walls");
        clearWalls.setOnAction(e -> commitWallEdit(() -> wall().setPaths(null)));

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Visible"), wallVisibleCheck);
        form.addRow(1, new Label("Locked"), wallLockedCheck);
        form.addRow(2, new Label("Opacity"), wallOpacitySlider);
        form.addRow(3, new Label("Thickness"), wallThicknessSpinner);
        form.addRow(4, new Label("Blocks movement"), wallBlocksMoveCheck);
        form.addRow(5, new Label("Blocks sight"), wallBlocksSightCheck);

        box.getChildren().addAll(new Label("Wall layer"), form, splitWall, mergeWall, clearWalls);
        refreshWallForm();
        return box;
    }

    private VBox buildFogPanel() {
        VBoxWrapper box = new VBoxWrapper("Fog of war", new Label("Visibility preview"));

        fogEnabledCheck.setSelected(true);
        fogRevealFromTokensCheck.setSelected(true);
        fogRevealFromSelectedCheck.setSelected(true);
        fogRetainExploredCheck.setSelected(true);
        fogOpacitySlider.setShowTickLabels(true);
        fogOpacitySlider.setShowTickMarks(true);
        fogOpacitySlider.setBlockIncrement(0.05);
        fogRadiusSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 64, 6));

        fogEnabledCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingFogForm) return;
            commitFogEdit(() -> fog().setEnabled(Boolean.TRUE.equals(newV)));
        });
        fogRevealFromTokensCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingFogForm) return;
            commitFogEdit(() -> fog().setRevealFromTokens(Boolean.TRUE.equals(newV)));
        });
        fogRevealFromSelectedCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingFogForm) return;
            commitFogEdit(() -> fog().setRevealFromSelectedPlacement(Boolean.TRUE.equals(newV)));
        });
        fogRetainExploredCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingFogForm) return;
            commitFogEdit(() -> fog().setRetainExploredCells(Boolean.TRUE.equals(newV)));
        });
        fogOpacitySlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingFogForm || newV == null) return;
            commitFogEdit(() -> fog().setOpacity(newV.doubleValue()));
        });
        fogRadiusSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingFogForm || newV == null) return;
            commitFogEdit(() -> fog().setRevealRadius(newV));
        });

        Button resetFog = new Button("Reset fog");
        resetFog.setOnAction(e -> commitFogEdit(() -> {
            FogSettings fog = fog();
            fog.setEnabled(true);
            fog.setRevealFromTokens(true);
            fog.setRevealFromSelectedPlacement(true);
            fog.setRevealRadius(6);
            fog.setOpacity(0.72);
        }));

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Enabled"), fogEnabledCheck);
        form.addRow(1, new Label("Reveal tokens"), fogRevealFromTokensCheck);
        form.addRow(2, new Label("Reveal selected"), fogRevealFromSelectedCheck);
        form.addRow(3, new Label("Radius"), fogRadiusSpinner);
        form.addRow(4, new Label("Opacity"), fogOpacitySlider);
        form.addRow(5, new Label("Keep explored"), fogRetainExploredCheck);

        box.getChildren().addAll(
                new Label("Fog settings"),
                form,
                resetFog
        );

        refreshFogForm();
        return box;
    }

    private VBox buildPropertiesPanel() {
        VBoxWrapper box = new VBoxWrapper("Selection", selectionLabel);

        placementNameField.setPromptText("Name");
        placementMicroLocationField.setPromptText("Micro location id");
        placementNameField.setOnAction(e -> commitPlacementEdit(() -> {
            MapPlacement selected = state.selectedPlacement();
            if (selected != null) {
                selected.setName(placementNameField.getText());
            }
        }));
        placementNameField.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV) {
                commitPlacementEdit(() -> {
                    MapPlacement selected = state.selectedPlacement();
                    if (selected != null) {
                        selected.setName(placementNameField.getText());
                    }
                });
            }
        });

        placementLayerCombo.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingPlacementForm || newV == null) return;
            commitPlacementEdit(() -> {
                MapPlacement selected = state.selectedPlacement();
                if (selected != null) {
                    selected.setLayerId(newV.getId());
                }
            });
            refreshLayerList();
        });

        placementMicroLocationField.setOnAction(e -> commitPlacementEdit(() -> {
            MapPlacement selected = state.selectedPlacement();
            if (selected != null) {
                String value = placementMicroLocationField.getText();
                selected.setMicroLocationId(value == null || value.isBlank() ? null : value.trim());
            }
        }));
        placementMicroLocationField.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV) {
                commitPlacementEdit(() -> {
                    MapPlacement selected = state.selectedPlacement();
                    if (selected != null) {
                        String value = placementMicroLocationField.getText();
                        selected.setMicroLocationId(value == null || value.isBlank() ? null : value.trim());
                    }
                });
            }
        });

        placementColSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 0));
        placementRowSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 0));
        placementWidthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 1));
        placementHeightSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 1));
        placementGridSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 10, 1));
        placementRotationSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0, 360, 0, 15));
        placementRotationSpinner.setEditable(true);

        placementColSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingPlacementForm || newV == null) return;
            commitPlacementEdit(() -> {
                MapPlacement selected = state.selectedPlacement();
                if (selected != null) selected.setCol(newV);
            });
        });
        placementRowSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingPlacementForm || newV == null) return;
            commitPlacementEdit(() -> {
                MapPlacement selected = state.selectedPlacement();
                if (selected != null) selected.setRow(newV);
            });
        });
        placementWidthSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingPlacementForm || newV == null) return;
            commitPlacementEdit(() -> {
                MapPlacement selected = state.selectedPlacement();
                if (selected != null) syncPlacementSize(selected, newV);
            });
        });
        placementHeightSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingPlacementForm || newV == null) return;
            commitPlacementEdit(() -> {
                MapPlacement selected = state.selectedPlacement();
                if (selected != null) syncPlacementSize(selected, newV);
            });
        });
        placementGridSizeSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingPlacementForm || newV == null) return;
            commitPlacementEdit(() -> {
                MapPlacement selected = state.selectedPlacement();
                if (selected != null) syncPlacementSize(selected, newV);
            });
        });
        placementRotationSpinner.valueProperty().addListener((obs, oldV, newV) -> {
            if (syncingPlacementForm || newV == null) return;
            commitPlacementEdit(() -> {
                MapPlacement selected = state.selectedPlacement();
                if (selected != null) selected.setRotation(newV);
            });
        });

        placementBlocksMoveCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingPlacementForm) return;
            commitPlacementEdit(() -> {
                MapPlacement selected = state.selectedPlacement();
                if (selected != null) selected.setBlocksMovement(newV);
            });
        });
        placementBlocksSightCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingPlacementForm) return;
            commitPlacementEdit(() -> {
                MapPlacement selected = state.selectedPlacement();
                if (selected != null) selected.setBlocksSight(newV);
            });
        });
        placementLockedCheck.selectedProperty().addListener((obs, oldV, newV) -> {
            if (syncingPlacementForm) return;
            commitPlacementEdit(() -> {
                MapPlacement selected = state.selectedPlacement();
                if (selected != null) selected.setLocked(newV);
            });
        });

        Button delete = new Button("Delete selected");
        delete.setOnAction(e -> deleteSelected());

        Button duplicate = new Button("Duplicate");
        duplicate.setOnAction(e -> duplicateSelected());

        Button moveToSelection = new Button("Move tool");
        moveToSelection.setOnAction(e -> state.setActiveTool(activeTool("move")));

        Button centerView = new Button("Center");
        centerView.setOnAction(e -> {
            state.setViewOffset(0, 0);
            canvas.requestRender();
        });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Name"), placementNameField);
        form.addRow(1, new Label("Layer"), placementLayerCombo);
        form.addRow(2, new Label("Micro location"), placementMicroLocationField);
        form.addRow(3, new Label("Col"), placementColSpinner);
        form.addRow(4, new Label("Row"), placementRowSpinner);
        form.addRow(5, new Label("Width"), placementWidthSpinner);
        form.addRow(6, new Label("Height"), placementHeightSpinner);
        form.addRow(7, new Label("Grid size"), placementGridSizeSpinner);
        form.addRow(8, new Label("Rotation"), placementRotationSpinner);

        VBox toggles = new VBox(6, placementBlocksMoveCheck, placementBlocksSightCheck, placementLockedCheck);

        box.getChildren().addAll(
                new Label("Type"),
                new Label(),
                new Label("Details"),
                form,
                new Label("Flags"),
                toggles,
                duplicate,
                delete,
                moveToSelection,
                centerView,
                layerLabel
        );

        setInspectorEnabled(false);
        return box;
    }

    private VBox buildMicroLocationPanel() {
        VBox box = new VBox(8);
        box.setPadding(new Insets(10));
        box.setPrefWidth(280);

        Label title = new Label("Micro locations");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

        microLocationList.setPrefHeight(180);
        microLocationList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(MicroLocationDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String base = item.getName() == null || item.getName().isBlank() ? item.getId() : item.getName();
                    String interior = item.getInteriorMapPath() == null || item.getInteriorMapPath().isBlank()
                            ? ""
                            : "  -> " + item.getInteriorMapPath();
                    setText(base + "  [" + item.getId() + "]" + interior);
                }
            }
        });
        microLocationList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selectedMicroLocationId = newV == null ? null : newV.getId();
            state.setSelectedMicroLocationId(selectedMicroLocationId);
            if (newV != null) {
                populateMicroLocationForm(newV);
            } else {
                clearMicroLocationForm();
            }
            canvas.requestRender();
        });

        microLocationIdField.setPromptText("id");
        microLocationNameField.setPromptText("Name");
        microLocationHintField.setPromptText("Hint / notes");
        microLocationInteriorPathField.setPromptText("interiors/<id>/map.json");
        microLocationColSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 0));
        microLocationRowSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 0));
        microLocationWidthSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 1));
        microLocationHeightSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 9999, 1));

        Button newZone = new Button("New zone");
        Button duplicateZone = new Button("Duplicate");
        Button deleteZone = new Button("Delete");
        Button assignSelectedPlacement = new Button("Assign selected placement");
        Button createInteriorMap = new Button("Create interior map");
        Button openInteriorMap = new Button("Open interior map");

        newZone.setMaxWidth(Double.MAX_VALUE);
        duplicateZone.setMaxWidth(Double.MAX_VALUE);
        deleteZone.setMaxWidth(Double.MAX_VALUE);
        assignSelectedPlacement.setMaxWidth(Double.MAX_VALUE);
        createInteriorMap.setMaxWidth(Double.MAX_VALUE);
        openInteriorMap.setMaxWidth(Double.MAX_VALUE);

        newZone.setOnAction(e -> createMicroLocationFromSelection());
        duplicateZone.setOnAction(e -> duplicateMicroLocation());
        deleteZone.setOnAction(e -> deleteMicroLocation());
        assignSelectedPlacement.setOnAction(e -> assignSelectedPlacementToMicroLocation());
        createInteriorMap.setOnAction(e -> createSelectedMicroLocationInterior());
        openInteriorMap.setOnAction(e -> openSelectedMicroLocationInterior());

        microLocationIdField.setOnAction(e -> commitMicroLocationEdit(this::applyMicroLocationForm));
        microLocationNameField.setOnAction(e -> commitMicroLocationEdit(this::applyMicroLocationForm));
        microLocationHintField.setOnAction(e -> commitMicroLocationEdit(this::applyMicroLocationForm));
        microLocationInteriorPathField.setOnAction(e -> commitMicroLocationEdit(this::applyMicroLocationForm));
        microLocationIdField.focusedProperty().addListener((obs, oldV, newV) -> { if (!newV) commitMicroLocationEdit(this::applyMicroLocationForm); });
        microLocationNameField.focusedProperty().addListener((obs, oldV, newV) -> { if (!newV) commitMicroLocationEdit(this::applyMicroLocationForm); });
        microLocationHintField.focusedProperty().addListener((obs, oldV, newV) -> { if (!newV) commitMicroLocationEdit(this::applyMicroLocationForm); });
        microLocationInteriorPathField.focusedProperty().addListener((obs, oldV, newV) -> { if (!newV) commitMicroLocationEdit(this::applyMicroLocationForm); });
        microLocationLockedCheck.selectedProperty().addListener((obs, oldV, newV) -> { if (!syncingMicroLocationForm) commitMicroLocationEdit(this::applyMicroLocationForm); });
        microLocationColSpinner.valueProperty().addListener((obs, oldV, newV) -> { if (!syncingMicroLocationForm && newV != null) commitMicroLocationEdit(this::applyMicroLocationForm); });
        microLocationRowSpinner.valueProperty().addListener((obs, oldV, newV) -> { if (!syncingMicroLocationForm && newV != null) commitMicroLocationEdit(this::applyMicroLocationForm); });
        microLocationWidthSpinner.valueProperty().addListener((obs, oldV, newV) -> { if (!syncingMicroLocationForm && newV != null) commitMicroLocationEdit(this::applyMicroLocationForm); });
        microLocationHeightSpinner.valueProperty().addListener((obs, oldV, newV) -> { if (!syncingMicroLocationForm && newV != null) commitMicroLocationEdit(this::applyMicroLocationForm); });

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.addRow(0, new Label("Id"), microLocationIdField);
        form.addRow(1, new Label("Name"), microLocationNameField);
        form.addRow(2, new Label("Col"), microLocationColSpinner);
        form.addRow(3, new Label("Row"), microLocationRowSpinner);
        form.addRow(4, new Label("Width"), microLocationWidthSpinner);
        form.addRow(5, new Label("Height"), microLocationHeightSpinner);
        form.addRow(6, new Label("Hint"), microLocationHintField);
        form.addRow(7, new Label("Interior"), microLocationInteriorPathField);
        form.addRow(8, new Label(""), microLocationLockedCheck);

        box.getChildren().addAll(
                title,
                microLocationList,
                form,
                microLocationStatusLabel,
                newZone,
                duplicateZone,
                deleteZone,
                assignSelectedPlacement,
                createInteriorMap,
                openInteriorMap
        );
        VBox.setVgrow(microLocationList, Priority.ALWAYS);
        refreshMicroLocationPanel();
        return box;
    }

    private void refreshBackgroundForm() {

        syncingBackgroundForm = true;
        try {
            BackgroundLayer bg = background();
            backgroundUrlField.setText(bg.getImageUrl() == null ? "" : bg.getImageUrl());
            backgroundModeCombo.getSelectionModel().select(bg.getMode() == null ? BackgroundMode.STRETCH : bg.getMode());
            backgroundVisibleCheck.setSelected(bg.isVisible());
            backgroundScaleSpinner.getValueFactory().setValue(bg.getScale());
            backgroundOffsetXSpinner.getValueFactory().setValue((int) Math.round(bg.getOffsetX()));
            backgroundOffsetYSpinner.getValueFactory().setValue((int) Math.round(bg.getOffsetY()));
            backgroundOpacitySlider.setValue(bg.getOpacity());
        } finally {
            syncingBackgroundForm = false;
        }
    }

    private void refreshReferenceForm() {
        syncingReferenceForm = true;
        try {
            ReferenceOverlay overlay = reference();
            AssetDefinition current = referenceAssets().stream()
                    .filter(asset -> Objects.equals(asset.getImageUrl(), overlay.getImageUrl()))
                    .findFirst()
                    .orElse(null);
            if (referenceAssetCombo.getItems().isEmpty()) {
                referenceAssetCombo.getItems().setAll(referenceAssets());
            }
            referenceAssetCombo.getSelectionModel().select(current);
            referenceVisibleCheck.setSelected(overlay.isVisible());
            referenceLockedCheck.setSelected(overlay.isLocked());
            referenceOpacitySlider.setValue(overlay.getOpacity());
            referenceScaleSpinner.getValueFactory().setValue(overlay.getScale());
            referenceRotationSpinner.getValueFactory().setValue(overlay.getRotation());
            referenceOffsetXSpinner.getValueFactory().setValue((int) Math.round(overlay.getOffsetX()));
            referenceOffsetYSpinner.getValueFactory().setValue((int) Math.round(overlay.getOffsetY()));
        } finally {
            syncingReferenceForm = false;
        }
    }

    private void refreshTerrainForm() {
        syncingTerrainForm = true;
        try {
            TerrainLayer terrain = terrain();
            terrainVisibleCheck.setSelected(terrain.isVisible());
            terrainLockedCheck.setSelected(terrain.isLocked());
            terrainOpacitySlider.setValue(terrain.getOpacity());
            terrainTypeCombo.getSelectionModel().select(terrain.getPaintType());
        } finally {
            syncingTerrainForm = false;
        }
    }

    private void refreshGridForm() {
        syncingGridForm = true;
        try {
            GridConfig grid = grid();
            gridCellSizeSpinner.getValueFactory().setValue(grid.getCellSize());
            gridColsSpinner.getValueFactory().setValue(grid.getCols());
            gridRowsSpinner.getValueFactory().setValue(grid.getRows());
            gridOffsetXSpinner.getValueFactory().setValue(grid.getOffsetX());
            gridOffsetYSpinner.getValueFactory().setValue(grid.getOffsetY());
        } finally {
            syncingGridForm = false;
        }
    }

    private void refreshWallForm() {
        syncingWallForm = true;
        try {
            WallLayer wall = wall();
            wallVisibleCheck.setSelected(wall.isVisible());
            wallLockedCheck.setSelected(wall.isLocked());
            wallOpacitySlider.setValue(wall.getOpacity());
            wallThicknessSpinner.getValueFactory().setValue(wall.getDefaultThickness());
            wallBlocksMoveCheck.setSelected(wall.isDefaultBlocksMovement());
            wallBlocksSightCheck.setSelected(wall.isDefaultBlocksSight());
        } finally {
            syncingWallForm = false;
        }
    }

    private void refreshFogForm() {
        syncingFogForm = true;
        try {
            FogSettings fog = fog();
            fogEnabledCheck.setSelected(fog.isEnabled());
            fogRevealFromTokensCheck.setSelected(fog.isRevealFromTokens());
            fogRevealFromSelectedCheck.setSelected(fog.isRevealFromSelectedPlacement());
            fogRadiusSpinner.getValueFactory().setValue(fog.getRevealRadius());
            fogOpacitySlider.setValue(fog.getOpacity());
        fogRetainExploredCheck.setSelected(fog.isRetainExploredCells());
        } finally {
            syncingFogForm = false;
        }
    }

    private BackgroundLayer background() {
        if (state.getProject() == null) {
            return new BackgroundLayer();
        }
        if (state.getProject().getBackgroundLayer() == null) {
            state.getProject().setBackgroundLayer(new BackgroundLayer());
        }
        return state.getProject().getBackgroundLayer();
    }

    private ReferenceOverlay reference() {
        if (state.getProject() == null) {
            return new ReferenceOverlay();
        }
        if (state.getProject().getReferenceOverlay() == null) {
            state.getProject().setReferenceOverlay(new ReferenceOverlay());
        }
        return state.getProject().getReferenceOverlay();
    }

    private List<AssetDefinition> referenceAssets() {
        if (assetCatalog == null) {
            return List.of();
        }
        return assetCatalog.getAssets().stream()
                .filter(this::isReferenceAsset)
                .toList();
    }

    private boolean isReferenceAsset(AssetDefinition asset) {
        if (asset == null) return false;
        String category = asset.getCategory();
        if (category != null && category.equalsIgnoreCase("reference")) {
            return true;
        }
        String imageUrl = asset.getImageUrl();
        return imageUrl != null && imageUrl.replace('\\', '/').contains("/maps/reference/");
    }

    private String referenceAssetLabel(AssetDefinition asset) {
        if (asset == null) {
            return "";
        }
        String name = asset.getName();
        String imageUrl = asset.getImageUrl();
        if (name == null || name.isBlank()) {
            return imageUrl == null ? "Unnamed reference" : imageUrl;
        }
        if (imageUrl == null || imageUrl.isBlank()) {
            return name;
        }
        return name + " — " + imageUrl;
    }

    private TerrainLayer terrain() {
        if (state.getProject() == null) {
            return new TerrainLayer();
        }
        if (state.getProject().getTerrainLayer() == null) {
            state.getProject().setTerrainLayer(new TerrainLayer());
        }
        return state.getProject().getTerrainLayer();
    }

    private WallLayer wall() {
        if (state.getProject() == null) {
            return new WallLayer();
        }
        if (state.getProject().getWallLayer() == null) {
            state.getProject().setWallLayer(new WallLayer());
        }
        return state.getProject().getWallLayer();
    }

    private FogSettings fog() {
        if (state.getProject() == null) {
            return new FogSettings();
        }
        if (state.getProject().getFogSettings() == null) {
            state.getProject().setFogSettings(new FogSettings());
        }
        return state.getProject().getFogSettings();
    }

    private void setBackgroundUrl(String url) {
        if (state.getProject() == null) {
            return;
        }
        state.getProject().setBackgroundUrl(url == null || url.isBlank() ? null : url.trim());
        refreshBackgroundForm();
        canvas.requestRender();
    }

    private void commitBackgroundEdit(Runnable action) {
        if (state.getProject() == null) {
            return;
        }
        state.recordHistory();
        action.run();
        canvas.requestRender();
        refreshBackgroundForm();
    }

    private void commitReferenceEdit(Runnable action) {
        if (state.getProject() == null) {
            return;
        }
        state.recordHistory();
        action.run();
        canvas.requestRender();
        refreshReferenceForm();
    }

    private void fitGridToReference() {
        if (state.getProject() == null) {
            return;
        }
        ReferenceOverlay overlay = reference();
        if (overlay.getImageUrl() == null || overlay.getImageUrl().isBlank()) {
            showError("Grid fit", new IllegalStateException("Reference image is not selected"));
            return;
        }
        try {
            state.recordHistory();
            var fitted = GridAlignmentService.fitToReference(state.getProject(), overlay);
            if (fitted.isEmpty()) {
                showError("Grid fit", new IllegalStateException("Could not detect a repeating grid pattern in the reference image"));
                return;
            }
            canvas.requestRender();
            refreshGridForm();
        } catch (Exception ex) {
            showError("Grid fit", ex);
        }
    }

    private void commitTerrainEdit(Runnable action) {
        if (state.getProject() == null) {
            return;
        }
        state.recordHistory();
        action.run();
        canvas.requestRender();
        refreshTerrainForm();
    }

    private void commitGridEdit(Runnable action) {
        if (state.getProject() == null) {
            return;
        }
        state.recordHistory();
        action.run();
        canvas.requestRender();
        refreshGridForm();
    }

    private void commitWallEdit(Runnable action) {
        if (state.getProject() == null) {
            return;
        }
        state.recordHistory();
        action.run();
        canvas.requestRender();
        refreshWallForm();
    }

    private void commitFogEdit(Runnable action) {
        if (state.getProject() == null) {
            return;
        }
        state.recordHistory();
        action.run();
        canvas.requestRender();
        refreshFogForm();
    }

    private void splitSelectedWall() {
        if (state.getProject() == null) {
            return;
        }
        WallPath selected = state.selectedWallPath();
        if (selected == null) {
            return;
        }
        int vertexIndex = state.getSelectedWallVertexIndex();
        if (vertexIndex <= 0 || vertexIndex >= selected.getPoints().size() - 1) {
            return;
        }

        commitWallEdit(() -> {
            WallPath tail = selected.splitAtVertex(vertexIndex);
            if (tail != null) {
                wall().addPath(tail);
                state.selectWallPath(tail.getId());
            }
        });
    }

    private void mergeSelectedWall() {
        if (state.getProject() == null) {
            return;
        }
        WallPath selected = state.selectedWallPath();
        if (selected == null || selected.getPoints().size() < 2) {
            return;
        }

        WallMergeCandidate candidate = findMergeCandidate(selected);
        if (candidate == null) {
            return;
        }

        commitWallEdit(() -> {
            WallPath other = wall().findPathById(candidate.otherPathId());
            if (other == null || selected.getId().equals(other.getId())) {
                return;
            }
            if (selected.mergeWith(other, candidate.appendToEnd(), candidate.reverseOther())) {
                wall().removePathById(other.getId());
                state.selectWallPath(selected.getId());
            }
        });
    }

    private WallMergeCandidate findMergeCandidate(WallPath selected) {
        if (selected == null || selected.getPoints().isEmpty() || state.getProject() == null) {
            return null;
        }
        double tolerance = 0.01;
        WallPoint start = selected.getFirstPoint();
        WallPoint end = selected.getLastPoint();
        if (start == null || end == null) {
            return null;
        }
        for (WallPath other : wall().getPaths()) {
            if (other == null || other == selected || other.getPoints().isEmpty()) {
                continue;
            }
            if (pointsNear(end, other.getFirstPoint(), tolerance)) {
                return new WallMergeCandidate(other.getId(), true, false);
            }
            if (pointsNear(end, other.getLastPoint(), tolerance)) {
                return new WallMergeCandidate(other.getId(), true, true);
            }
            if (pointsNear(start, other.getLastPoint(), tolerance)) {
                return new WallMergeCandidate(other.getId(), false, false);
            }
            if (pointsNear(start, other.getFirstPoint(), tolerance)) {
                return new WallMergeCandidate(other.getId(), false, true);
            }
        }
        return null;
    }

    private boolean pointsNear(WallPoint a, WallPoint b, double tolerance) {
        if (a == null || b == null) {
            return false;
        }
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return dx * dx + dy * dy <= tolerance * tolerance;
    }

    private record WallMergeCandidate(String otherPathId, boolean appendToEnd, boolean reverseOther) {}

    private void openTokenBrowser() {
        Window owner = getScene() == null ? null : getScene().getWindow();
        AssetBrowserWindow.showTokenBrowser(owner, assetCatalog, asset -> {
            if (asset == null) return;
            state.selectTokenAsset(asset.getId());
            if (state.getProject() != null) {
                MapLayer suggested = state.getProject().defaultLayerFor(asset.getKind());
                if (suggested != null) {
                    state.selectLayer(suggested.getId());
                }
            }
            state.setActiveTool(activeTool("token"));
            refreshSelection();
            canvas.requestRender();
        });
    }

    private void openObjectBrowser() {
        Window owner = getScene() == null ? null : getScene().getWindow();
        AssetBrowserWindow.showObjectWindow(owner, assetCatalog, asset -> {
            if (asset == null) return;
            state.selectObjectAsset(asset.getId());
            if (state.getProject() != null) {
                MapLayer suggested = state.getProject().defaultLayerFor(asset.getKind());
                if (suggested != null) {
                    state.selectLayer(suggested.getId());
                }
            }
            state.setActiveTool(activeTool("place"));
            refreshSelection();
            canvas.requestRender();
        });
    }

    private void refreshSelection() {
        if (state.selectedPlacement() != null) {
            selectionLabel.setText("Selected: " + displayName(state.selectedPlacement().getName(), state.selectedPlacement().getAssetId()));
            layerLabel.setText("Layer: " + safeLayerName(state.selectedPlacement().getLayerId()));
        } else if (state.selectedWallPath() != null) {
            String wallName = state.selectedWallPath().getName() == null ? state.selectedWallPath().getId() : state.selectedWallPath().getName();
            String vertexInfo = state.getSelectedWallVertexIndex() >= 0 ? " | Vertex: " + state.getSelectedWallVertexIndex() : "";
            selectionLabel.setText("Wall: " + wallName + vertexInfo);
            layerLabel.setText("Layer: Walls");
        } else if (state.getSelectedMicroLocationId() != null && state.getProject() != null && state.getProject().findMicroLocation(state.getSelectedMicroLocationId()).isPresent()) {
            MicroLocationDto zone = state.getProject().findMicroLocation(state.getSelectedMicroLocationId()).orElse(null);
            String zoneName = zone == null ? state.getSelectedMicroLocationId() : displayName(zone.getName(), zone.getId());
            selectionLabel.setText("Micro location: " + zoneName + " @ " + (zone == null ? "?" : zone.getCol() + "," + zone.getRow()) + " " + (zone == null ? "" : zone.getWidth() + "x" + zone.getHeight()));
            layerLabel.setText("Layer: map zones");
        } else if (state.selectedAsset() != null) {
            selectionLabel.setText("Asset: " + state.selectedAsset().getName());
            layerLabel.setText("Layer: " + safeLayerName(state.getSelectedLayerId()));
        } else {
            selectionLabel.setText("Nothing selected");
            layerLabel.setText("Layer: " + safeLayerName(state.getSelectedLayerId()));
        }
        refreshPlacementForm();
        refreshBackgroundForm();
        refreshReferenceForm();
        refreshTerrainForm();
        refreshGridForm();
        refreshWallForm();
        refreshFogForm();
        refreshMicroLocationPanel();
        refreshLayerList();
    }

    private void refreshPlacementForm() {
        syncingPlacementForm = true;
        try {
            MapPlacement selected = state.selectedPlacement();
            boolean hasSelection = selected != null;
            setInspectorEnabled(hasSelection);
            if (!hasSelection) {
                placementNameField.setText("");
                placementLayerCombo.getItems().clear();
                placementColSpinner.getValueFactory().setValue(0);
                placementRowSpinner.getValueFactory().setValue(0);
                placementWidthSpinner.getValueFactory().setValue(1);
                placementHeightSpinner.getValueFactory().setValue(1);
                placementGridSizeSpinner.getValueFactory().setValue(1);
                placementRotationSpinner.getValueFactory().setValue(0.0);
                placementBlocksMoveCheck.setSelected(false);
                placementBlocksSightCheck.setSelected(false);
                placementLockedCheck.setSelected(false);
                placementMicroLocationField.setText("");
                return;
            }

            placementNameField.setText(selected.getName() == null ? selected.getAssetId() : selected.getName());
            placementMicroLocationField.setText(selected.getMicroLocationId() == null ? "" : selected.getMicroLocationId());
            if (state.getProject() != null) {
                placementLayerCombo.getItems().setAll(state.getProject().getLayers());
                placementLayerCombo.getSelectionModel().select(state.selectedLayer() != null ? state.selectedLayer() : state.getProject().findLayer(selected.getLayerId()).orElse(null));
            }
            placementColSpinner.getValueFactory().setValue(selected.getCol());
            placementRowSpinner.getValueFactory().setValue(selected.getRow());
            int size = Math.max(1, selected.getKind() == PlacementKind.TOKEN || selected.getKind() == PlacementKind.SPAWN
                    ? selected.getGridSize()
                    : Math.max(selected.getWidth(), selected.getHeight()));
            placementWidthSpinner.getValueFactory().setValue(selected.getKind() == PlacementKind.TOKEN || selected.getKind() == PlacementKind.SPAWN ? size : selected.getWidth());
            placementHeightSpinner.getValueFactory().setValue(selected.getKind() == PlacementKind.TOKEN || selected.getKind() == PlacementKind.SPAWN ? size : selected.getHeight());
            placementGridSizeSpinner.getValueFactory().setValue(size);
            placementRotationSpinner.getValueFactory().setValue(selected.getRotation());
            placementBlocksMoveCheck.setSelected(selected.isBlocksMovement());
            placementBlocksSightCheck.setSelected(selected.isBlocksSight());
            placementLockedCheck.setSelected(selected.isLocked());
        } finally {
            syncingPlacementForm = false;
        }
    }

    private void syncPlacementSize(MapPlacement placement, int size) {
        if (placement == null) return;
        if (placement.getKind() == PlacementKind.TOKEN || placement.getKind() == PlacementKind.SPAWN) {
            int normalized = Math.max(1, Math.min(10, size));
            placement.setGridSize(normalized);
            placement.setWidth(normalized);
            placement.setHeight(normalized);
        } else {
            int normalized = Math.max(1, size);
            placement.setWidth(normalized);
            placement.setHeight(normalized);
        }
    }

    private void setInspectorEnabled(boolean enabled) {
        placementNameField.setDisable(!enabled);
        placementLayerCombo.setDisable(!enabled);
        placementMicroLocationField.setDisable(!enabled);
        placementColSpinner.setDisable(!enabled);
        placementRowSpinner.setDisable(!enabled);
        placementWidthSpinner.setDisable(!enabled);
        placementHeightSpinner.setDisable(!enabled);
        placementGridSizeSpinner.setDisable(!enabled);
        placementRotationSpinner.setDisable(!enabled);
        placementBlocksMoveCheck.setDisable(!enabled);
        placementBlocksSightCheck.setDisable(!enabled);
        placementLockedCheck.setDisable(!enabled);
    }

    private void commitMicroLocationEdit(Runnable action) {
        if (state.getProject() == null) return;
        state.recordHistory();
        action.run();
        canvas.requestRender();
        refreshMicroLocationPanel();
    }

    private void commitPlacementEdit(Runnable action) {
        MapPlacement selected = state.selectedPlacement();
        if (selected == null || selected.isLocked() || state.isLayerLocked(selected.getLayerId())) {
            refreshPlacementForm();
            return;
        }
        state.recordHistory();
        action.run();
        canvas.requestRender();
        refreshSelection();
    }

    private void refreshMicroLocationPanel() {
        MapProject project = state.getProject();
        if (project == null) {
            microLocationList.getItems().clear();
            clearMicroLocationForm();
            state.setSelectedMicroLocationId(null);
            microLocationStatusLabel.setText("No project loaded");
            return;
        }
        syncingMicroLocationForm = true;
        try {
            MicroLocationDto selected = getSelectedMicroLocation();
            if (selected == null && selectedMicroLocationId != null) {
                selectedMicroLocationId = null;
                state.setSelectedMicroLocationId(null);
            }

            microLocationList.getItems().setAll(project.getMicroLocations());
            if (selected != null) {
                int idx = microLocationList.getItems().indexOf(selected);
                if (idx >= 0) {
                    microLocationList.getSelectionModel().select(idx);
                }
                state.setSelectedMicroLocationId(selected.getId());
                populateMicroLocationForm(selected);
                String selectedIdText = selected.getId() == null ? "" : selected.getId();
                microLocationStatusLabel.setText("Zones: " + project.getMicroLocations().size() + " | selected: " + selectedIdText);
            } else {
                if (microLocationList.getItems().isEmpty()) {
                    microLocationStatusLabel.setText("No micro locations yet");
                } else if (microLocationList.getSelectionModel().getSelectedItem() == null) {
                    microLocationList.getSelectionModel().selectFirst();
                }
                if (microLocationList.getSelectionModel().getSelectedItem() == null) {
                    clearMicroLocationForm();
                    state.setSelectedMicroLocationId(null);
                } else {
                    state.setSelectedMicroLocationId(microLocationList.getSelectionModel().getSelectedItem().getId());
                }
                microLocationStatusLabel.setText("Zones: " + project.getMicroLocations().size());
            }
        } finally {
            syncingMicroLocationForm = false;
        }
    }

    private void populateMicroLocationForm(MicroLocationDto zone) {
        if (zone == null) {
            clearMicroLocationForm();
            return;
        }
        microLocationIdField.setText(zone.getId() == null ? "" : zone.getId());
        microLocationNameField.setText(zone.getName() == null ? "" : zone.getName());
        microLocationHintField.setText(zone.getHint() == null ? "" : zone.getHint());
        microLocationInteriorPathField.setText(zone.getInteriorMapPath() == null ? "" : zone.getInteriorMapPath());
        microLocationColSpinner.getValueFactory().setValue(zone.getCol());
        microLocationRowSpinner.getValueFactory().setValue(zone.getRow());
        microLocationWidthSpinner.getValueFactory().setValue(zone.getWidth());
        microLocationHeightSpinner.getValueFactory().setValue(zone.getHeight());
        microLocationLockedCheck.setSelected(zone.isLocked());
    }

    private void clearMicroLocationForm() {
        microLocationIdField.setText("");
        microLocationNameField.setText("");
        microLocationHintField.setText("");
        microLocationInteriorPathField.setText("");
        microLocationColSpinner.getValueFactory().setValue(0);
        microLocationRowSpinner.getValueFactory().setValue(0);
        microLocationWidthSpinner.getValueFactory().setValue(1);
        microLocationHeightSpinner.getValueFactory().setValue(1);
        microLocationLockedCheck.setSelected(false);
    }

    private void createMicroLocationFromSelection() {
        if (state.getProject() == null) return;
        MicroLocationDto zone = new MicroLocationDto();
        zone.setId(java.util.UUID.randomUUID().toString());
        zone.setName("New zone");
        int col = 0;
        int row = 0;
        int width = 1;
        int height = 1;

        MapPlacement selectedPlacement = state.selectedPlacement();
        if (selectedPlacement != null) {
            col = Math.max(0, selectedPlacement.getCol());
            row = Math.max(0, selectedPlacement.getRow());
            width = Math.max(1, selectedPlacement.effectiveWidth());
            height = Math.max(1, selectedPlacement.effectiveHeight());
        }

        zone.setCol(col);
        zone.setRow(row);
        zone.setWidth(width);
        zone.setHeight(height);
        zone.setLocked(false);
        zone.setHint("");
        zone.setInteriorMapPath(defaultInteriorPath(zone.getId()));
        state.recordHistory();
        state.getProject().addMicroLocation(zone);
        selectedMicroLocationId = zone.getId();
        state.setSelectedMicroLocationId(selectedMicroLocationId);
        refreshMicroLocationPanel();
        canvas.requestRender();
    }

    private void duplicateMicroLocation() {
        MapProject project = state.getProject();
        if (project == null) return;
        MicroLocationDto selected = getSelectedMicroLocation();
        if (selected == null) return;
        MicroLocationDto copy = copyMicroLocation(selected);
        copy.setId(java.util.UUID.randomUUID().toString());
        copy.setName((selected.getName() == null || selected.getName().isBlank() ? "Zone" : selected.getName()) + " copy");
        copy.setCol(selected.getCol() + 1);
        copy.setRow(selected.getRow() + 1);
        copy.setInteriorMapPath(defaultInteriorPath(copy.getId()));
        state.recordHistory();
        project.addMicroLocation(copy);
        selectedMicroLocationId = copy.getId();
        state.setSelectedMicroLocationId(selectedMicroLocationId);
        refreshMicroLocationPanel();
        canvas.requestRender();
    }

    private void deleteMicroLocation() {
        MapProject project = state.getProject();
        if (project == null) return;
        MicroLocationDto selected = getSelectedMicroLocation();
        if (selected == null) return;
        state.recordHistory();
        project.removeMicroLocationById(selected.getId());
        for (MapPlacement placement : project.getPlacements()) {
            if (selected.getId() != null && selected.getId().equals(placement.getMicroLocationId())) {
                placement.setMicroLocationId(null);
            }
        }
        selectedMicroLocationId = null;
        state.setSelectedMicroLocationId(null);
        canvas.requestRender();
        refreshMicroLocationPanel();
        refreshPlacementForm();
    }

    private void assignSelectedPlacementToMicroLocation() {
        MapPlacement placement = state.selectedPlacement();
        MicroLocationDto zone = getSelectedMicroLocation();
        if (placement == null || zone == null) return;
        commitPlacementEdit(() -> placement.setMicroLocationId(zone.getId()));
    }

    private void createSelectedMicroLocationInterior() {
        MicroLocationDto zone = getSelectedMicroLocation();
        if (zone == null) {
            return;
        }
        if (microLocationInteriorPathField.getText() == null || microLocationInteriorPathField.getText().isBlank()) {
            microLocationInteriorPathField.setText(defaultInteriorPath(zone.getId()));
            commitMicroLocationEdit(this::applyMicroLocationForm);
        }
        openSelectedMicroLocationInterior(true);
    }

    private void openSelectedMicroLocationInterior() {
        openSelectedMicroLocationInterior(false);
    }

    private void openSelectedMicroLocationInterior(boolean createIfMissing) {
        MapProject project = state.getProject();
        MicroLocationDto zone = getSelectedMicroLocation();
        if (project == null || zone == null) {
            return;
        }
        Path interiorPath = resolveInteriorPath(zone);
        if (interiorPath == null) {
            showError("Interior map", new IllegalStateException("Interior map path is not set"));
            return;
        }
        try {
            if (!Files.exists(interiorPath)) {
                if (!createIfMissing) {
                    showError("Interior map", new IllegalStateException("Interior map does not exist yet"));
                    return;
                }
                MapProject interior = MapProject.createBlank(zone.getId(), zone.getName() == null || zone.getName().isBlank() ? zone.getId() : zone.getName());
                repository.saveWorkspace(interiorPath.getParent(), interior);
            }
            openDocumentCallback.accept(interiorPath);
        } catch (Exception ex) {
            showError("Interior map", ex);
        }
    }

    private MicroLocationDto getSelectedMicroLocation() {
        MapProject project = state.getProject();
        if (project == null) return null;
        if (selectedMicroLocationId != null) {
            MicroLocationDto zone = project.findMicroLocation(selectedMicroLocationId).orElse(null);
            if (zone != null) return zone;
        }
        return microLocationList.getSelectionModel().getSelectedItem();
    }

    private void applyMicroLocationForm() {
        MapProject project = state.getProject();
        if (project == null) return;
        MicroLocationDto selected = getSelectedMicroLocation();
        if (selected == null) return;

        String id = microLocationIdField.getText() == null ? "" : microLocationIdField.getText().trim();
        if (id.isBlank()) {
            id = selected.getId();
        }

        MicroLocationDto updated = new MicroLocationDto();
        updated.setId(id);
        updated.setName(safeTrim(microLocationNameField.getText()));
        updated.setHint(safeTrim(microLocationHintField.getText()));
        updated.setInteriorMapPath(normalizeInteriorField(id));
        updated.setCol(safeSpinnerInt(microLocationColSpinner, selected.getCol()));
        updated.setRow(safeSpinnerInt(microLocationRowSpinner, selected.getRow()));
        updated.setWidth(safeSpinnerInt(microLocationWidthSpinner, selected.getWidth()));
        updated.setHeight(safeSpinnerInt(microLocationHeightSpinner, selected.getHeight()));
        updated.setLocked(microLocationLockedCheck.isSelected());

        String oldId = selected.getId();
        state.recordHistory();
        project.updateMicroLocation(oldId, updated);
        if (!java.util.Objects.equals(oldId, updated.getId())) {
            for (MapPlacement placement : project.getPlacements()) {
                if (java.util.Objects.equals(oldId, placement.getMicroLocationId())) {
                    placement.setMicroLocationId(updated.getId());
                }
            }
        }
        selectedMicroLocationId = updated.getId();
        state.setSelectedMicroLocationId(selectedMicroLocationId);
        canvas.requestRender();
        refreshMicroLocationPanel();
        refreshPlacementForm();
    }

    private String normalizeInteriorField(String zoneId) {
        String raw = microLocationInteriorPathField.getText();
        if (raw == null || raw.isBlank()) {
            return defaultInteriorPath(zoneId);
        }
        String trimmed = raw.trim();
        if (documentRoot == null) {
            return trimmed;
        }
        Path resolved = repository.resolveChild(documentRoot, trimmed);
        Path root = documentRoot.toAbsolutePath().normalize();
        try {
            Path relative = root.relativize(resolved);
            return relative.toString().replace('\\', '/');
        } catch (Exception ex) {
            return trimmed;
        }
    }

    private String defaultInteriorPath(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return "interiors/map.json";
        }
        return "interiors/" + zoneId + "/map.json";
    }

    private Path resolveInteriorPath(MicroLocationDto zone) {
        String path = zone == null ? null : zone.getInteriorMapPath();
        if (path == null || path.isBlank()) {
            path = defaultInteriorPath(zone == null ? null : zone.getId());
        }
        return documentRoot == null ? Path.of(path) : repository.resolveChild(documentRoot, path);
    }

    private static MicroLocationDto copyMicroLocation(MicroLocationDto source) {
        if (source == null) return null;
        MicroLocationDto copy = new MicroLocationDto();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setCol(source.getCol());
        copy.setRow(source.getRow());
        copy.setWidth(source.getWidth());
        copy.setHeight(source.getHeight());
        copy.setLocked(source.isLocked());
        copy.setHint(source.getHint());
        copy.setInteriorMapPath(source.getInteriorMapPath());
        return copy;
    }

    private static String safeTrim(String value) {

        return value == null ? null : (value.isBlank() ? null : value.trim());
    }

    private static int safeSpinnerInt(Spinner<Integer> spinner, int fallback) {
        Integer value = spinner == null ? null : spinner.getValue();
        return value == null ? fallback : value;
    }

    private void duplicateSelected() {
        MapPlacement selected = state.selectedPlacement();
        if (selected == null || state.getProject() == null) return;
        if (selected.isLocked() || state.isLayerLocked(selected.getLayerId())) return;

        state.recordHistory();
        MapPlacement copy = selected.copy();
        copy.setId(java.util.UUID.randomUUID().toString());
        copy.setCol(selected.getCol() + 1);
        copy.setRow(selected.getRow() + 1);
        copy.setSelected(false);
        state.getProject().addPlacement(copy);
        state.selectPlacement(copy.getId());
        canvas.requestRender();
        refreshSelection();
    }

    private void nudgeSelected(int dx, int dy) {
        MapPlacement selected = state.selectedPlacement();
        if (selected == null || state.getProject() == null) return;
        if (selected.isLocked() || state.isLayerLocked(selected.getLayerId())) return;

        var grid = state.grid();
        int maxCol = Math.max(0, grid.getCols() - selected.effectiveWidth());
        int maxRow = Math.max(0, grid.getRows() - selected.effectiveHeight());

        int newCol = Math.max(0, Math.min(maxCol, selected.getCol() + dx));
        int newRow = Math.max(0, Math.min(maxRow, selected.getRow() + dy));
        if (newCol == selected.getCol() && newRow == selected.getRow()) {
            return;
        }
        state.recordHistory();
        selected.setCol(newCol);
        selected.setRow(newRow);
        canvas.requestRender();
        refreshSelection();
    }

    private void refreshLayerList() {
        if (state.getProject() == null) return;
        syncingLayerSelection = true;
        try {
            layerList.setItems(FXCollections.observableArrayList(state.getProject().getLayers()));
            MapLayer selectedLayer = state.selectedLayer();
            if (selectedLayer != null) {
                int index = state.getProject().getLayers().indexOf(selectedLayer);
                if (index >= 0) layerList.getSelectionModel().select(index);
                else layerList.getSelectionModel().clearSelection();
            } else {
                layerList.getSelectionModel().clearSelection();
            }
        } finally {
            syncingLayerSelection = false;
        }
    }

    private String safeLayerName(String layerId) {
        if (layerId == null || state.getProject() == null) return "-";
        return state.getProject().findLayer(layerId).map(MapLayer::getName).orElse(layerId);
    }

    private String displayName(String primary, String fallback) {
        return primary == null ? fallback : primary;
    }

    private void deleteSelected() {
        if (state.getProject() == null) {
            return;
        }

        if (state.getSelectedPlacementId() != null) {
            state.recordHistory();
            state.getProject().removePlacementById(state.getSelectedPlacementId());
            state.clearSelection();
            canvas.requestRender();
            refreshSelection();
            return;
        }

        if (state.selectedWallPath() != null) {
            state.recordHistory();
            WallPath wall = state.selectedWallPath();
            int vertexIndex = state.getSelectedWallVertexIndex();
            if (vertexIndex >= 0 && wall.removePoint(vertexIndex)) {
                if (wall.getPoints().size() < 2) {
                    state.getProject().getWallLayer().removePathById(wall.getId());
                    state.selectWallPath(null);
                } else {
                    state.selectWallVertex(wall.getId(), Math.min(vertexIndex, wall.getPoints().size() - 1));
                }
            } else {
                state.getProject().getWallLayer().removePathById(wall.getId());
                state.selectWallPath(null);
            }
            canvas.requestRender();
            refreshSelection();
        }
    }

    private void saveProject() {
        MapProject project = state.getProject();
        if (project == null) {
            return;
        }
        try {
            MapProject snapshot = project.copy();
            Path targetRoot = documentRoot;
            if (targetRoot == null) {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle("Save map workspace");
                var dir = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
                if (dir == null) {
                    return;
                }
                targetRoot = dir.toPath();
            }
            repository.saveWorkspace(targetRoot, snapshot);
            setDocumentRoot(targetRoot);
        } catch (Exception ex) {
            showError("Save failed", ex);
        }
    }

    private void scheduleBackupSave() {
        backupAutosaveDebounce.playFromStart();
    }

    private void saveBackupSnapshot() {
        MapProject project = state.getProject();
        if (project == null) {
            return;
        }
        MapProject snapshot = project.copy();
        Thread t = new Thread(() -> {
            try {
                repository.saveBackup(snapshot);
            } catch (Exception ex) {
                System.err.println("Backup autosave failed: " + ex.getMessage());
            }
        }, "map-editor-autosave");
        t.setDaemon(true);
        t.start();
    }

    private void loadProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Load map workspace");
        var dir = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
        if (dir == null) return;
        try {
            Path loadedRoot = dir.toPath();
            MapProject loaded = repository.loadWorkspace(loadedRoot);
            setDocumentRoot(loadedRoot);
            state.setProject(loaded);
            canvas.requestRender();
            refreshSelection();
            refreshBackgroundForm();
            refreshReferenceForm();
            refreshTerrainForm();
            refreshWallForm();
            refreshFogForm();
            refreshGridForm();
            refreshLayerList();
            updateDocumentTitle();
        } catch (Exception ex) {
            showError("Load failed", ex);
        }
    }

    private void exportLayout() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export shared layout");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        var file = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return;
        try {
            MapLayoutUpdateDto layout = SharedProjectMapper.toLayoutDto(state.getProject());
            repository.saveLayout(Path.of(file.toURI()), layout);
        } catch (Exception ex) {
            showError("Export failed", ex);
        }
    }

    private void importLayout() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import shared layout");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        var file = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (file == null) return;
        try {
            MapLayoutUpdateDto layout = repository.loadLayout(Path.of(file.toURI()));
            state.setProject(SharedProjectMapper.fromLayoutDto("imported-project", "Imported Map", layout));
            canvas.requestRender();
            refreshSelection();
            refreshBackgroundForm();
            refreshReferenceForm();
            refreshTerrainForm();
            refreshWallForm();
            refreshFogForm();
            refreshGridForm();
            refreshLayerList();
        } catch (Exception ex) {
            showError("Import failed", ex);
        }
    }


    private boolean contains(String value, String filter) {
        return value != null && value.toLowerCase().contains(filter);
    }

    private void undo() {
        if (state.undo()) {
            canvas.requestRender();
            refreshSelection();
            refreshBackgroundForm();
            refreshReferenceForm();
            refreshTerrainForm();
            refreshWallForm();
            refreshFogForm();
            refreshGridForm();
            refreshLayerList();
        }
    }

    private void redo() {
        if (state.redo()) {
            canvas.requestRender();
            refreshSelection();
            refreshBackgroundForm();
            refreshReferenceForm();
            refreshTerrainForm();
            refreshWallForm();
            refreshFogForm();
            refreshGridForm();
            refreshLayerList();
        }
    }

    private void onAddLayer() {
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

        refreshLayerList();
        refreshSelection();
        canvas.requestRender();
    }

    private void onRemoveSelectedLayer() {
        MapProject project = state.getProject();
        if (project == null) {
            return;
        }

        MapLayer selected = state.selectedLayer();
        if (selected == null) {
            return;
        }

        if (project.getLayers().size() <= 1) {
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

        refreshLayerList();
        refreshSelection();
        canvas.requestRender();
    }

    private void onToggleSelectedLayerVisible() {
        MapLayer selected = state.selectedLayer();
        if (selected == null) {
            return;
        }

        state.recordHistory();
        selected.setVisible(!selected.isVisible());

        refreshLayerList();
        refreshSelection();
        canvas.requestRender();
    }

    private void onToggleSelectedLayerLocked() {
        MapLayer selected = state.selectedLayer();
        if (selected == null) {
            return;
        }

        state.recordHistory();
        selected.setLocked(!selected.isLocked());

        refreshLayerList();
        refreshSelection();
        canvas.requestRender();
    }

    private void installAccelerators(Scene scene) {
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), this::saveProject);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), this::loadProject);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), this::undo);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), this::redo);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN), this::duplicateSelected);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DELETE), this::deleteSelected);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN), this::mergeSelectedWall);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), this::splitSelectedWall);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.LEFT), () -> nudgeSelected(-1, 0));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.RIGHT), () -> nudgeSelected(1, 0));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.UP), () -> nudgeSelected(0, -1));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DOWN), () -> nudgeSelected(0, 1));
    }

    private void showError(String title, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(ex.getMessage());
        alert.showAndWait();
    }

    private static class VBoxWrapper extends VBox {
        VBoxWrapper(String title, Node content) {
            setSpacing(10);
            setPadding(new Insets(12));
            getChildren().addAll(new Label(title), content);
            VBox.setVgrow(content, Priority.ALWAYS);
        }
    }
}
