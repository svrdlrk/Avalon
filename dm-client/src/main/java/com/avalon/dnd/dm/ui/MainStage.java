package com.avalon.dnd.dm.ui;

import com.avalon.dnd.dm.canvas.BattleMapCanvas;
import com.avalon.dnd.dm.net.ServerConnection;
import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.*;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.*;

public class MainStage {

    private final Stage stage;
    private BattleMapCanvas mapCanvas;

    // Selectors
    private ComboBox<TokenDto> tokenActionsCombo;
    private ComboBox<PlayerDto> playerAssignCombo;
    private ComboBox<MapObjectDto> objectRemoveCombo;
    private Spinner<Integer> objectColSpinner;
    private Spinner<Integer> objectRowSpinner;
    private Runnable dmUiRefreshHandler;

    private String currentServerUrl = "http://localhost:8080";

    private final List<JsonNode> tokenCatalog = new ArrayList<>();
    private final List<JsonNode> objectCatalog = new ArrayList<>();

    // Initiative
    private final List<InitiativeEntry> initiativeQueue = new ArrayList<>();
    private int currentInitiativeIndex = 0;

    public MainStage(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        stage.setTitle("Avalon DnD — DM");
        stage.setScene(new Scene(buildConnectForm(), 1200, 800));
        stage.show();
    }

    // ================================================================ connect

    private VBox buildConnectForm() {
        VBox form = new VBox(10);
        form.setPadding(new Insets(20));

        Label title = new Label("Avalon DnD — DM Панель");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        TextField serverField = new TextField("http://localhost:8080");
        TextField sessionField = new TextField();
        sessionField.setPromptText("ID сессии (создайте или вставьте)");
        TextField nameField = new TextField("DM");
        TextField playerClientUrlField = new TextField("http://localhost:5173");

        Button createBtn = new Button("✨ Создать сессию");
        Button connectBtn = new Button("🔗 Подключиться");
        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #888;");

        createBtn.setOnAction(e -> {
            String url = serverField.getText().trim();
            createBtn.setDisable(true);
            statusLabel.setText("Создание сессии...");
            ServerConnection.getInstance().createSession(url, sessionId -> {
                createBtn.setDisable(false);
                if (sessionId != null) {
                    sessionField.setText(sessionId);
                    statusLabel.setText("✅ Сессия: " + sessionId);
                } else {
                    statusLabel.setText("❌ Не удалось создать сессию");
                }
            });
        });

        connectBtn.setOnAction(e -> {
            String url = serverField.getText().trim();
            String sid = sessionField.getText().trim();
            String name = nameField.getText().trim();
            if (url.isEmpty() || sid.isEmpty() || name.isEmpty()) {
                statusLabel.setText("Заполните все поля");
                return;
            }
            statusLabel.setText("Подключение...");
            currentServerUrl = url;
            loadAssetCatalog(url, () ->
                    ServerConnection.getInstance().connect(url, sid, name, true,
                            v -> Platform.runLater(() ->
                                    switchToBattleMap(playerClientUrlField.getText().trim(), sid)))
            );
        });

        form.getChildren().addAll(
                title,
                new Label("Сервер:"), serverField,
                new Label("ID сессии:"), sessionField,
                new Label("Имя DM:"), nameField,
                new Label("Player-client URL:"), playerClientUrlField,
                createBtn, connectBtn, statusLabel
        );
        return form;
    }

    // ================================================================ asset catalog

    private void loadAssetCatalog(String serverUrl, Runnable onDone) {
        new Thread(() -> {
            try {
                var client = new okhttp3.OkHttpClient();
                var req = new okhttp3.Request.Builder()
                        .url(serverUrl + "/api/assets/catalog")
                        .build();
                try (var resp = client.newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        JsonNode root = mapper.readTree(resp.body().string());
                        tokenCatalog.clear();
                        objectCatalog.clear();
                        if (root.has("tokens")) {
                            root.get("tokens").forEach(tokenCatalog::add);
                        }
                        if (root.has("objects")) {
                            root.get("objects").forEach(objectCatalog::add);
                        }
                    }
                }
            } catch (Exception ex) {
                System.err.println("Asset catalog load failed: " + ex.getMessage());
            }
            Platform.runLater(onDone::run);
        }).start();
    }

    // ================================================================ battle map

    private void switchToBattleMap(String playerClientBase, String sessionId) {
        mapCanvas = new BattleMapCanvas();

        String bgUrl = ClientState.getInstance().getBackgroundUrl();
        if (bgUrl != null && !bgUrl.isEmpty()) {
            mapCanvas.setBackground(currentServerUrl + bgUrl);
        }

        // Listen for background changes
        ClientState.getInstance().addChangeListener(() -> {
            String url = ClientState.getInstance().getBackgroundUrl();
            if (url != null && mapCanvas != null) {
                mapCanvas.setBackground(currentServerUrl + url);
            }
        });

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        tabs.getTabs().addAll(
                buildTokenTab(),
                buildObjectTab(sessionId),
                buildGridTab(sessionId),
                buildHpTab(),
                buildInitiativeTab()
        );

        String linkHint = (playerClientBase.isEmpty() ? "http://localhost:5173" : playerClientBase)
                + "  →  сессия: " + sessionId;
        Label sessionLinkLabel = new Label(linkHint);
        sessionLinkLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        Button copyIdBtn = new Button("📋 ID");
        copyIdBtn.setOnAction(e -> {
            var cb = javafx.scene.input.Clipboard.getSystemClipboard();
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(sessionId);
            cb.setContent(content);
        });

        HBox statusBar = new HBox(8, sessionLinkLabel, copyIdBtn);
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        statusBar.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(tabs, statusBar);

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

    // ================================================================ Tab: Токены

    private Tab buildTokenTab() {
        Tab tab = new Tab("🗡 Токены");

        ComboBox<JsonNode> creatureCatalogCombo = new ComboBox<>();
        creatureCatalogCombo.setPrefWidth(180);
        creatureCatalogCombo.setConverter(new StringConverter<>() {
            @Override public String toString(JsonNode n) {
                if (n == null) return "";
                return n.path("name").asText("?") + " [" + n.path("size").asText() + "]";
            }
            @Override public JsonNode fromString(String s) { return null; }
        });
        creatureCatalogCombo.getItems().addAll(tokenCatalog);

        TextField tokenNameField = new TextField("Гоблин");
        tokenNameField.setPrefWidth(110);

        Spinner<Integer> hpSpinner = makeSpinner(1, 999, 20);
        Label gridSizeLabel = new Label("1×1");

        creatureCatalogCombo.setOnAction(e -> {
            JsonNode sel = creatureCatalogCombo.getSelectionModel().getSelectedItem();
            if (sel != null) {
                tokenNameField.setText(sel.path("name").asText("Существо"));
                int gs = sel.path("gridSize").asInt(1);
                gridSizeLabel.setText(gs + "×" + gs);
                int defaultHp = switch (sel.path("size").asText("medium")) {
                    case "tiny" -> 5;
                    case "small" -> 10;
                    case "large" -> 50;
                    case "huge" -> 150;
                    default -> 20;
                };
                hpSpinner.getValueFactory().setValue(defaultHp);
            }
        });

        Button addTokenBtn = new Button("➕ Добавить");
        addTokenBtn.setOnAction(e -> addToken(tokenNameField.getText(),
                creatureCatalogCombo.getSelectionModel().getSelectedItem(), hpSpinner.getValue()));

        tokenActionsCombo = makeCombo(200, t -> {
            if (t == null) return "";
            String own = t.getOwnerId() == null ? "NPC" : "игрок…" + shortId(t.getOwnerId());
            String size = t.getGridSize() > 1 ? " [" + t.getGridSize() + "×" + t.getGridSize() + "]" : "";
            return t.getName() + size + " (" + own + ")";
        });

        playerAssignCombo = makeCombo(160, p -> p == null ? "" :
                p.getName() + " (" + shortId(p.getId()) + ")");

        Button assignBtn = new Button("👤 → Игрок");
        assignBtn.setOnAction(e -> assignSelectedTokenToPlayer());
        Button unassignBtn = new Button("→ NPC");
        unassignBtn.setOnAction(e -> unassignSelectedToken());
        Button removeTokenBtn = new Button("🗑 Удалить");
        removeTokenBtn.setOnAction(e -> removeSelectedToken());

        HBox row1 = new HBox(6,
                new Label("Каталог:"), creatureCatalogCombo,
                new Label("Имя:"), tokenNameField,
                new Label("HP:"), hpSpinner,
                new Label("Размер:"), gridSizeLabel,
                addTokenBtn
        );
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPadding(new Insets(6));

        HBox row2 = new HBox(6,
                new Label("Выбрать:"), tokenActionsCombo,
                new Label("Игрок:"), playerAssignCombo,
                assignBtn, unassignBtn, removeTokenBtn
        );
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.setPadding(new Insets(6));

        tab.setContent(new VBox(row1, new Separator(), row2));
        return tab;
    }

    // ================================================================ Tab: Объекты

    private Tab buildObjectTab(String sessionId) {
        Tab tab = new Tab("🧱 Объекты");

        ComboBox<JsonNode> objectCatalogCombo = new ComboBox<>();
        objectCatalogCombo.setPrefWidth(200);
        objectCatalogCombo.setConverter(new StringConverter<>() {
            @Override public String toString(JsonNode n) {
                if (n == null) return "";
                return n.path("name").asText("?") + " [" + n.path("category").asText() + "]";
            }
            @Override public JsonNode fromString(String s) { return null; }
        });
        objectCatalogCombo.getItems().addAll(objectCatalog);

        var g0 = ClientState.getInstance().getGrid();
        objectColSpinner = makeSpinner(0, Math.max(0, g0.getCols() - 1), 0);
        objectRowSpinner = makeSpinner(0, Math.max(0, g0.getRows() - 1), 0);
        Spinner<Integer> objWSpinner = makeSpinner(1, 10, 1);
        Spinner<Integer> objHSpinner = makeSpinner(1, 10, 1);
        Label objPreviewLabel = new Label("1×1, без текстуры");

        objectCatalogCombo.setOnAction(e -> {
            JsonNode sel = objectCatalogCombo.getSelectionModel().getSelectedItem();
            if (sel != null) {
                int w = sel.path("defaultWidth").asInt(1);
                int h = sel.path("defaultHeight").asInt(1);
                objWSpinner.getValueFactory().setValue(w);
                objHSpinner.getValueFactory().setValue(h);
                objPreviewLabel.setText(w + "×" + h + ", " + sel.path("name").asText());
            }
        });

        Button placeBtn = new Button("📌 Разместить");
        placeBtn.setOnAction(e -> placeObject(
                objectCatalogCombo.getSelectionModel().getSelectedItem(),
                objectColSpinner.getValue(),
                objectRowSpinner.getValue(),
                objWSpinner.getValue(),
                objHSpinner.getValue()
        ));

        objectRemoveCombo = makeCombo(200, o -> o == null ? "" :
                o.getType() + " @(" + o.getCol() + "," + o.getRow() + ")");
        Button removeObjectBtn = new Button("🗑 Удалить");
        removeObjectBtn.setOnAction(e -> removeSelectedObject());

        HBox row1 = new HBox(6,
                new Label("Тип:"), objectCatalogCombo,
                new Label("col:"), objectColSpinner,
                new Label("row:"), objectRowSpinner,
                new Label("W:"), objWSpinner,
                new Label("H:"), objHSpinner,
                objPreviewLabel, placeBtn
        );
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPadding(new Insets(6));

        HBox row2 = new HBox(6,
                new Label("Удалить:"), objectRemoveCombo, removeObjectBtn
        );
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.setPadding(new Insets(6));

        tab.setContent(new VBox(row1, new Separator(), row2));
        return tab;
    }

    // ================================================================ Tab: Сетка/карта

    private Tab buildGridTab(String sessionId) {
        Tab tab = new Tab("🗺 Карта");

        var g0 = ClientState.getInstance().getGrid();
        Spinner<Integer> colsSpinner = makeSpinner(4, 60, g0.getCols());
        Spinner<Integer> rowsSpinner = makeSpinner(4, 60, g0.getRows());
        Spinner<Integer> cellSpinner = makeSpinner(24, 128, g0.getCellSize());

        Button applyGridBtn = new Button("✅ Применить сетку");
        applyGridBtn.setOnAction(e -> applyGridResize(
                colsSpinner.getValue(), rowsSpinner.getValue(), cellSpinner.getValue()));

        Button uploadMapBtn = new Button("🖼 Загрузить фон");
        Label uploadStatusLabel = new Label("");
        uploadStatusLabel.setStyle("-fx-text-fill: #888;");

        uploadMapBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Выберите изображение карты");
            fc.getExtensionFilters().add(
                    new javafx.stage.FileChooser.ExtensionFilter("Images",
                            "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif"));
            java.io.File file = fc.showOpenDialog(stage);
            if (file != null) {
                uploadStatusLabel.setText("Загрузка...");
                uploadMapBtn.setDisable(true);
                ServerConnection.getInstance().uploadMap(
                        currentServerUrl,
                        ClientState.getInstance().getSessionId(),
                        file,
                        url -> {
                            uploadMapBtn.setDisable(false);
                            if (url != null && mapCanvas != null) {
                                // url от сервера — относительный путь (/uploads/...)
                                String fullUrl = currentServerUrl + url.trim();
                                mapCanvas.setBackground(fullUrl);
                                uploadStatusLabel.setText("✅ Загружено");
                                System.out.println("Background set to: " + fullUrl);
                            } else {
                                uploadStatusLabel.setText("❌ Ошибка загрузки");
                            }
                        }
                );
            }
        });

        HBox row = new HBox(8,
                new Label("cols:"), colsSpinner,
                new Label("rows:"), rowsSpinner,
                new Label("cell px:"), cellSpinner,
                applyGridBtn,
                new Separator(),
                uploadMapBtn,
                uploadStatusLabel
        );
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));

        tab.setContent(row);
        return tab;
    }

    // ================================================================ Tab: HP-менеджер

    private Tab buildHpTab() {
        Tab tab = new Tab("❤ HP");

        ComboBox<TokenDto> hpTokenCombo = makeCombo(220, t -> {
            if (t == null) return "";
            return t.getName() + " [" + t.getHp() + "/" + t.getMaxHp() + "]";
        });

        Spinner<Integer> newHpSpinner = makeSpinner(0, 9999, 20);
        Spinner<Integer> newMaxHpSpinner = makeSpinner(1, 9999, 20);
        Label currentHpLabel = new Label("HP: —/—");

        hpTokenCombo.setOnAction(e -> {
            TokenDto t = hpTokenCombo.getSelectionModel().getSelectedItem();
            if (t != null) {
                newHpSpinner.getValueFactory().setValue(t.getHp());
                newMaxHpSpinner.getValueFactory().setValue(t.getMaxHp());
                currentHpLabel.setText("HP: " + t.getHp() + " / " + t.getMaxHp());
            }
        });

        Button dmgBtn = new Button("⚔ -");
        Spinner<Integer> deltaSpinner = makeSpinner(1, 999, 5);
        Button healBtn = new Button("💚 +");
        Button setBtn = new Button("💾 Задать");
        Button killBtn = new Button("💀 0 HP");

        dmgBtn.setStyle("-fx-base: #c0392b;");
        healBtn.setStyle("-fx-base: #27ae60;");
        killBtn.setStyle("-fx-base: #7f8c8d;");

        dmgBtn.setOnAction(e -> {
            TokenDto t = hpTokenCombo.getSelectionModel().getSelectedItem();
            if (t == null) return;
            int newHp = Math.max(0, t.getHp() - deltaSpinner.getValue());
            ServerConnection.getInstance().updateTokenHp(t.getId(), newHp, t.getMaxHp());
        });

        healBtn.setOnAction(e -> {
            TokenDto t = hpTokenCombo.getSelectionModel().getSelectedItem();
            if (t == null) return;
            int newHp = Math.min(t.getMaxHp(), t.getHp() + deltaSpinner.getValue());
            ServerConnection.getInstance().updateTokenHp(t.getId(), newHp, t.getMaxHp());
        });

        setBtn.setOnAction(e -> {
            TokenDto t = hpTokenCombo.getSelectionModel().getSelectedItem();
            if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(),
                    newHpSpinner.getValue(), newMaxHpSpinner.getValue());
        });

        killBtn.setOnAction(e -> {
            TokenDto t = hpTokenCombo.getSelectionModel().getSelectedItem();
            if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(), 0, t.getMaxHp());
        });

        ClientState.getInstance().addChangeListener(() -> {
            String keepId = selectedId(hpTokenCombo, TokenDto::getId);
            hpTokenCombo.getItems().setAll(ClientState.getInstance().getTokens().values());
            selectById(hpTokenCombo, keepId, TokenDto::getId);

            TokenDto sel = hpTokenCombo.getSelectionModel().getSelectedItem();
            if (sel != null) {
                currentHpLabel.setText("HP: " + sel.getHp() + " / " + sel.getMaxHp());
            }
        });

        HBox row1 = new HBox(8, new Label("Токен:"), hpTokenCombo, currentHpLabel);
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPadding(new Insets(6));

        HBox row2 = new HBox(8,
                new Label("Урон/Лечение:"),
                dmgBtn, deltaSpinner, healBtn,
                new Separator(),
                new Label("HP:"), newHpSpinner,
                new Label("/ maxHP:"), newMaxHpSpinner,
                setBtn, killBtn
        );
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.setPadding(new Insets(6));

        tab.setContent(new VBox(row1, new Separator(), row2));
        return tab;
    }

    // ================================================================ Tab: Инициатива

    private Tab buildInitiativeTab() {
        Tab tab = new Tab("🎲 Инициатива");

        ListView<String> initiativeList = new ListView<>();
        initiativeList.setPrefHeight(120);

        ComboBox<TokenDto> addToInitiativeCombo = makeCombo(200, t ->
                t == null ? "" : t.getName());

        Spinner<Integer> initiativeSpinner = makeSpinner(1, 30, 10);

        Button addBtn = new Button("➕ Добавить");
        Button nextBtn = new Button("▶ Следующий ход");
        Button clearBtn = new Button("🔄 Сброс");
        Label currentTurnLabel = new Label("Текущий ход: —");
        currentTurnLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        addBtn.setOnAction(e -> {
            TokenDto t = addToInitiativeCombo.getSelectionModel().getSelectedItem();
            if (t == null) return;
            int ini = initiativeSpinner.getValue();
            initiativeQueue.add(new InitiativeEntry(t.getId(), t.getName(), ini));
            initiativeQueue.sort(Comparator.comparingInt(InitiativeEntry::initiative).reversed());
            refreshInitiativeList(initiativeList, initiativeQueue, 0);
        });

        nextBtn.setOnAction(e -> {
            if (initiativeQueue.isEmpty()) return;
            currentInitiativeIndex = (currentInitiativeIndex + 1) % initiativeQueue.size();
            refreshInitiativeList(initiativeList, initiativeQueue, currentInitiativeIndex);
            currentTurnLabel.setText("Текущий ход: " + initiativeQueue.get(currentInitiativeIndex).name());
        });

        clearBtn.setOnAction(e -> {
            initiativeQueue.clear();
            currentInitiativeIndex = 0;
            initiativeList.getItems().clear();
            currentTurnLabel.setText("Текущий ход: —");
        });

        Button removeFromIniBtn = new Button("🗑");
        removeFromIniBtn.setOnAction(e -> {
            int sel = initiativeList.getSelectionModel().getSelectedIndex();
            if (sel >= 0 && sel < initiativeQueue.size()) {
                initiativeQueue.remove(sel);
                if (currentInitiativeIndex >= initiativeQueue.size()) currentInitiativeIndex = 0;
                refreshInitiativeList(initiativeList, initiativeQueue, currentInitiativeIndex);
            }
        });

        ClientState.getInstance().addChangeListener(() -> {
            String keepId = selectedId(addToInitiativeCombo, TokenDto::getId);
            addToInitiativeCombo.getItems().setAll(ClientState.getInstance().getTokens().values());
            selectById(addToInitiativeCombo, keepId, TokenDto::getId);
        });

        HBox row1 = new HBox(8,
                new Label("Токен:"), addToInitiativeCombo,
                new Label("Инициатива:"), initiativeSpinner,
                addBtn, removeFromIniBtn
        );
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPadding(new Insets(6));

        HBox row2 = new HBox(8, nextBtn, clearBtn, currentTurnLabel);
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.setPadding(new Insets(6));

        VBox content = new VBox(row1, row2, initiativeList);
        content.setPadding(new Insets(4));
        tab.setContent(content);
        return tab;
    }

    private record InitiativeEntry(String id, String name, int initiative) {}

    private void refreshInitiativeList(ListView<String> list,
                                       List<InitiativeEntry> queue, int current) {
        list.getItems().clear();
        for (int i = 0; i < queue.size(); i++) {
            var e = queue.get(i);
            String prefix = (i == current) ? "► " : "  ";
            list.getItems().add(prefix + e.name() + " [" + e.initiative() + "]");
        }
        list.getSelectionModel().select(current);
    }

    // ================================================================ refresh selectors

    private void refreshDmSelectors() {
        String keepTokenId = selectedId(tokenActionsCombo, TokenDto::getId);
        String keepPlayerId = selectedId(playerAssignCombo, PlayerDto::getId);
        String keepObjId = selectedId(objectRemoveCombo, MapObjectDto::getId);

        tokenActionsCombo.getItems().setAll(ClientState.getInstance().getTokens().values());

        // FIX: фильтруем игроков правильно — роль хранится как строка "PLAYER"
        playerAssignCombo.getItems().setAll(
                ClientState.getInstance().getPlayers().values().stream()
                        .filter(p -> "PLAYER".equalsIgnoreCase(p.getRole()))
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

    // ================================================================ actions

    private void addToken(String name, JsonNode catalogEntry, int hp) {
        int col = ClientState.getInstance().getPendingPlaceCol();
        int row = ClientState.getInstance().getPendingPlaceRow();
        int gridSize = 1;
        String imageUrl = null;

        if (catalogEntry != null) {
            gridSize = catalogEntry.path("gridSize").asInt(1);
            String imgPath = catalogEntry.path("imagePath").asText(null);
            if (imgPath != null && !imgPath.equals("null")) {
                imageUrl = "/" + imgPath;
            }
        }

        PlayerDto selectedPlayer = playerAssignCombo.getSelectionModel().getSelectedItem();
        String ownerId = (selectedPlayer != null) ? selectedPlayer.getId() : null;

        ServerConnection.getInstance().createToken(name, col, row, hp, hp, gridSize, imageUrl, ownerId);
    }

    private void placeObject(JsonNode catalogEntry, int col, int row, int w, int h) {
        String type = "wall";
        int gridSize = 1;
        String imageUrl = null;

        if (catalogEntry != null) {
            type = catalogEntry.path("id").asText("wall");
            String imgPath = catalogEntry.path("imagePath").asText(null);
            if (imgPath != null && !imgPath.equals("null")) {
                imageUrl = "/" + imgPath;
            }
        }

        MapObjectCreateRequest req = new MapObjectCreateRequest(type, col, row, w, h, gridSize, imageUrl);
        ServerConnection.getInstance().send("/map.object.create", req);
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

    private void removeSelectedObject() {
        MapObjectDto o = objectRemoveCombo.getSelectionModel().getSelectedItem();
        if (o == null) return;
        MapObjectRemoveEvent ev = new MapObjectRemoveEvent();
        ev.setObjectId(o.getId());
        ServerConnection.getInstance().send("/map.object.remove", ev);
    }

    private void applyGridResize(int cols, int rows, int cellSize) {
        GridConfig newGrid = new GridConfig();
        newGrid.setCols(cols);
        newGrid.setRows(rows);
        newGrid.setCellSize(cellSize);
        newGrid.setOffsetX(0);
        newGrid.setOffsetY(0);
        ServerConnection.getInstance().send("/map.grid.update", newGrid);
    }

    // ================================================================ utils

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
            @Override public String toString(T o) { return toString.apply(o); }
            @Override public T fromString(String s) { return null; }
        });
        return combo;
    }

    private static Spinner<Integer> makeSpinner(int min, int max, int initial) {
        Spinner<Integer> s = new Spinner<>(min, max, initial);
        s.setEditable(true);
        s.setPrefWidth(72);
        return s;
    }
}