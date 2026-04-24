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

public class MainStage {

    private final Stage stage;
    private BattleMapCanvas mapCanvas;
    private ScrollPane mapScrollPane;

    private ComboBox<TokenDto>     tokenActionsCombo;
    private ComboBox<PlayerDto>    playerAssignCombo;
    private ComboBox<MapObjectDto> objectRemoveCombo;
    private Spinner<Integer>       objectColSpinner;
    private Spinner<Integer>       objectRowSpinner;

    private String currentServerUrl = "http://localhost:8080";

    private final List<JsonNode>  tokenCatalog  = new ArrayList<>();
    private final List<JsonNode>  objectCatalog = new ArrayList<>();
    private final List<InitEntry> iniQueue      = new ArrayList<>();
    private int iniIndex = 0;

    // FIX: keep references to registered listeners so we can remove them
    // before registering new ones when the DM reconnects / loads another
    // session.  Without this, each call to switchToBattleMap stacks another
    // listener on the singleton ClientState, causing double background loads
    // and other ghost-update issues.
    private Runnable backgroundChangeListener = null;
    private Runnable selectorRefreshListener  = null;
    private Runnable hpRefreshListener = null;
    private Runnable initiativeRefreshListener = null;

    public MainStage(Stage stage) { this.stage = stage; }

    public void show() {
        stage.setTitle("Avalon DnD — DM");
        stage.setScene(new Scene(buildConnectForm(), 1280, 840));
        stage.show();
    }

    // ================================================================ Connect form

    private VBox buildConnectForm() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        Label title = new Label("Avalon DnD — DM Панель");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        TextField serverField      = new TextField("http://localhost:8080");
        TextField playerClientField = new TextField("http://localhost:5173");

        Tab newTab = new Tab("✨ Новая / существующая сессия");
        newTab.setClosable(false);
        {
            TextField sessionField = new TextField(); sessionField.setPromptText("ID сессии");
            TextField nameField = new TextField("DM");
            Button createBtn = new Button("Создать новую сессию");
            Button connectBtn = new Button("🔗 Подключиться");
            Label statusLbl = new Label("");

            createBtn.setOnAction(e -> {
                createBtn.setDisable(true); statusLbl.setText("Создание...");
                ServerConnection.getInstance().createSession(serverField.getText().trim(), sid -> {
                    createBtn.setDisable(false);
                    if (sid != null) { sessionField.setText(sid); statusLbl.setText("✅ " + sid); }
                    else statusLbl.setText("❌ Не удалось создать");
                });
            });
            connectBtn.setOnAction(e -> {
                String url = serverField.getText().trim();
                String sid = sessionField.getText().trim();
                String nm  = nameField.getText().trim();
                if (url.isEmpty() || sid.isEmpty() || nm.isEmpty()) {
                    statusLbl.setText("⚠ Заполните все поля"); return;
                }
                currentServerUrl = url; statusLbl.setText("Подключение...");
                loadCatalog(url, () -> ServerConnection.getInstance().connect(url, sid, nm, true,
                        v -> Platform.runLater(() ->
                                switchToBattleMap(playerClientField.getText().trim(), sid))));
            });

            VBox c = new VBox(8, new Label("ID сессии:"), sessionField, new Label("Имя DM:"), nameField,
                    new HBox(8, createBtn, connectBtn), statusLbl);
            c.setPadding(new Insets(10)); newTab.setContent(c);
        }

        Tab loadTab = new Tab("📂 Загрузить сохранённую");
        loadTab.setClosable(false);
        {
            TableView<JsonNode> table = buildSavedSessionsTable();
            TextField dmName = new TextField("DM"); dmName.setPrefWidth(100);
            Button refresh  = new Button("🔄");
            Button loadBtn  = new Button("▶ Загрузить");
            Button deleteBtn = new Button("🗑");
            Label statusLbl = new Label("");

            Runnable doRefresh = () -> {
                statusLbl.setText("...");
                ServerConnection.getInstance().listSavedSessions(
                        serverField.getText().trim(),
                        list -> { table.getItems().setAll(list); statusLbl.setText(""); });
            };
            refresh.setOnAction(e -> doRefresh.run());
            loadBtn.setOnAction(e -> {
                JsonNode sel = table.getSelectionModel().getSelectedItem();
                if (sel == null) { statusLbl.setText("Выберите сессию"); return; }
                String sid  = sel.path("sessionId").asText();
                String url  = serverField.getText().trim();
                String name = dmName.getText().trim().isEmpty() ? "DM" : dmName.getText().trim();
                statusLbl.setText("Загрузка...");
                ServerConnection.getInstance().loadSession(url, sid, id -> {
                    if (id == null) { statusLbl.setText("❌ Ошибка"); return; }
                    currentServerUrl = url;
                    loadCatalog(url, () -> ServerConnection.getInstance().connect(url, id, name, true,
                            v -> Platform.runLater(() ->
                                    switchToBattleMap(playerClientField.getText().trim(), id))));
                });
            });
            deleteBtn.setOnAction(e -> {
                JsonNode sel = table.getSelectionModel().getSelectedItem(); if (sel == null) return;
                String sid = sel.path("sessionId").asText();
                new Thread(() -> {
                    try { new okhttp3.OkHttpClient().newCall(new okhttp3.Request.Builder()
                            .url(serverField.getText().trim() + "/api/session/" + sid + "/saved")
                            .delete().build()).execute().close(); }
                    catch (Exception ex) { ex.printStackTrace(); }
                    Platform.runLater(doRefresh::run);
                }).start();
            });
            HBox row = hbox(8, refresh, loadBtn, deleteBtn, new Label("Имя DM:"), dmName);
            row.setAlignment(Pos.CENTER_LEFT);
            VBox c = new VBox(8, table, row, statusLbl); c.setPadding(new Insets(10));
            loadTab.setContent(c);
        }

        TabPane tabPane = new TabPane(newTab, loadTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        root.getChildren().addAll(title,
                new Label("Сервер:"), serverField,
                new Label("Player-client URL:"), playerClientField,
                tabPane);
        return root;
    }

    @SuppressWarnings("unchecked")
    private TableView<JsonNode> buildSavedSessionsTable() {
        TableView<JsonNode> t = new TableView<>();
        t.setPrefHeight(200);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<JsonNode, String> cn = new TableColumn<>("Название");
        cn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path("displayName").asText()));
        TableColumn<JsonNode, String> ci = new TableColumn<>("ID");
        ci.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path("sessionId").asText()));
        TableColumn<JsonNode, String> cd = new TableColumn<>("Дата");
        cd.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().path("savedAt").asText().replace("T", " ")));
        t.getColumns().addAll(cn, ci, cd);
        return t;
    }

    // ================================================================ Catalog

    private void loadCatalog(String serverUrl, Runnable onDone) {
        new Thread(() -> {
            try {
                var req = new okhttp3.Request.Builder()
                        .url(serverUrl + "/api/assets/catalog").build();
                try (var r = new okhttp3.OkHttpClient().newCall(req).execute()) {
                    if (r.isSuccessful() && r.body() != null) {
                        var m = new com.fasterxml.jackson.databind.ObjectMapper();
                        JsonNode root = m.readTree(r.body().string());
                        tokenCatalog.clear(); objectCatalog.clear();
                        if (root.has("tokens"))  root.get("tokens").forEach(tokenCatalog::add);
                        if (root.has("objects")) root.get("objects").forEach(objectCatalog::add);
                    }
                }
            } catch (Exception ex) { System.err.println("Catalog: " + ex.getMessage()); }
            Platform.runLater(onDone::run);
        }).start();
    }

    // ================================================================ Battle map

    private void switchToBattleMap(String playerClientBase, String sessionId) {

        // FIX: remove stale listeners from any previous session so they don't
        // fire on the new canvas / new ClientState data.
        if (backgroundChangeListener != null) {
            ClientState.getInstance().removeChangeListener(backgroundChangeListener);
            backgroundChangeListener = null;
        }
        if (selectorRefreshListener != null) {
            ClientState.getInstance().removeChangeListener(selectorRefreshListener);
            selectorRefreshListener = null;
        }
        if (hpRefreshListener != null) {
            ClientState.getInstance().removeChangeListener(hpRefreshListener);
            hpRefreshListener = null;
        }
        if (initiativeRefreshListener != null) {
            ClientState.getInstance().removeChangeListener(initiativeRefreshListener);
            initiativeRefreshListener = null;
        }

        mapCanvas = new BattleMapCanvas();
        mapCanvas.setServerBaseUrl(currentServerUrl);

        // FIX: backgroundUrl is already a relative path like "/uploads/maps/..."
        // Prepend the server base only once here; the listener below does the same.
        String bg = ClientState.getInstance().getBackgroundUrl();
        if (bg != null && !bg.isEmpty()) {
            mapCanvas.setBackground(currentServerUrl + bg);
        }

        // FIX: keep the reference so we can remove it on next switchToBattleMap
        backgroundChangeListener = () -> {
            String url = ClientState.getInstance().getBackgroundUrl();
            if (url != null && !url.isEmpty() && mapCanvas != null) {
                // url is relative ("/uploads/maps/..."), prepend base once
                mapCanvas.setBackground(currentServerUrl + url);
            }
        };
        ClientState.getInstance().addChangeListener(backgroundChangeListener);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                buildTokenTab(), buildObjectTab(), buildGridTab(sessionId),
                buildHpTab(), buildInitiativeTab(), buildSessionTab(sessionId));

        Label linkLbl = new Label(
                (playerClientBase.isEmpty() ? "http://localhost:5173" : playerClientBase)
                        + "  →  " + sessionId);
        linkLbl.setStyle("-fx-font-family: monospace; -fx-font-size: 11px;");
        Button copyBtn = new Button("📋");
        copyBtn.setOnAction(e -> {
            var cb = javafx.scene.input.Clipboard.getSystemClipboard();
            var cc = new javafx.scene.input.ClipboardContent();
            cc.putString(sessionId); cb.setContent(cc);
        });
        HBox bar = hbox(8, linkLbl, copyBtn); bar.setPadding(new Insets(4, 8, 4, 8));

        mapScrollPane = new ScrollPane(mapCanvas);
        mapScrollPane.setFitToWidth(false);
        mapScrollPane.setFitToHeight(false);
        mapScrollPane.setPannable(false);

        BorderPane root = new BorderPane();
        root.setTop(new VBox(tabs, bar));
        root.setCenter(mapScrollPane);
        stage.getScene().setRoot(root);
        mapCanvas.render();

        // FIX: keep reference for cleanup
        selectorRefreshListener = this::refreshSelectors;
        ClientState.getInstance().addChangeListener(selectorRefreshListener);
        refreshSelectors();
    }

    // ================================================================ Tab: 💾 Сессия

    private Tab buildSessionTab(String sessionId) {
        Tab tab = new Tab("💾 Сессия");
        Label idLbl = new Label("ID: " + sessionId); idLbl.setStyle("-fx-font-family: monospace;");
        TextField nameField = new TextField("Моя сессия");
        Button saveBtn = new Button("💾 Сохранить"); Label saveStatus = new Label("");
        saveBtn.setOnAction(e -> {
            String n = nameField.getText().trim().isEmpty() ? "Сессия" : nameField.getText().trim();
            saveBtn.setDisable(true); saveStatus.setText("...");
            ServerConnection.getInstance().saveSession(currentServerUrl, sessionId, n,
                    ok -> { saveBtn.setDisable(false); saveStatus.setText(ok ? "✅ Сохранено" : "❌ Ошибка"); });
        });
        CheckBox auto = new CheckBox("Автосохранение каждые 5 минут");
        final javafx.animation.Timeline[] tl = {null};
        auto.setOnAction(e -> {
            if (auto.isSelected()) {
                tl[0] = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                        javafx.util.Duration.minutes(5), ae -> {
                    String n = nameField.getText().trim().isEmpty() ? "Авто" : nameField.getText().trim();
                    ServerConnection.getInstance().saveSession(currentServerUrl, sessionId, n,
                            ok -> saveStatus.setText(ok ? "✅ Авто" : "❌ Ошибка"));
                }));
                tl[0].setCycleCount(javafx.animation.Animation.INDEFINITE); tl[0].play();
                saveStatus.setText("Автосохранение включено");
            } else { if (tl[0] != null) tl[0].stop(); saveStatus.setText("Отключено"); }
        });
        VBox c = new VBox(10, idLbl, hbox(8, new Label("Название:"), nameField, saveBtn), auto, saveStatus);
        c.setPadding(new Insets(10)); tab.setContent(c); return tab;
    }

    // ================================================================ Tab: 🗡 Токены

    private Tab buildTokenTab() {
        Tab tab = new Tab("🗡 Токены");
        ComboBox<JsonNode> catCombo = makeJsonCombo(185, n ->
                n.path("name").asText() + " [" + n.path("size").asText() + "]");
        catCombo.getItems().addAll(tokenCatalog);
        TextField nameField = new TextField("Гоблин"); nameField.setPrefWidth(110);
        Spinner<Integer> hpSpin = makeSpinner(1, 999, 20);
        Label sizeLbl = new Label("1×1");

        catCombo.setOnAction(e -> {
            JsonNode s = catCombo.getSelectionModel().getSelectedItem(); if (s == null) return;
            nameField.setText(s.path("name").asText());
            int gs = s.path("gridSize").asInt(1); sizeLbl.setText(gs + "×" + gs);
            hpSpin.getValueFactory().setValue(switch (s.path("size").asText("medium")) {
                case "tiny" -> 5; case "small" -> 10; case "large" -> 50; case "huge" -> 150; default -> 20;
            });
        });

        Button addBtn = new Button("➕ Добавить");
        addBtn.setOnAction(e -> {
            addToken(nameField.getText(), catCombo.getSelectionModel().getSelectedItem(), hpSpin.getValue());
            playerAssignCombo.getSelectionModel().clearSelection();
        });

        tokenActionsCombo = makeCombo(210, t -> {
            if (t == null) return "";
            String own = t.getOwnerId() == null ? "NPC" : getPlayerName(t.getOwnerId());
            String sz  = t.getGridSize() > 1 ? " [" + t.getGridSize() + "×]" : "";
            return t.getName() + sz + " (" + own + ")";
        });

        playerAssignCombo = makeCombo(140,
                p -> p == null ? "" : p.getName());

        Button assignBtn   = new Button("👤 → Игрок"); assignBtn.setOnAction(e -> assignToken());
        Button unassignBtn = new Button("→ NPC");     unassignBtn.setOnAction(e -> unassignToken());
        Button removeBtn   = new Button("🗑");         removeBtn.setOnAction(e -> removeToken());

        HBox r1 = hbox(6, new Label("Каталог:"), catCombo, new Label("Имя:"), nameField,
                new Label("HP:"), hpSpin, new Label("Размер:"), sizeLbl, addBtn);
        HBox r2 = hbox(6, new Label("Токен:"), tokenActionsCombo,
                new Label("Игрок:"), playerAssignCombo, assignBtn, unassignBtn, removeBtn);
        tab.setContent(new VBox(r1, new Separator(), r2)); return tab;
    }

    private String getPlayerName(String ownerId) {
        PlayerDto p = ClientState.getInstance().getPlayers().get(ownerId);
        return p != null ? p.getName() : shortId(ownerId);
    }

    // ================================================================ Tab: 🧱 Объекты

    private Tab buildObjectTab() {
        Tab tab = new Tab("🧱 Объекты");
        ComboBox<JsonNode> catCombo = makeJsonCombo(210, n ->
                n.path("name").asText() + " [" + n.path("category").asText() + "]");
        catCombo.getItems().addAll(objectCatalog);
        var g0 = ClientState.getInstance().getGrid();
        objectColSpinner = makeSpinner(0, Math.max(0, g0.getCols() - 1), 0);
        objectRowSpinner = makeSpinner(0, Math.max(0, g0.getRows() - 1), 0);
        Spinner<Integer> wSpin = makeSpinner(1, 10, 1);
        Spinner<Integer> hSpin = makeSpinner(1, 10, 1);
        Label prevLbl = new Label("1×1");
        catCombo.setOnAction(e -> {
            JsonNode s = catCombo.getSelectionModel().getSelectedItem(); if (s == null) return;
            int w = s.path("defaultWidth").asInt(1); int h = s.path("defaultHeight").asInt(1);
            wSpin.getValueFactory().setValue(w); hSpin.getValueFactory().setValue(h);
            prevLbl.setText(w + "×" + h);
        });
        Button placeBtn = new Button("📌 Разместить");
        placeBtn.setOnAction(e -> placeObject(catCombo.getSelectionModel().getSelectedItem(),
                objectColSpinner.getValue(), objectRowSpinner.getValue(),
                wSpin.getValue(), hSpin.getValue()));
        objectRemoveCombo = makeCombo(210, o -> o == null ? "" :
                o.getType() + " @(" + o.getCol() + "," + o.getRow() + ")");
        Button removeBtn = new Button("🗑"); removeBtn.setOnAction(e -> removeObject());
        HBox r1 = hbox(6, new Label("Тип:"), catCombo, new Label("col:"), objectColSpinner,
                new Label("row:"), objectRowSpinner, new Label("W:"), wSpin,
                new Label("H:"), hSpin, prevLbl, placeBtn);
        HBox r2 = hbox(6, new Label("Удалить:"), objectRemoveCombo, removeBtn);
        tab.setContent(new VBox(r1, new Separator(), r2)); return tab;
    }

    // ================================================================ Tab: 🗺 Карта

    private Tab buildGridTab(String sessionId) {
        Tab tab = new Tab("🗺 Карта");
        var g0 = ClientState.getInstance().getGrid();
        Spinner<Integer> cols = makeSpinner(4, 60, g0.getCols());
        Spinner<Integer> rows = makeSpinner(4, 60, g0.getRows());
        Spinner<Integer> cell = makeSpinner(24, 128, g0.getCellSize());
        Button applyBtn = new Button("✅ Применить");
        applyBtn.setOnAction(e -> applyGrid(cols.getValue(), rows.getValue(), cell.getValue()));

        Button uploadBtn = new Button("🖼 Загрузить фон");
        Label upSt = new Label("");
        uploadBtn.setOnAction(e -> {
            javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
            fc.setTitle("Выберите изображение карты");
            fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter(
                    "Images", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif"));
            java.io.File file = fc.showOpenDialog(stage);
            if (file == null) return;
            uploadBtn.setDisable(true); upSt.setText("Загрузка...");
            ServerConnection.getInstance().uploadMap(currentServerUrl,
                    ClientState.getInstance().getSessionId(), file, url -> {
                        uploadBtn.setDisable(false);
                        if (url != null && mapCanvas != null) {
                            // FIX: url from server is already relative ("/uploads/maps/...").
                            // Prepend base here; the backgroundChangeListener will do the same
                            // when MAP_BACKGROUND_UPDATED arrives — that's fine because
                            // setBackground is idempotent (same URL → same cached image).
                            String full = currentServerUrl + url.trim();
                            mapCanvas.setBackground(full);
                            upSt.setText("✅ " + url.trim());
                        } else {
                            upSt.setText("❌ Ошибка. Проверьте консоль сервера.");
                        }
                    });
        });
        HBox row = hbox(8, new Label("cols:"), cols, new Label("rows:"), rows,
                new Label("cell px:"), cell, applyBtn, new Separator(), uploadBtn, upSt);
        tab.setContent(row); return tab;
    }

    // ================================================================ Tab: ❤ HP

    private Tab buildHpTab() {
        Tab tab = new Tab("❤ HP");
        ComboBox<TokenDto> hpCombo = makeCombo(225,
                t -> t == null ? "" : t.getName() + " [" + t.getHp() + "/" + t.getMaxHp() + "]");
        Spinner<Integer> hpSpin  = makeSpinner(0, 9999, 20);
        Spinner<Integer> maxSpin = makeSpinner(1, 9999, 20);
        Label curLbl = new Label("HP: —/—");

        hpCombo.setOnAction(e -> {
            TokenDto t = hpCombo.getSelectionModel().getSelectedItem(); if (t == null) return;
            hpSpin.getValueFactory().setValue(t.getHp()); maxSpin.getValueFactory().setValue(t.getMaxHp());
            curLbl.setText("HP: " + t.getHp() + " / " + t.getMaxHp());
        });

        Button dmg  = new Button("⚔ -"); dmg.setStyle("-fx-base:#c0392b;");
        Spinner<Integer> delta = makeSpinner(1, 999, 5);
        Button heal = new Button("💚 +"); heal.setStyle("-fx-base:#27ae60;");
        Button set  = new Button("💾 Задать");
        Button kill = new Button("💀 0"); kill.setStyle("-fx-base:#7f8c8d;");

        dmg.setOnAction(e -> { TokenDto t = sel(hpCombo); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(), Math.max(0, t.getHp() - delta.getValue()), t.getMaxHp()); });
        heal.setOnAction(e -> { TokenDto t = sel(hpCombo); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(), Math.min(t.getMaxHp(), t.getHp() + delta.getValue()), t.getMaxHp()); });
        set.setOnAction(e -> { TokenDto t = sel(hpCombo); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(), hpSpin.getValue(), maxSpin.getValue()); });
        kill.setOnAction(e -> { TokenDto t = sel(hpCombo); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(), 0, t.getMaxHp()); });

        hpRefreshListener = () -> {
            String keep = selId(hpCombo, TokenDto::getId);
            hpCombo.getItems().setAll(ClientState.getInstance().getTokens().values());
            selById(hpCombo, keep, TokenDto::getId);
            TokenDto s = sel(hpCombo);
            if (s != null) curLbl.setText("HP: " + s.getHp() + " / " + s.getMaxHp());
        };
        ClientState.getInstance().addChangeListener(hpRefreshListener);

        HBox r1 = hbox(8, new Label("Токен:"), hpCombo, curLbl);
        HBox r2 = hbox(8, new Label("Урон/Лечение:"), dmg, delta, heal,
                new Separator(), new Label("HP:"), hpSpin, new Label("/ max:"), maxSpin, set, kill);
        tab.setContent(new VBox(r1, new Separator(), r2)); return tab;
    }

    // ================================================================ Tab: 🎲 Инициатива

    private Tab buildInitiativeTab() {
        Tab tab = new Tab("🎲 Инициатива");
        ListView<String> listView = new ListView<>(); listView.setPrefHeight(130);
        ComboBox<TokenDto> addCombo = makeCombo(210, t -> t == null ? "" : t.getName());
        Spinner<Integer> iniSpin = makeSpinner(1, 30, 10);
        Button addBtn     = new Button("➕");
        Button removeBtn  = new Button("🗑");
        Button clearBtn   = new Button("🔄 Сброс");
        Button nextBtn    = new Button("▶ Следующий ход");
        Button publishBtn = new Button("📡 Опубликовать");
        publishBtn.setStyle("-fx-base: #2980b9;");
        Label curTurnLbl = new Label("Ход: —"); curTurnLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        initiativeRefreshListener = () -> {
            String keep = selId(addCombo, TokenDto::getId);
            addCombo.getItems().setAll(ClientState.getInstance().getTokens().values());
            selById(addCombo, keep, TokenDto::getId);
        };
        ClientState.getInstance().addChangeListener(initiativeRefreshListener);

        addBtn.setOnAction(e -> {
            TokenDto t = sel(addCombo); if (t == null) return;
            iniQueue.add(new InitEntry(t.getId(), t.getName(), iniSpin.getValue()));
            iniQueue.sort(Comparator.comparingInt(InitEntry::initiative).reversed());
            iniIndex = 0; refreshIniList(listView);
        });
        removeBtn.setOnAction(e -> {
            int idx = listView.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < iniQueue.size()) {
                iniQueue.remove(idx);
                if (iniIndex >= iniQueue.size()) iniIndex = 0;
                refreshIniList(listView);
            }
        });
        clearBtn.setOnAction(e -> {
            iniQueue.clear(); iniIndex = 0; listView.getItems().clear();
            curTurnLbl.setText("Ход: —"); ServerConnection.getInstance().clearInitiative();
        });
        nextBtn.setOnAction(e -> {
            if (iniQueue.isEmpty()) return;
            iniIndex = (iniIndex + 1) % iniQueue.size();
            refreshIniList(listView);
            curTurnLbl.setText("Ход: " + iniQueue.get(iniIndex).name());
            publishInitiative();
        });
        publishBtn.setOnAction(e -> publishInitiative());

        HBox r1 = hbox(8, new Label("Токен:"), addCombo, new Label("Иниц:"), iniSpin, addBtn, removeBtn);
        HBox r2 = hbox(8, nextBtn, clearBtn, publishBtn, curTurnLbl);
        VBox c = new VBox(8, r1, r2, listView); c.setPadding(new Insets(6));
        tab.setContent(c); return tab;
    }

    private void publishInitiative() {
        if (iniQueue.isEmpty()) return;
        var entries = iniQueue.stream()
                .map(e -> new InitiativeStateDto.InitiativeEntry(e.id(), e.name(), e.initiative()))
                .toList();
        ServerConnection.getInstance().publishInitiative(entries, iniIndex);
    }

    private void refreshIniList(ListView<String> list) {
        list.getItems().clear();
        for (int i = 0; i < iniQueue.size(); i++) {
            var e = iniQueue.get(i);
            list.getItems().add((i == iniIndex ? "► " : "  ") + e.name());
        }
        if (!iniQueue.isEmpty()) list.getSelectionModel().select(iniIndex);
    }

    private record InitEntry(String id, String name, int initiative) {}

    // ================================================================ Refresh

    private void refreshSelectors() {
        String keepToken  = selId(tokenActionsCombo, TokenDto::getId);
        String keepPlayer = selId(playerAssignCombo, PlayerDto::getId);
        String keepObj    = selId(objectRemoveCombo, MapObjectDto::getId);

        tokenActionsCombo.getItems().setAll(ClientState.getInstance().getTokens().values());

        playerAssignCombo.getItems().setAll(
                ClientState.getInstance().getPlayers().values().stream()
                        .filter(p -> "PLAYER".equalsIgnoreCase(p.getRole()))
                        .toList());

        objectRemoveCombo.getItems().setAll(ClientState.getInstance().getObjects().values());

        selById(tokenActionsCombo, keepToken,  TokenDto::getId);
        selById(playerAssignCombo, keepPlayer, PlayerDto::getId);
        selById(objectRemoveCombo, keepObj,    MapObjectDto::getId);

        var g = ClientState.getInstance().getGrid();
        int maxC = Math.max(0, g.getCols() - 1), maxR = Math.max(0, g.getRows() - 1);
        int pc = Math.min(ClientState.getInstance().getPendingPlaceCol(), maxC);
        int pr = Math.min(ClientState.getInstance().getPendingPlaceRow(), maxR);
        objectColSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxC, pc));
        objectRowSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxR, pr));
    }

    // ================================================================ Actions

    private void addToken(String name, JsonNode ce, int hp) {
        int col = ClientState.getInstance().getPendingPlaceCol();
        int row = ClientState.getInstance().getPendingPlaceRow();
        int gs = 1; String img = null;
        if (ce != null) {
            gs = ce.path("gridSize").asInt(1);
            String ip = ce.path("imagePath").asText(null);
            if (ip != null && !ip.equals("null")) img = "/" + ip;
        }
        PlayerDto p = playerAssignCombo.getSelectionModel().getSelectedItem();
        String ownerId = (p != null) ? p.getId() : null;
        ServerConnection.getInstance().createToken(name, col, row, hp, hp, gs, img, ownerId);
    }

    private void placeObject(JsonNode ce, int col, int row, int w, int h) {
        String type = "wall"; String img = null;
        if (ce != null) {
            type = ce.path("id").asText("wall");
            String ip = ce.path("imagePath").asText(null);
            if (ip != null && !ip.equals("null")) img = "/" + ip;
        }
        ServerConnection.getInstance().send("/map.object.create",
                new MapObjectCreateRequest(type, col, row, w, h, 1, img));
    }

    private void assignToken() {
        TokenDto t = sel(tokenActionsCombo); PlayerDto p = sel(playerAssignCombo);
        if (t == null || p == null) return;
        TokenAssignRequest req = new TokenAssignRequest();
        req.setTokenId(t.getId()); req.setOwnerId(p.getId());
        ServerConnection.getInstance().send("/token.assign", req);
    }

    private void unassignToken() {
        TokenDto t = sel(tokenActionsCombo); if (t == null) return;
        TokenAssignRequest req = new TokenAssignRequest();
        req.setTokenId(t.getId()); req.setOwnerId(null);
        ServerConnection.getInstance().send("/token.assign", req);
    }

    private void removeToken() {
        TokenDto t = sel(tokenActionsCombo); if (t == null) return;
        TokenRemoveEvent ev = new TokenRemoveEvent(); ev.setTokenId(t.getId());
        ServerConnection.getInstance().send("/token.remove", ev);
    }

    private void removeObject() {
        MapObjectDto o = sel(objectRemoveCombo); if (o == null) return;
        MapObjectRemoveEvent ev = new MapObjectRemoveEvent(); ev.setObjectId(o.getId());
        ServerConnection.getInstance().send("/map.object.remove", ev);
    }

    private void applyGrid(int cols, int rows, int cellSize) {
        GridConfig g = new GridConfig();
        g.setCols(cols); g.setRows(rows); g.setCellSize(cellSize); g.setOffsetX(0); g.setOffsetY(0);
        ServerConnection.getInstance().send("/map.grid.update", g);
    }

    // ================================================================ Utils

    private static String shortId(String id) {
        return id == null ? "" : id.length() <= 8 ? id : id.substring(0, 8) + "…";
    }

    private static <T> T sel(ComboBox<T> b) {
        return b.getSelectionModel().getSelectedItem();
    }

    private static <T> String selId(ComboBox<T> b, java.util.function.Function<T, String> f) {
        T v = b.getSelectionModel().getSelectedItem(); return v == null ? null : f.apply(v);
    }

    private static <T> void selById(ComboBox<T> b, String id,
                                    java.util.function.Function<T, String> f) {
        if (id == null) return;
        b.getItems().stream().filter(t -> id.equals(f.apply(t))).findFirst()
                .ifPresent(t -> b.getSelectionModel().select(t));
    }

    private static <T> ComboBox<T> makeCombo(int w, java.util.function.Function<T, String> ts) {
        ComboBox<T> c = new ComboBox<>(); c.setPrefWidth(w);
        c.setConverter(new StringConverter<>() {
            @Override public String toString(T o) { return ts.apply(o); }
            @Override public T fromString(String s) { return null; }
        });
        return c;
    }

    private static ComboBox<JsonNode> makeJsonCombo(int w,
                                                    java.util.function.Function<JsonNode, String> ts) {
        return makeCombo(w, n -> n == null ? "" : ts.apply(n));
    }

    private static Spinner<Integer> makeSpinner(int min, int max, int init) {
        Spinner<Integer> s = new Spinner<>(min, max, init);
        s.setEditable(true); s.setPrefWidth(72); return s;
    }

    private static HBox hbox(int gap, javafx.scene.Node... nodes) {
        HBox h = new HBox(gap, nodes);
        h.setAlignment(Pos.CENTER_LEFT); h.setPadding(new Insets(6)); return h;
    }
}