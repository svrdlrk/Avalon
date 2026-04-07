package com.avalon.dnd.dm.ui;

import com.avalon.dnd.dm.canvas.BattleMapCanvas;
import com.avalon.dnd.dm.net.ServerConnection;
import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.*;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.HashSet;
import java.util.Set;

public class MainStage {

    private final Stage stage;
    private BattleMapCanvas mapCanvas;

    private ComboBox<TokenDto> tokenActionsCombo;
    private ComboBox<PlayerDto> playerAssignCombo;
    private ComboBox<MapObjectDto> objectRemoveCombo;
    private Spinner<Integer> objectColSpinner;
    private Spinner<Integer> objectRowSpinner;
    private Runnable dmUiRefreshHandler;

    // Хранит текущий serverUrl для uploadMap
    private String currentServerUrl = "http://localhost:8080";

    public MainStage(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        stage.setTitle("Avalon DnD — DM");
        stage.setScene(new Scene(buildConnectForm(), 1024, 768));
        stage.show();
    }

    // ------------------------------------------------------------------ connect

    private VBox buildConnectForm() {
        VBox form = new VBox(10);
        form.setPadding(new Insets(20));

        Label title = new Label("Avalon DnD — подключение");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        TextField serverField = new TextField("http://localhost:8080");
        TextField sessionField = new TextField();
        sessionField.setPromptText("ID сессии");
        TextField nameField = new TextField("DM");
        TextField playerClientUrlField = new TextField("http://localhost:5173");

        Label localHint = new Label(
                "Игроки открывают player-client (npm run dev) и вводят ID сессии.");
        localHint.setWrapText(true);
        localHint.setStyle("-fx-text-fill: #555;");

        Button createBtn = new Button("Создать сессию");
        Button connectBtn = new Button("Подключиться");
        Label statusLabel = new Label("");

        // FIX: createSession теперь принимает callback и обновляет sessionField
        createBtn.setOnAction(e -> {
            String serverUrl = serverField.getText().trim();
            createBtn.setDisable(true);
            statusLabel.setText("Создание сессии...");
            ServerConnection.getInstance().createSession(serverUrl, sessionId -> {
                createBtn.setDisable(false);
                if (sessionId != null) {
                    sessionField.setText(sessionId);
                    statusLabel.setText("✅ Сессия создана: " + sessionId);
                } else {
                    statusLabel.setText("❌ Не удалось создать сессию");
                }
            });
        });

        connectBtn.setOnAction(e -> {
            String serverUrl = serverField.getText().trim();
            String sessionId = sessionField.getText().trim();
            String name = nameField.getText().trim();

            if (serverUrl.isEmpty() || sessionId.isEmpty() || name.isEmpty()) {
                statusLabel.setText("Заполните все поля");
                return;
            }

            statusLabel.setText("Подключение...");
            currentServerUrl = serverUrl;

            ServerConnection.getInstance().connect(
                    serverUrl,
                    sessionId,
                    name,
                    true,
                    v -> javafx.application.Platform.runLater(
                            () -> switchToBattleMap(
                                    playerClientUrlField.getText().trim(),
                                    sessionId)
                    )
            );
        });

        form.getChildren().addAll(
                title,
                new Label("Сервер:"), serverField,
                new Label("ID сессии:"), sessionField,
                new Label("Имя:"), nameField,
                new Label("Player-client (локально):"), playerClientUrlField,
                localHint,
                createBtn, connectBtn, statusLabel
        );
        return form;
    }

    // ------------------------------------------------------------------ battle map

    private void switchToBattleMap(String playerClientBase, String sessionId) {
        mapCanvas = new BattleMapCanvas();

        // Применяем фон если уже есть
        String bgUrl = ClientState.getInstance().getBackgroundUrl();
        if (bgUrl != null && !bgUrl.isEmpty()) {
            mapCanvas.setBackground(currentServerUrl + bgUrl);
        }

        // --- Toolbar 1: токены ---
        TextField tokenNameField = new TextField("Гоблин");
        tokenNameField.setPrefWidth(120);

        Button addTokenBtn = new Button("Добавить NPC");
        addTokenBtn.setOnAction(e -> addNpcToken(tokenNameField.getText()));

        tokenActionsCombo = makeCombo(200, t -> {
            if (t == null) return "";
            String own = t.getOwnerId() == null ? "NPC" : "игрок…" + shortId(t.getOwnerId());
            return t.getName() + " (" + own + ")";
        });

        playerAssignCombo = makeCombo(180, p -> p == null ? "" :
                p.getName() + " (" + shortId(p.getId()) + ")");

        Button assignBtn = new Button("Назначить игроку");
        assignBtn.setOnAction(e -> assignSelectedTokenToPlayer());

        Button unassignBtn = new Button("Сделать NPC");
        unassignBtn.setOnAction(e -> unassignSelectedToken());

        Button removeTokenBtn = new Button("Удалить токен");
        removeTokenBtn.setOnAction(e -> removeSelectedToken());

        Button copyIdBtn = new Button("Копировать ID сессии");
        copyIdBtn.setOnAction(e -> {
            var cb = javafx.scene.input.Clipboard.getSystemClipboard();
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(sessionId);
            cb.setContent(content);
        });

        ToolBar bar1 = new ToolBar(
                new Label("Токены:"), tokenNameField, addTokenBtn, new Separator(),
                tokenActionsCombo, playerAssignCombo, assignBtn, unassignBtn, removeTokenBtn,
                new Separator(), copyIdBtn
        );

        // --- Toolbar 2: карта/объекты ---
        var g0 = ClientState.getInstance().getGrid();
        objectColSpinner = makeSpinner(0, Math.max(0, g0.getCols() - 1), 0);
        objectRowSpinner = makeSpinner(0, Math.max(0, g0.getRows() - 1), 0);

        Button placeWallBtn = new Button("Стена 1×1");
        placeWallBtn.setTooltip(new Tooltip("Препятствие в клетке col, row"));
        placeWallBtn.setOnAction(e -> placeWall1x1());

        objectRemoveCombo = makeCombo(200, o -> o == null ? "" :
                o.getType() + " @(" + o.getCol() + "," + o.getRow() + ")");

        Button removeObjectBtn = new Button("Удалить объект");
        removeObjectBtn.setOnAction(e -> removeSelectedObject());

        ToolBar bar2 = new ToolBar(
                new Label("Карта:"),
                new Label("col"), objectColSpinner,
                new Label("row"), objectRowSpinner,
                placeWallBtn, new Separator(),
                objectRemoveCombo, removeObjectBtn
        );

        // --- Toolbar 3: сетка + загрузка фона ---
        Spinner<Integer> colsSpinner = makeSpinner(4, 60, g0.getCols());
        Spinner<Integer> rowsSpinner = makeSpinner(4, 60, g0.getRows());
        Spinner<Integer> cellSpinner = makeSpinner(24, 128, g0.getCellSize());

        Button applyGridBtn = new Button("Применить размер сетки");
        applyGridBtn.setOnAction(e -> applyGridResize(
                colsSpinner.getValue(), rowsSpinner.getValue(), cellSpinner.getValue()));

        Button uploadMapBtn = new Button("Загрузить карту (фон)");
        uploadMapBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.webp"));
            java.io.File file = fc.showOpenDialog(stage);
            if (file != null) {
                // FIX: используем callback — получаем URL и сразу применяем фон
                ServerConnection.getInstance().uploadMap(
                        currentServerUrl,
                        ClientState.getInstance().getSessionId(),
                        file,
                        url -> {
                            if (url != null) {
                                String fullUrl = currentServerUrl + url;
                                ClientState.getInstance().setBackgroundUrl(url);
                                mapCanvas.setBackground(fullUrl);
                            }
                        }
                );
            }
        });

        ToolBar bar3 = new ToolBar(
                new Label("Сетка:"),
                new Label("cols"), colsSpinner,
                new Label("rows"), rowsSpinner,
                new Label("cell"), cellSpinner,
                applyGridBtn,
                new Separator(),
                uploadMapBtn
        );

        String linkHint = (playerClientBase.isEmpty() ? "http://localhost:5173" : playerClientBase)
                + "  →  сессия: " + sessionId;
        Label sessionLinkLabel = new Label(linkHint);
        sessionLinkLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        VBox top = new VBox(6);
        top.setPadding(new Insets(8));
        top.getChildren().addAll(bar1, bar2, bar3, sessionLinkLabel);

        ScrollPane scroll = new ScrollPane(mapCanvas);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.setPannable(true);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(scroll);

        stage.getScene().setRoot(root);
        mapCanvas.render();

        dmUiRefreshHandler = this::refreshDmSelectors;
        ClientState.getInstance().addChangeListener(dmUiRefreshHandler);
        refreshDmSelectors();
    }

    // ------------------------------------------------------------------ refresh

    private void refreshDmSelectors() {
        String keepTokenId = selectedId(tokenActionsCombo, TokenDto::getId);
        String keepPlayerId = selectedId(playerAssignCombo, PlayerDto::getId);
        String keepObjId = selectedId(objectRemoveCombo, MapObjectDto::getId);

        tokenActionsCombo.getItems().setAll(ClientState.getInstance().getTokens().values());
        playerAssignCombo.getItems().setAll(
                ClientState.getInstance().getPlayers().values().stream()
                        .filter(p -> "PLAYER".equals(p.getRole()))
                        .toList()
        );
        objectRemoveCombo.getItems().setAll(ClientState.getInstance().getObjects().values());

        selectById(tokenActionsCombo, keepTokenId, TokenDto::getId);
        selectById(playerAssignCombo, keepPlayerId, PlayerDto::getId);
        selectById(objectRemoveCombo, keepObjId, MapObjectDto::getId);

        var g = ClientState.getInstance().getGrid();
        int maxC = Math.max(0, g.getCols() - 1);
        int maxR = Math.max(0, g.getRows() - 1);

        int pendingCol = Math.min(ClientState.getInstance().getPendingPlaceCol(), maxC);
        int pendingRow = Math.min(ClientState.getInstance().getPendingPlaceRow(), maxR);

        objectColSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxC, pendingCol));
        objectRowSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxR, pendingRow));
    }

    // ------------------------------------------------------------------ actions

    private void assignSelectedTokenToPlayer() {
        TokenDto t = tokenActionsCombo.getSelectionModel().getSelectedItem();
        PlayerDto p = playerAssignCombo.getSelectionModel().getSelectedItem();
        if (t == null || p == null) return;
        TokenAssignRequest req = new TokenAssignRequest();
        req.setTokenId(t.getId());
        req.setOwnerId(p.getId());
        ServerConnection.getInstance().send("/token.assign", req);
    }

    private void unassignSelectedToken() {
        TokenDto t = tokenActionsCombo.getSelectionModel().getSelectedItem();
        if (t == null) return;
        TokenAssignRequest req = new TokenAssignRequest();
        req.setTokenId(t.getId());
        req.setOwnerId(null);
        ServerConnection.getInstance().send("/token.assign", req);
    }

    private void removeSelectedToken() {
        TokenDto t = tokenActionsCombo.getSelectionModel().getSelectedItem();
        if (t == null) return;
        TokenRemoveEvent ev = new TokenRemoveEvent();
        ev.setTokenId(t.getId());
        ServerConnection.getInstance().send("/token.remove", ev);
    }

    private void placeWall1x1() {
        MapObjectCreateRequest req = new MapObjectCreateRequest();
        req.setType("wall");
        req.setCol(objectColSpinner.getValue());
        req.setRow(objectRowSpinner.getValue());
        req.setWidth(1);
        req.setHeight(1);
        ServerConnection.getInstance().send("/map.object.create", req);
    }

    private void removeSelectedObject() {
        MapObjectDto o = objectRemoveCombo.getSelectionModel().getSelectedItem();
        if (o == null) return;
        MapObjectRemoveEvent ev = new MapObjectRemoveEvent();
        ev.setObjectId(o.getId());
        ServerConnection.getInstance().send("/map.object.remove", ev);
    }

    private void addNpcToken(String name) {
        int col = ClientState.getInstance().getPendingPlaceCol();
        int row = ClientState.getInstance().getPendingPlaceRow();

        PlayerDto selectedPlayer = playerAssignCombo.getSelectionModel().getSelectedItem();
        String ownerId = (selectedPlayer != null) ? selectedPlayer.getId() : null;

        ServerConnection.getInstance().createToken(name, col, row, 100, ownerId);
    }

    private void applyGridResize(int cols, int rows, int cellSize) {
        GridConfig newGrid = new GridConfig();
        newGrid.setCols(cols);
        newGrid.setRows(rows);
        newGrid.setCellSize(cellSize);
        newGrid.setOffsetX(0);
        newGrid.setOffsetY(0);
        ServerConnection.getInstance().send("/map.grid.update", newGrid);
        System.out.println("DM → Server: обновление сетки " + cols + "×" + rows + " cell=" + cellSize);
    }

    // ------------------------------------------------------------------ utils

    private static String shortId(String id) {
        if (id == null) return "";
        return id.length() <= 8 ? id : id.substring(0, 8) + "…";
    }

    private static <T> String selectedId(ComboBox<T> box,
                                         java.util.function.Function<T, String> idFn) {
        T v = box.getSelectionModel().getSelectedItem();
        return v == null ? null : idFn.apply(v);
    }

    private static <T> void selectById(ComboBox<T> box, String id,
                                       java.util.function.Function<T, String> idFn) {
        if (id == null) return;
        box.getItems().stream()
                .filter(t -> id.equals(idFn.apply(t)))
                .findFirst()
                .ifPresent(t -> box.getSelectionModel().select(t));
    }

    private static <T> ComboBox<T> makeCombo(int prefWidth,
                                             java.util.function.Function<T, String> toString) {
        ComboBox<T> combo = new ComboBox<>();
        combo.setPrefWidth(prefWidth);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(T o) {
                return toString.apply(o);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        });
        return combo;
    }

    private static Spinner<Integer> makeSpinner(int min, int max, int initial) {
        Spinner<Integer> s = new Spinner<>(min, max, initial);
        s.setEditable(true);
        s.setPrefWidth(70);
        return s;
    }
}