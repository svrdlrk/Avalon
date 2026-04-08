package com.avalon.dnd.dm.ui;

import com.avalon.dnd.dm.canvas.BattleMapCanvas;
import com.avalon.dnd.dm.net.ServerConnection;
import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.*;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.*;

/**
 * Главный экран DM-клиента.
 *
 * Исправления:
 * 1. Список игроков: фильтр "PLAYER".equalsIgnoreCase(p.getRole())
 * 2. Загрузка карты: полный URL = currentServerUrl + url.trim()
 * 3. Drag токена: смещение считается от верхнего-левого угла, а не от центра
 * 4. Сохранение / загрузка сессии через БД
 */
public class MainStage {

    private final Stage stage;
    private BattleMapCanvas mapCanvas;

    private ComboBox<TokenDto>    tokenActionsCombo;
    private ComboBox<PlayerDto>   playerAssignCombo;
    private ComboBox<MapObjectDto> objectRemoveCombo;
    private Spinner<Integer>      objectColSpinner;
    private Spinner<Integer>      objectRowSpinner;

    private String currentServerUrl = "http://localhost:8080";

    private final List<JsonNode>        tokenCatalog    = new ArrayList<>();
    private final List<JsonNode>        objectCatalog   = new ArrayList<>();
    private final List<InitiativeEntry> initiativeQueue = new ArrayList<>();
    private int currentInitiativeIndex = 0;

    public MainStage(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        stage.setTitle("Avalon DnD — DM");
        stage.setScene(new Scene(buildConnectForm(), 1200, 820));
        stage.show();
    }

    // =========================================================== connect form

    private VBox buildConnectForm() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        Label title = new Label("Avalon DnD — DM Панель");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextField serverField        = new TextField("http://localhost:8080");
        TextField playerClientField  = new TextField("http://localhost:5173");

        // ---- Tab: новая / существующая сессия ----
        Tab newTab = new Tab("✨ Новая / существующая сессия");
        newTab.setClosable(false);
        {
            TextField sessionField = new TextField();
            sessionField.setPromptText("ID сессии (оставьте пустым — создайте новую)");
            TextField nameField = new TextField("DM");

            Button createBtn  = new Button("Создать новую сессию");
            Button connectBtn = new Button("🔗 Подключиться");
            Label  statusLbl  = new Label("");

            createBtn.setOnAction(e -> {
                createBtn.setDisable(true);
                statusLbl.setText("Создание...");
                ServerConnection.getInstance().createSession(
                        serverField.getText().trim(), sid -> {
                            createBtn.setDisable(false);
                            if (sid != null) {
                                sessionField.setText(sid);
                                statusLbl.setText("✅ Сессия: " + sid);
                            } else {
                                statusLbl.setText("❌ Не удалось создать сессию");
                            }
                        });
            });

            connectBtn.setOnAction(e -> {
                String url  = serverField.getText().trim();
                String sid  = sessionField.getText().trim();
                String name = nameField.getText().trim();
                if (url.isEmpty() || sid.isEmpty() || name.isEmpty()) {
                    statusLbl.setText("⚠ Заполните все поля");
                    return;
                }
                currentServerUrl = url;
                statusLbl.setText("Подключение...");
                loadAssetCatalog(url, () ->
                        ServerConnection.getInstance().connect(
                                url, sid, name, true,
                                v -> Platform.runLater(() ->
                                        switchToBattleMap(playerClientField.getText().trim(), sid))));
            });

            VBox c = new VBox(8,
                    new Label("ID сессии:"), sessionField,
                    new Label("Имя DM:"),    nameField,
                    new HBox(8, createBtn, connectBtn),
                    statusLbl);
            c.setPadding(new Insets(10));
            newTab.setContent(c);
        }

        // ---- Tab: загрузить сохранённую сессию ----
        Tab loadTab = new Tab("📂 Загрузить сохранённую сессию");
        loadTab.setClosable(false);
        {
            TableView<JsonNode> table = new TableView<>();
            table.setPrefHeight(200);
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

            TableColumn<JsonNode, String> colName = new TableColumn<>("Название");
            colName.setCellValueFactory(c ->
                    new SimpleStringProperty(c.getValue().path("displayName").asText("?")));
            TableColumn<JsonNode, String> colId = new TableColumn<>("ID сессии");
            colId.setCellValueFactory(c ->
                    new SimpleStringProperty(c.getValue().path("sessionId").asText()));
            TableColumn<JsonNode, String> colDate = new TableColumn<>("Дата сохранения");
            colDate.setCellValueFactory(c ->
                    new SimpleStringProperty(
                            c.getValue().path("savedAt").asText("").replace("T", " ")));
            TableColumn<JsonNode, String> colVer = new TableColumn<>("Версия");
            colVer.setPrefWidth(70);
            colVer.setCellValueFactory(c ->
                    new SimpleStringProperty(c.getValue().path("version").asText()));
            //noinspection unchecked
            table.getColumns().addAll(colName, colId, colDate, colVer);

            TextField dmNameField = new TextField("DM");
            Button refreshBtn = new Button("🔄 Обновить");
            Button loadBtn    = new Button("▶ Загрузить и подключиться");
            Button deleteBtn  = new Button("🗑 Удалить запись");
            Label  statusLbl  = new Label("");

            Runnable refreshList = () -> {
                statusLbl.setText("Загрузка...");
                ServerConnection.getInstance().listSavedSessions(
                        serverField.getText().trim(),
                        list -> {
                            table.getItems().setAll(list);
                            statusLbl.setText(list.isEmpty() ? "Нет сохранений" : "");
                        });
            };

            refreshBtn.setOnAction(e -> refreshList.run());

            loadBtn.setOnAction(e -> {
                JsonNode sel = table.getSelectionModel().getSelectedItem();
                if (sel == null) { statusLbl.setText("Выберите сессию"); return; }
                String sid    = sel.path("sessionId").asText();
                String url    = serverField.getText().trim();
                String dmName = dmNameField.getText().trim().isEmpty()
                        ? "DM" : dmNameField.getText().trim();
                statusLbl.setText("Загрузка сессии...");
                ServerConnection.getInstance().loadSession(url, sid, loadedId -> {
                    if (loadedId == null) { statusLbl.setText("❌ Ошибка загрузки"); return; }
                    currentServerUrl = url;
                    loadAssetCatalog(url, () ->
                            ServerConnection.getInstance().connect(
                                    url, loadedId, dmName, true,
                                    v -> Platform.runLater(() ->
                                            switchToBattleMap(
                                                    playerClientField.getText().trim(), loadedId))));
                });
            });

            deleteBtn.setOnAction(e -> {
                JsonNode sel = table.getSelectionModel().getSelectedItem();
                if (sel == null) return;
                String sid = sel.path("sessionId").asText();
                new Thread(() -> {
                    try {
                        var client = new okhttp3.OkHttpClient();
                        var req = new okhttp3.Request.Builder()
                                .url(serverField.getText().trim()
                                        + "/api/session/" + sid + "/saved")
                                .delete().build();
                        client.newCall(req).execute().close();
                    } catch (Exception ex) { ex.printStackTrace(); }
                    Platform.runLater(refreshList::run);
                }).start();
            });

            HBox btnRow = new HBox(8, refreshBtn, loadBtn, deleteBtn,
                    new Label("Имя DM:"), dmNameField);
            btnRow.setAlignment(Pos.CENTER_LEFT);

            VBox c = new VBox(8, table, btnRow, statusLbl);
            c.setPadding(new Insets(10));
            loadTab.setContent(c);
        }

        TabPane tabPane = new TabPane(newTab, loadTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        root.getChildren().addAll(
                title,
                new Label("Адрес сервера:"),    serverField,
                new Label("Player-client URL:"), playerClientField,
                tabPane);
        return root;
    }

    // =========================================================== asset catalog

    private void loadAssetCatalog(String serverUrl, Runnable onDone) {
        new Thread(() -> {
            try {
                var client = new okhttp3.OkHttpClient();
                var req = new okhttp3.Request.Builder()
                        .url(serverUrl + "/api/assets/catalog").build();
                try (var resp = client.newCall(req).execute()) {
                    if (resp.isSuccessful() && resp.body() != null) {
                        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        JsonNode root = mapper.readTree(resp.body().string());
                        tokenCatalog.clear();
                        objectCatalog.clear();
                        if (root.has("tokens"))  root.get("tokens").forEach(tokenCatalog::add);
                        if (root.has("objects")) root.get("objects").forEach(objectCatalog::add);
                    }
                }
            } catch (Exception ex) {
                System.err.println("Asset catalog load failed: " + ex.getMessage());
            }
            Platform.runLater(onDone::run);
        }).start();
    }

    // =========================================================== battle map

    private void switchToBattleMap(String playerClientBase, String sessionId) {
        mapCanvas = new BattleMapCanvas();

        // Установить фон, если уже есть в состоянии
        String bgUrl = ClientState.getInstance().getBackgroundUrl();
        if (bgUrl != null && !bgUrl.isEmpty()) {
            mapCanvas.setBackground(currentServerUrl + bgUrl);
        }
        // Слушаем обновления фона
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
                buildObjectTab(),
                buildGridTab(sessionId),
                buildHpTab(),
                buildInitiativeTab(),
                buildSessionTab(sessionId)
        );

        String linkHint = (playerClientBase.isEmpty() ? "http://localhost:5173" : playerClientBase)
                + "  →  сессия: " + sessionId;
        Label sessionLinkLabel = new Label(linkHint);
        sessionLinkLabel.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");

        Button copyIdBtn = new Button("📋 ID");
        copyIdBtn.setOnAction(e -> {
            var cb      = javafx.scene.input.Clipboard.getSystemClipboard();
            var content = new javafx.scene.input.ClipboardContent();
            content.putString(sessionId);
            cb.setContent(content);
        });

        HBox statusBar = new HBox(8, sessionLinkLabel, copyIdBtn);
        statusBar.setPadding(new Insets(4, 8, 4, 8));
        statusBar.setAlignment(Pos.CENTER_LEFT);

        VBox top      = new VBox(tabs, statusBar);
        ScrollPane scroll = new ScrollPane(mapCanvas);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.setPannable(true);

        BorderPane root = new BorderPane();
        root.setTop(top);
        root.setCenter(scroll);

        stage.getScene().setRoot(root);
        mapCanvas.render();

        ClientState.getInstance().addChangeListener(this::refreshDmSelectors);
        refreshDmSelectors();
    }

    // =========================================================== Tab: 💾 Сессия

    private Tab buildSessionTab(String sessionId) {
        Tab tab = new Tab("💾 Сессия");

        Label idLabel = new Label("ID: " + sessionId);
        idLabel.setStyle("-fx-font-family: monospace;");

        TextField saveNameField = new TextField("Моя сессия");
        Button    saveBtn       = new Button("💾 Сохранить");
        Label     saveStatus    = new Label("");

        saveBtn.setOnAction(e -> {
            String name = saveNameField.getText().trim();
            if (name.isEmpty()) name = "Сессия " + sessionId.substring(0, 8);
            final String finalName = name;
            saveBtn.setDisable(true);
            saveStatus.setText("Сохранение...");
            ServerConnection.getInstance().saveSession(
                    currentServerUrl, sessionId, finalName,
                    ok -> {
                        saveBtn.setDisable(false);
                        saveStatus.setText(ok ? "✅ Сохранено" : "❌ Ошибка сохранения");
                    });
        });

        CheckBox autoSaveCheck = new CheckBox("Автосохранение каждые 5 минут");
        final javafx.animation.Timeline[] tl = {null};
        autoSaveCheck.setOnAction(e -> {
            if (autoSaveCheck.isSelected()) {
                tl[0] = new javafx.animation.Timeline(
                        new javafx.animation.KeyFrame(
                                javafx.util.Duration.minutes(5),
                                ae -> {
                                    String n = saveNameField.getText().trim().isEmpty()
                                            ? "Автосохранение"
                                            : saveNameField.getText().trim();
                                    ServerConnection.getInstance().saveSession(
                                            currentServerUrl, sessionId, n,
                                            ok -> saveStatus.setText(
                                                    ok ? "✅ Автосохранено" : "❌ Ошибка"));
                                }));
                tl[0].setCycleCount(javafx.animation.Animation.INDEFINITE);
                tl[0].play();
                saveStatus.setText("Автосохранение включено");
            } else {
                if (tl[0] != null) tl[0].stop();
                saveStatus.setText("Автосохранение отключено");
            }
        });

        VBox c = new VBox(10,
                idLabel,
                new HBox(8, new Label("Название:"), saveNameField, saveBtn),
                autoSaveCheck,
                saveStatus);
        c.setPadding(new Insets(10));
        tab.setContent(c);
        return tab;
    }

    // =========================================================== Tab: 🗡 Токены

    private Tab buildTokenTab() {
        Tab tab = new Tab("🗡 Токены");

        ComboBox<JsonNode> creatureCatalogCombo = new ComboBox<>();
        creatureCatalogCombo.setPrefWidth(185);
        creatureCatalogCombo.setConverter(jsonNodeConverter(
                n -> n.path("name").asText("?") + " [" + n.path("size").asText() + "]"));
        creatureCatalogCombo.getItems().addAll(tokenCatalog);

        TextField tokenNameField = new TextField("Гоблин");
        tokenNameField.setPrefWidth(110);
        Spinner<Integer> hpSpinner    = makeSpinner(1, 999, 20);
        Label            gridSizeLbl  = new Label("1×1");

        creatureCatalogCombo.setOnAction(e -> {
            JsonNode sel = creatureCatalogCombo.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            tokenNameField.setText(sel.path("name").asText("Существо"));
            int gs = sel.path("gridSize").asInt(1);
            gridSizeLbl.setText(gs + "×" + gs);
            hpSpinner.getValueFactory().setValue(switch (sel.path("size").asText("medium")) {
                case "tiny"  -> 5;
                case "small" -> 10;
                case "large" -> 50;
                case "huge"  -> 150;
                default      -> 20;
            });
        });

        Button addTokenBtn = new Button("➕ Добавить");
        addTokenBtn.setOnAction(e -> addToken(
                tokenNameField.getText(),
                creatureCatalogCombo.getSelectionModel().getSelectedItem(),
                hpSpinner.getValue()));

        tokenActionsCombo = makeCombo(200, t -> {
            if (t == null) return "";
            String own  = t.getOwnerId() == null ? "NPC" : "игрок…" + shortId(t.getOwnerId());
            String size = t.getGridSize() > 1
                    ? " [" + t.getGridSize() + "×" + t.getGridSize() + "]" : "";
            return t.getName() + size + " (" + own + ")";
        });

        playerAssignCombo = makeCombo(165,
                p -> p == null ? "" : p.getName() + " (" + shortId(p.getId()) + ")");

        Button assignBtn     = new Button("👤 → Игрок");
        Button unassignBtn   = new Button("→ NPC");
        Button removeTokenBtn = new Button("🗑 Удалить");
        assignBtn.setOnAction(e    -> assignSelectedTokenToPlayer());
        unassignBtn.setOnAction(e  -> unassignSelectedToken());
        removeTokenBtn.setOnAction(e -> removeSelectedToken());

        HBox row1 = new HBox(6,
                new Label("Каталог:"), creatureCatalogCombo,
                new Label("Имя:"),     tokenNameField,
                new Label("HP:"),      hpSpinner,
                new Label("Размер:"),  gridSizeLbl,
                addTokenBtn);
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPadding(new Insets(6));

        HBox row2 = new HBox(6,
                new Label("Выбрать:"), tokenActionsCombo,
                new Label("Игрок:"),   playerAssignCombo,
                assignBtn, unassignBtn, removeTokenBtn);
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.setPadding(new Insets(6));

        tab.setContent(new VBox(row1, new Separator(), row2));
        return tab;
    }

    // =========================================================== Tab: 🧱 Объекты

    private Tab buildObjectTab() {
        Tab tab = new Tab("🧱 Объекты");

        ComboBox<JsonNode> objectCatalogCombo = new ComboBox<>();
        objectCatalogCombo.setPrefWidth(205);
        objectCatalogCombo.setConverter(jsonNodeConverter(
                n -> n.path("name").asText("?") + " [" + n.path("category").asText() + "]"));
        objectCatalogCombo.getItems().addAll(objectCatalog);

        var g0 = ClientState.getInstance().getGrid();
        objectColSpinner = makeSpinner(0, Math.max(0, g0.getCols() - 1), 0);
        objectRowSpinner = makeSpinner(0, Math.max(0, g0.getRows() - 1), 0);
        Spinner<Integer> objWSpinner   = makeSpinner(1, 10, 1);
        Spinner<Integer> objHSpinner   = makeSpinner(1, 10, 1);
        Label            objPreviewLbl = new Label("1×1");

        objectCatalogCombo.setOnAction(e -> {
            JsonNode sel = objectCatalogCombo.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            int w = sel.path("defaultWidth").asInt(1);
            int h = sel.path("defaultHeight").asInt(1);
            objWSpinner.getValueFactory().setValue(w);
            objHSpinner.getValueFactory().setValue(h);
            objPreviewLbl.setText(w + "×" + h + " " + sel.path("name").asText());
        });

        Button placeBtn = new Button("📌 Разместить");
        placeBtn.setOnAction(e -> placeObject(
                objectCatalogCombo.getSelectionModel().getSelectedItem(),
                objectColSpinner.getValue(), objectRowSpinner.getValue(),
                objWSpinner.getValue(), objHSpinner.getValue()));

        objectRemoveCombo = makeCombo(205,
                o -> o == null ? "" : o.getType() + " @(" + o.getCol() + "," + o.getRow() + ")");
        Button removeObjectBtn = new Button("🗑 Удалить");
        removeObjectBtn.setOnAction(e -> removeSelectedObject());

        HBox row1 = new HBox(6,
                new Label("Тип:"),  objectCatalogCombo,
                new Label("col:"),  objectColSpinner,
                new Label("row:"),  objectRowSpinner,
                new Label("W:"),    objWSpinner,
                new Label("H:"),    objHSpinner,
                objPreviewLbl, placeBtn);
        row1.setAlignment(Pos.CENTER_LEFT);
        row1.setPadding(new Insets(6));

        HBox row2 = new HBox(6, new Label("Удалить:"), objectRemoveCombo, removeObjectBtn);
        row2.setAlignment(Pos.CENTER_LEFT);
        row2.setPadding(new Insets(6));

        tab.setContent(new VBox(row1, new Separator(), row2));
        return tab;
    }

    // =========================================================== Tab: 🗺 Карта

    private Tab buildGridTab(String sessionId) {
        Tab tab = new Tab("🗺 Карта");

        var g0 = ClientState.getInstance().getGrid();
        Spinner<Integer> colsSpinner = makeSpinner(4, 60, g0.getCols());
        Spinner<Integer> rowsSpinner = makeSpinner(4, 60, g0.getRows());
        Spinner<Integer> cellSpinner = makeSpinner(24, 128, g0.getCellSize());

        Button applyGridBtn = new Button("✅ Применить сетку");
        applyGridBtn.setOnAction(e ->
                applyGridResize(colsSpinner.getValue(), rowsSpinner.getValue(), cellSpinner.getValue()));

        Button uploadMapBtn  = new Button("🖼 Загрузить фон");
        Label  uploadStatus  = new Label("");

        uploadMapBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Выберите изображение карты");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                    "Images", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif"));
            java.io.File file = fc.showOpenDialog(stage);
            if (file == null) return;

            uploadStatus.setText("Загрузка...");
            uploadMapBtn.setDisable(true);
            ServerConnection.getInstance().uploadMap(
                    currentServerUrl,
                    ClientState.getInstance().getSessionId(),
                    file,
                    url -> {
                        uploadMapBtn.setDisable(false);
                        if (url != null && mapCanvas != null) {
                            // FIX: url от сервера — относительный (/uploads/...)
                            mapCanvas.setBackground(currentServerUrl + url.trim());
                            uploadStatus.setText("✅ Загружено");
                        } else {
                            uploadStatus.setText("❌ Ошибка загрузки");
                        }
                    });
        });

        HBox row = new HBox(8,
                new Label("cols:"), colsSpinner,
                new Label("rows:"), rowsSpinner,
                new Label("cell px:"), cellSpinner,
                applyGridBtn,
                new Separator(),
                uploadMapBtn, uploadStatus);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8));

        tab.setContent(row);
        return tab;
    }

    // =========================================================== Tab: ❤ HP

    private Tab buildHpTab() {
        Tab tab = new Tab("❤ HP");

        ComboBox<TokenDto> hpTokenCombo = makeCombo(225,
                t -> t == null ? "" : t.getName() + " [" + t.getHp() + "/" + t.getMaxHp() + "]");
        Spinner<Integer> newHpSpinner    = makeSpinner(0, 9999, 20);
        Spinner<Integer> newMaxHpSpinner = makeSpinner(1, 9999, 20);
        Label            currentHpLbl   = new Label("HP: —/—");

        hpTokenCombo.setOnAction(e -> {
            TokenDto t = hpTokenCombo.getSelectionModel().getSelectedItem();
            if (t == null) return;
            newHpSpinner.getValueFactory().setValue(t.getHp());
            newMaxHpSpinner.getValueFactory().setValue(t.getMaxHp());
            currentHpLbl.setText("HP: " + t.getHp() + " / " + t.getMaxHp());
        });

        Button dmgBtn  = new Button("⚔ -");  dmgBtn.setStyle("-fx-base: #c0392b;");
        Spinner<Integer> deltaSpinner = makeSpinner(1, 999, 5);
        Button healBtn = new Button("💚 +");  healBtn.setStyle("-fx-base: #27ae60;");
        Button setBtn  = new Button("💾 Задать");
        Button killBtn = new Button("💀 0 HP"); killBtn.setStyle("-fx-base: #7f8c8d;");

        dmgBtn.setOnAction(e -> {
            TokenDto t = hpTokenCombo.getSelectionModel().getSelectedItem(); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(),
                    Math.max(0, t.getHp() - deltaSpinner.getValue()), t.getMaxHp());
        });
        healBtn.setOnAction(e -> {
            TokenDto t = hpTokenCombo.getSelectionModel().getSelectedItem(); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(),
                    Math.min(t.getMaxHp(), t.getHp() + deltaSpinner.getValue()), t.getMaxHp());
        });
        setBtn.setOnAction(e -> {
            TokenDto t = hpTokenCombo.getSelectionModel().getSelectedItem(); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(),
                    newHpSpinner.getValue(), newMaxHpSpinner.getValue());
        });
        killBtn.setOnAction(e -> {
            TokenDto t = hpTokenCombo.getSelectionModel().getSelectedItem(); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(), 0, t.getMaxHp());
        });

        ClientState.getInstance().addChangeListener(() -> {
            String keepId = selectedId(hpTokenCombo, TokenDto::getId);
            hpTokenCombo.getItems().setAll(ClientState.getInstance().getTokens().values());
            selectById(hpTokenCombo, keepId, TokenDto::getId);
            TokenDto sel = hpTokenCombo.getSelectionModel().getSelectedItem();
            if (sel != null) currentHpLbl.setText("HP: " + sel.getHp() + " / " + sel.getMaxHp());
        });

        HBox row1 = new HBox(8, new Label("Токен:"), hpTokenCombo, currentHpLbl);
        row1.setAlignment(Pos.CENTER_LEFT); row1.setPadding(new Insets(6));

        HBox row2 = new HBox(8,
                new Label("Урон/Лечение:"), dmgBtn, deltaSpinner, healBtn,
                new Separator(),
                new Label("HP:"), newHpSpinner, new Label("/ maxHP:"), newMaxHpSpinner,
                setBtn, killBtn);
        row2.setAlignment(Pos.CENTER_LEFT); row2.setPadding(new Insets(6));

        tab.setContent(new VBox(row1, new Separator(), row2));
        return tab;
    }

    // =========================================================== Tab: 🎲 Инициатива

    private Tab buildInitiativeTab() {
        Tab tab = new Tab("🎲 Инициатива");

        ListView<String> initiativeList = new ListView<>();
        initiativeList.setPrefHeight(120);

        ComboBox<TokenDto> addToInitCombo = makeCombo(205, t -> t == null ? "" : t.getName());
        Spinner<Integer>   iniSpinner     = makeSpinner(1, 30, 10);

        Button addBtn          = new Button("➕ Добавить");
        Button nextBtn         = new Button("▶ Следующий");
        Button clearBtn        = new Button("🔄 Сброс");
        Button removeFromIniBtn = new Button("🗑");
        Label  currentTurnLbl  = new Label("Текущий ход: —");
        currentTurnLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        addBtn.setOnAction(e -> {
            TokenDto t = addToInitCombo.getSelectionModel().getSelectedItem();
            if (t == null) return;
            initiativeQueue.add(new InitiativeEntry(t.getId(), t.getName(), iniSpinner.getValue()));
            initiativeQueue.sort(Comparator.comparingInt(InitiativeEntry::initiative).reversed());
            refreshInitiativeList(initiativeList, 0);
        });
        nextBtn.setOnAction(e -> {
            if (initiativeQueue.isEmpty()) return;
            currentInitiativeIndex = (currentInitiativeIndex + 1) % initiativeQueue.size();
            refreshInitiativeList(initiativeList, currentInitiativeIndex);
            currentTurnLbl.setText("Текущий ход: "
                    + initiativeQueue.get(currentInitiativeIndex).name());
        });
        clearBtn.setOnAction(e -> {
            initiativeQueue.clear();
            currentInitiativeIndex = 0;
            initiativeList.getItems().clear();
            currentTurnLbl.setText("Текущий ход: —");
        });
        removeFromIniBtn.setOnAction(e -> {
            int sel = initiativeList.getSelectionModel().getSelectedIndex();
            if (sel >= 0 && sel < initiativeQueue.size()) {
                initiativeQueue.remove(sel);
                if (currentInitiativeIndex >= initiativeQueue.size()) currentInitiativeIndex = 0;
                refreshInitiativeList(initiativeList, currentInitiativeIndex);
            }
        });
        ClientState.getInstance().addChangeListener(() -> {
            String keepId = selectedId(addToInitCombo, TokenDto::getId);
            addToInitCombo.getItems().setAll(ClientState.getInstance().getTokens().values());
            selectById(addToInitCombo, keepId, TokenDto::getId);
        });

        HBox row1 = new HBox(8, new Label("Токен:"), addToInitCombo,
                new Label("Инициатива:"), iniSpinner, addBtn, removeFromIniBtn);
        row1.setAlignment(Pos.CENTER_LEFT); row1.setPadding(new Insets(6));

        HBox row2 = new HBox(8, nextBtn, clearBtn, currentTurnLbl);
        row2.setAlignment(Pos.CENTER_LEFT); row2.setPadding(new Insets(6));

        VBox content = new VBox(row1, row2, initiativeList);
        content.setPadding(new Insets(4));
        tab.setContent(content);
        return tab;
    }

    private record InitiativeEntry(String id, String name, int initiative) {}

    private void refreshInitiativeList(ListView<String> list, int current) {
        list.getItems().clear();
        for (int i = 0; i < initiativeQueue.size(); i++) {
            var e = initiativeQueue.get(i);
            list.getItems().add((i == current ? "► " : "  ")
                    + e.name() + " [" + e.initiative() + "]");
        }
        if (!initiativeQueue.isEmpty()) list.getSelectionModel().select(current);
    }

    // =========================================================== refresh selectors

    private void refreshDmSelectors() {
        String keepTokenId  = selectedId(tokenActionsCombo,  TokenDto::getId);
        String keepPlayerId = selectedId(playerAssignCombo,  PlayerDto::getId);
        String keepObjId    = selectedId(objectRemoveCombo,  MapObjectDto::getId);

        tokenActionsCombo.getItems().setAll(ClientState.getInstance().getTokens().values());

        // FIX: PlayerDto.getRole() — строка "PLAYER", не enum
        playerAssignCombo.getItems().setAll(
                ClientState.getInstance().getPlayers().values().stream()
                        .filter(p -> "PLAYER".equalsIgnoreCase(p.getRole()))
                        .toList());

        objectRemoveCombo.getItems().setAll(ClientState.getInstance().getObjects().values());

        selectById(tokenActionsCombo,  keepTokenId,  TokenDto::getId);
        selectById(playerAssignCombo,  keepPlayerId, PlayerDto::getId);
        selectById(objectRemoveCombo,  keepObjId,    MapObjectDto::getId);

        var g    = ClientState.getInstance().getGrid();
        int maxC = Math.max(0, g.getCols() - 1);
        int maxR = Math.max(0, g.getRows() - 1);
        int pc   = Math.min(ClientState.getInstance().getPendingPlaceCol(), maxC);
        int pr   = Math.min(ClientState.getInstance().getPendingPlaceRow(), maxR);

        objectColSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxC, pc));
        objectRowSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxR, pr));
    }

    // =========================================================== actions

    private void addToken(String name, JsonNode catalogEntry, int hp) {
        int col = ClientState.getInstance().getPendingPlaceCol();
        int row = ClientState.getInstance().getPendingPlaceRow();
        int gs  = 1;
        String imageUrl = null;
        if (catalogEntry != null) {
            gs = catalogEntry.path("gridSize").asInt(1);
            String imgPath = catalogEntry.path("imagePath").asText(null);
            if (imgPath != null && !imgPath.equals("null")) imageUrl = "/" + imgPath;
        }
        PlayerDto sp = playerAssignCombo.getSelectionModel().getSelectedItem();
        ServerConnection.getInstance().createToken(
                name, col, row, hp, hp, gs, imageUrl,
                sp != null ? sp.getId() : null);
    }

    private void placeObject(JsonNode ce, int col, int row, int w, int h) {
        String type     = "wall";
        String imageUrl = null;
        if (ce != null) {
            type = ce.path("id").asText("wall");
            String ip = ce.path("imagePath").asText(null);
            if (ip != null && !ip.equals("null")) imageUrl = "/" + ip;
        }
        ServerConnection.getInstance().send("/map.object.create",
                new MapObjectCreateRequest(type, col, row, w, h, 1, imageUrl));
    }

    private void assignSelectedTokenToPlayer() {
        TokenDto  t = tokenActionsCombo.getSelectionModel().getSelectedItem();
        PlayerDto p = playerAssignCombo.getSelectionModel().getSelectedItem();
        if (t == null || p == null) return;
        TokenAssignRequest req = new TokenAssignRequest();
        req.setTokenId(t.getId()); req.setOwnerId(p.getId());
        ServerConnection.getInstance().send("/token.assign", req);
    }

    private void unassignSelectedToken() {
        TokenDto t = tokenActionsCombo.getSelectionModel().getSelectedItem();
        if (t == null) return;
        TokenAssignRequest req = new TokenAssignRequest();
        req.setTokenId(t.getId()); req.setOwnerId(null);
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
        GridConfig g = new GridConfig();
        g.setCols(cols); g.setRows(rows); g.setCellSize(cellSize);
        g.setOffsetX(0); g.setOffsetY(0);
        ServerConnection.getInstance().send("/map.grid.update", g);
    }

    // =========================================================== utils

    private static String shortId(String id) {
        if (id == null) return "";
        return id.length() <= 8 ? id : id.substring(0, 8) + "…";
    }

    private static <T> String selectedId(ComboBox<T> box,
                                         java.util.function.Function<T, String> f) {
        T v = box.getSelectionModel().getSelectedItem();
        return v == null ? null : f.apply(v);
    }

    private static <T> void selectById(ComboBox<T> box, String id,
                                       java.util.function.Function<T, String> f) {
        if (id == null) return;
        box.getItems().stream()
                .filter(t -> id.equals(f.apply(t)))
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

    private static StringConverter<JsonNode> jsonNodeConverter(
            java.util.function.Function<JsonNode, String> fn) {
        return new StringConverter<>() {
            @Override public String toString(JsonNode n) { return n == null ? "" : fn.apply(n); }
            @Override public JsonNode fromString(String s) { return null; }
        };
    }

    private static Spinner<Integer> makeSpinner(int min, int max, int initial) {
        Spinner<Integer> s = new Spinner<>(min, max, initial);
        s.setEditable(true);
        s.setPrefWidth(72);
        return s;
    }
}