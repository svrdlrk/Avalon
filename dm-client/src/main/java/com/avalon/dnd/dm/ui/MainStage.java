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

    public MainStage(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        stage.setTitle("Avalon DnD — DM");

        VBox connectForm = buildConnectForm();

        Scene scene = new Scene(connectForm, 1024, 768);
        stage.setScene(scene);
        stage.show();
    }

    private VBox buildConnectForm() {
        VBox form = new VBox(10);
        form.setPadding(new Insets(20));

        Label title = new Label("Avalon DnD — подключение");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        TextField serverField = new TextField("http://localhost:8080");
        serverField.setPromptText("Адрес сервера");

        TextField sessionField = new TextField();
        sessionField.setPromptText("ID сессии");

        TextField nameField = new TextField("DM");
        nameField.setPromptText("Имя");

        TextField playerClientUrlField = new TextField("http://localhost:5173");
        playerClientUrlField.setPromptText("URL player-client (Vite)");

        Label localHint = new Label(
                "Игроки: запусти player-client (npm run dev), открой URL выше " +
                "и вставь ID сессии.");
        localHint.setWrapText(true);
        localHint.setStyle("-fx-text-fill: #555;");

        Button createBtn = new Button("Создать сессию");
        Button connectBtn = new Button("Подключиться");
        Label statusLabel = new Label("");

        createBtn.setOnAction(e -> {
            createSession(serverField.getText(), sessionField);
        });

        connectBtn.setOnAction(e -> {
            statusLabel.setText("Подключение...");
            ServerConnection.getInstance().connect(
                    serverField.getText(),
                    sessionField.getText(),
                    nameField.getText(),
                    true,
                    v -> javafx.application.Platform.runLater(
                            () -> switchToBattleMap(
                                    playerClientUrlField.getText().trim(),
                                    sessionField.getText())
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
                createBtn,
                connectBtn,
                statusLabel
        );

        return form;
    }

    private void createSession(String serverUrl, TextField sessionField) {
        try {
            var client = new okhttp3.OkHttpClient();
            var request = new okhttp3.Request.Builder()
                    .url(serverUrl + "/api/session/create")
                    .post(okhttp3.RequestBody.create(new byte[0]))
                    .build();

            try (var response = client.newCall(request).execute()) {
                var body = response.body().string();
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var json = mapper.readTree(body);
                String sessionId = json.get("id").asText();
                javafx.application.Platform.runLater(
                        () -> sessionField.setText(sessionId)
                );
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void switchToBattleMap(String playerClientBase, String sessionId) {
        mapCanvas = new BattleMapCanvas();

        TextField tokenNameField = new TextField("Гоблин");
        tokenNameField.setPromptText("Имя токена (NPC)");
        tokenNameField.setPrefWidth(120);

        Button addTokenBtn = new Button("Добавить NPC");
        addTokenBtn.setOnAction(e -> addNpcToken(tokenNameField.getText()));

        tokenActionsCombo = new ComboBox<>();
        tokenActionsCombo.setPromptText("Токен");
        tokenActionsCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TokenDto t) {
                if (t == null) return "";
                String own = t.getOwnerId() == null ? "NPC" : "игрок…" + shortId(t.getOwnerId());
                return t.getName() + " (" + own + ")";
            }

            @Override
            public TokenDto fromString(String s) {
                return null;
            }
        });
        tokenActionsCombo.setPrefWidth(200);

        playerAssignCombo = new ComboBox<>();
        playerAssignCombo.setPromptText("Игрок");
        playerAssignCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(PlayerDto p) {
                return p == null ? "" : p.getName() + " (" + shortId(p.getId()) + ")";
            }

            @Override
            public PlayerDto fromString(String s) {
                return null;
            }
        });
        playerAssignCombo.setPrefWidth(180);

        Button assignBtn = new Button("Назначить игроку");
        assignBtn.setOnAction(e -> assignSelectedTokenToPlayer());

        Button unassignBtn = new Button("Сделать NPC");
        unassignBtn.setOnAction(e -> unassignSelectedToken());

        Button removeTokenBtn = new Button("Удалить токен");
        removeTokenBtn.setOnAction(e -> removeSelectedToken());

        var g0 = ClientState.getInstance().getGrid();
        objectColSpinner = new Spinner<>(0, Math.max(0, g0.getCols() - 1), 0);
        objectColSpinner.setEditable(true);
        objectColSpinner.setPrefWidth(70);
        objectRowSpinner = new Spinner<>(0, Math.max(0, g0.getRows() - 1), 0);
        objectRowSpinner.setEditable(true);
        objectRowSpinner.setPrefWidth(70);

        Button placeWallBtn = new Button("Стена 1×1");
        placeWallBtn.setTooltip(new Tooltip("Препятствие в клетке col, row"));
        placeWallBtn.setOnAction(e -> placeWall1x1());

        objectRemoveCombo = new ComboBox<>();
        objectRemoveCombo.setPromptText("Объект");
        objectRemoveCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(MapObjectDto o) {
                if (o == null) return "";
                return o.getType() + " @(" + o.getCol() + "," + o.getRow() + ")";
            }

            @Override
            public MapObjectDto fromString(String s) {
                return null;
            }
        });
        objectRemoveCombo.setPrefWidth(200);

        Button removeObjectBtn = new Button("Удалить объект");
        removeObjectBtn.setOnAction(e -> removeSelectedObject());

        String base = playerClientBase.isEmpty() ? "http://localhost:5173" : playerClientBase;
        String linkHint = base + "  →  сессия: " + sessionId;
        Label sessionLinkLabel = new Label(linkHint);
        sessionLinkLabel.setWrapText(true);
        sessionLinkLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        Button copyIdBtn = new Button("Копировать ID сессии");
        copyIdBtn.setOnAction(e -> {
            var cb = javafx.scene.input.Clipboard.getSystemClipboard();
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(sessionId);
            cb.setContent(content);
        });

        ToolBar bar1 = new ToolBar(
                new Label("Токены:"),
                tokenNameField,
                addTokenBtn,
                new Separator(),
                tokenActionsCombo,
                playerAssignCombo,
                assignBtn,
                unassignBtn,
                removeTokenBtn,
                new Separator(),
                copyIdBtn
        );

        ToolBar bar2 = new ToolBar(
                new Label("Карта:"),
                new Label("col"), objectColSpinner,
                new Label("row"), objectRowSpinner,
                placeWallBtn,
                new Separator(),
                objectRemoveCombo,
                removeObjectBtn
        );

        VBox top = new VBox(6);
        top.setPadding(new Insets(8));
        top.getChildren().addAll(bar1, bar2, sessionLinkLabel);

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

    private static String shortId(String id) {
        if (id == null) return "";
        if (id.length() <= 8) return id;
        return id.substring(0, 8) + "…";
    }

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
        objectColSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxC,
                Math.min(objectColSpinner.getValue(), maxC)));
        objectRowSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxR,
                Math.min(objectRowSpinner.getValue(), maxR)));
    }

    private static <T> String selectedId(ComboBox<T> box, java.util.function.Function<T, String> idFn) {
        T v = box.getSelectionModel().getSelectedItem();
        return v == null ? null : idFn.apply(v);
    }

    private static <T> void selectById(ComboBox<T> box, String id, java.util.function.Function<T, String> idFn) {
        if (id == null) return;
        box.getItems().stream()
                .filter(t -> id.equals(idFn.apply(t)))
                .findFirst()
                .ifPresent(t -> box.getSelectionModel().select(t));
    }

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

    private void addNpcToken(String rawName) {
        String name = rawName == null || rawName.isBlank() ? "NPC" : rawName.trim();
        int[] cell = findFirstEmptyCell();
        TokenCreateRequest req = new TokenCreateRequest();
        req.setName(name);
        req.setCol(cell[0]);
        req.setRow(cell[1]);
        req.setOwnerId(null);

        ServerConnection.getInstance().send("/token.create", req);
    }

    private int[] findFirstEmptyCell() {
        var g = ClientState.getInstance().getGrid();
        Set<String> occupied = new HashSet<>();
        for (TokenDto t : ClientState.getInstance().getTokens().values()) {
            occupied.add(t.getCol() + "," + t.getRow());
        }
        for (int row = 0; row < g.getRows(); row++) {
            for (int col = 0; col < g.getCols(); col++) {
                if (!occupied.contains(col + "," + row)) {
                    return new int[]{col, row};
                }
            }
        }
        return new int[]{0, 0};
    }
}
