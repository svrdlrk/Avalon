package com.avalon.dnd.dm.ui;

import com.avalon.dnd.dm.canvas.BattleMapCanvas;
import com.avalon.dnd.dm.config.RuntimeConfig;
import com.avalon.dnd.dm.net.ServerConnection;
import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.*;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.*;
import java.util.Objects;

public class MainStage {

    private final Stage stage;
    private BattleMapCanvas mapCanvas;
    private ScrollPane mapScrollPane;
    private double mapZoom = 1.0;

    private String currentSessionId = "";
    private String currentPlayerClientBase = RuntimeConfig.defaultPlayerClientUrl();
    private final Label sessionSummaryLabel = new Label("Session —");
    private final Label sessionCountsLabel = new Label("Players 0 • Tokens 0 • Objects 0");

    {
        sessionSummaryLabel.getStyleClass().add("dm-chip");
        sessionCountsLabel.getStyleClass().addAll("dm-chip", "dm-chip-accent");
    }

    private ComboBox<TokenDto>     tokenActionsCombo;
    private ComboBox<PlayerDto>    playerAssignCombo;
    private ComboBox<MapObjectDto> objectRemoveCombo;
    private Spinner<Integer>       objectColSpinner;
    private Spinner<Integer>       objectRowSpinner;

    private String currentServerUrl = RuntimeConfig.defaultServerUrl();

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

    public void dispose() {
        if (mapCanvas != null) {
            mapCanvas.dispose();
            mapCanvas = null;
        }
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
    }

    public void show() {
        stage.setTitle("Avalon DnD — DM");
        Scene scene = new Scene(buildConnectForm(), 1280, 840);
        scene.getStylesheets().add(Objects.requireNonNull(
                MainStage.class.getResource("/com/avalon/dnd/dm/ui/dm-theme.css")
        ).toExternalForm());
        scene.setOnKeyPressed(event -> {
            if (mapScrollPane == null || mapCanvas == null) return;
            switch (event.getCode()) {
                case EQUALS, PLUS -> { zoomMap(1.08); event.consume(); }
                case MINUS, SUBTRACT -> { zoomMap(0.92); event.consume(); }
                case DIGIT0 -> { setMapZoom(1.0); centerMapView(); event.consume(); }
                case F -> { fitMapView(); event.consume(); }
                case C -> { centerMapView(); event.consume(); }
                case LEFT -> { mapCanvas.panBy(64, 0); event.consume(); }
                case RIGHT -> { mapCanvas.panBy(-64, 0); event.consume(); }
                case UP -> { mapCanvas.panBy(0, 64); event.consume(); }
                case DOWN -> { mapCanvas.panBy(0, -64); event.consume(); }
                default -> { }
            }
        });
        stage.setScene(scene);
        stage.show();
    }

    // ================================================================ Connect form

    private VBox buildConnectForm() {
        VBox root = new VBox(14);
        root.getStyleClass().add("dm-connect-root");
        root.setPadding(new Insets(24));
        root.setMaxWidth(720);
        root.setStyle("-fx-background-color: linear-gradient(to bottom, rgba(15,23,42,0.96), rgba(8,15,28,0.96));"
                + "-fx-border-color: rgba(148,163,184,0.14); -fx-border-width: 1; -fx-border-radius: 24; -fx-background-radius: 24;"
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.28), 28, 0.28, 0, 10);");

        Label title = new Label("Avalon DnD — DM Панель");
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #f8fafc;");

        Label subtitle = new Label("Подключение к сессии, загрузка сохранений и быстрый вход в боевой режим.");
        subtitle.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 13px;");
        subtitle.setWrapText(true);

        TextField serverField = new TextField(RuntimeConfig.defaultServerUrl());
        TextField playerClientField = new TextField(RuntimeConfig.defaultPlayerClientUrl());

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
                String serverUrl = RuntimeConfig.normalize(serverField.getText().trim());
                ServerConnection.getInstance().createSession(serverUrl, sid -> {
                    createBtn.setDisable(false);
                    if (sid != null) { sessionField.setText(sid); statusLbl.setText("✅ " + sid); }
                    else statusLbl.setText("❌ Не удалось создать");
                });
            });
            connectBtn.setOnAction(e -> {
                String url = RuntimeConfig.normalize(serverField.getText().trim());
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
            c.setPadding(new Insets(12));
            c.setStyle("-fx-background-color: rgba(15,23,42,0.58); -fx-border-color: rgba(148,163,184,0.12); -fx-border-radius: 18; -fx-background-radius: 18;");
            newTab.setContent(c);
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
                        RuntimeConfig.normalize(serverField.getText().trim()),
                        list -> { table.getItems().setAll(list); statusLbl.setText(""); });
            };
            refresh.setOnAction(e -> doRefresh.run());
            loadBtn.setOnAction(e -> {
                JsonNode sel = table.getSelectionModel().getSelectedItem();
                if (sel == null) { statusLbl.setText("Выберите сессию"); return; }
                String sid  = sel.path("sessionId").asText();
                String url  = RuntimeConfig.normalize(serverField.getText().trim());
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
                deleteBtn.setDisable(true);
                ServerConnection.getInstance().deleteSavedSession(
                        serverField.getText().trim(),
                        sid,
                        ok -> {
                            deleteBtn.setDisable(false);
                            Platform.runLater(doRefresh::run);
                        }
                );
            });
            FlowPane row = flowRow(8, 8, refresh, loadBtn, deleteBtn, new Label("Имя DM:"), dmName);
            row.setAlignment(Pos.CENTER_LEFT);
            VBox c = new VBox(8, table, row, statusLbl); c.setPadding(new Insets(12));
            c.setStyle("-fx-background-color: rgba(15,23,42,0.58); -fx-border-color: rgba(148,163,184,0.12); -fx-border-radius: 18; -fx-background-radius: 18;");
            loadTab.setContent(c);
        }

        TabPane tabPane = new TabPane(newTab, loadTab);
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setStyle("-fx-background-color: transparent;");
        tabPane.getStyleClass().add("dm-connect-tabs");

        VBox connectCard = new VBox(12,
                title,
                subtitle,
                new Label("Сервер:"), serverField,
                new Label("Player-client URL:"), playerClientField,
                tabPane);
        connectCard.setPadding(new Insets(20));
        connectCard.setMaxWidth(760);
        connectCard.getStyleClass().add("dm-connect-card");
        connectCard.setStyle("-fx-background-color: rgba(8,13,25,0.54); -fx-border-color: rgba(148,163,184,0.14); -fx-border-radius: 28; -fx-background-radius: 28;");

        StackPane wrapper = new StackPane(connectCard);
        wrapper.setPadding(new Insets(18));
        wrapper.getStyleClass().add("dm-connect-shell");
        wrapper.setStyle("-fx-background-color: radial-gradient(radius 120%, rgba(124,58,237,0.18), transparent 45%), radial-gradient(radius 110%, rgba(37,99,235,0.16), transparent 50%), linear-gradient(to bottom, #09101c, #070b14);");
        StackPane.setAlignment(connectCard, Pos.CENTER);
        root.setAlignment(Pos.CENTER);
        root.getChildren().add(wrapper);
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


    private void copyToClipboard(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        var cb = javafx.scene.input.Clipboard.getSystemClipboard();
        var cc = new javafx.scene.input.ClipboardContent();
        cc.putString(value);
        cb.setContent(cc);
    }

    private void centerMapView() {
        if (mapCanvas == null || mapScrollPane == null) {
            return;
        }
        double viewportW = Math.max(1, mapScrollPane.getViewportBounds().getWidth());
        double viewportH = Math.max(1, mapScrollPane.getViewportBounds().getHeight());
        mapCanvas.centerView();
        mapScrollPane.requestLayout();
    }

    private void setMapZoom(double zoom) {
        mapZoom = Math.max(0.25, Math.min(2.75, zoom));
        if (mapCanvas != null) {
            mapCanvas.setZoom(mapZoom);
        }
        if (mapScrollPane != null) {
            mapScrollPane.layout();
        }
    }

    private void zoomMap(double factor) {
        setMapZoom(mapZoom * factor);
        centerMapView();
    }

    private void fitMapView() {
        if (mapCanvas == null || mapScrollPane == null) {
            return;
        }
        double viewportW = Math.max(1, mapScrollPane.getViewportBounds().getWidth());
        double viewportH = Math.max(1, mapScrollPane.getViewportBounds().getHeight());
        mapCanvas.fitView(viewportW, viewportH);
        mapZoom = mapCanvas.getZoom();
        mapScrollPane.requestLayout();
    }

    private VBox buildBattleOverview(String playerClientBase, String sessionId) {
        Label title = new Label("Battle Control");
        title.getStyleClass().add("dm-title");

        Label subtitle = new Label((playerClientBase == null || playerClientBase.isBlank()
                ? RuntimeConfig.defaultPlayerClientUrl()
                : playerClientBase) + "  →  " + sessionId);
        subtitle.getStyleClass().add("dm-meta");
        subtitle.setWrapText(true);

        Button zoomOutBtn = new Button("Zoom -");
        zoomOutBtn.setOnAction(e -> zoomMap(0.88));

        Button zoomInBtn = new Button("Zoom +");
        zoomInBtn.setOnAction(e -> zoomMap(1.12));

        Button fitBtn = new Button("Fit");
        fitBtn.setOnAction(e -> fitMapView());

        Button centerBtn = new Button("Center map");
        centerBtn.setOnAction(e -> centerMapView());

        Button copySessionBtn = new Button("Copy session");
        copySessionBtn.getStyleClass().add("primary-action");
        copySessionBtn.setOnAction(e -> copyToClipboard(sessionId));

        Button copyPlayerBtn = new Button("Copy player URL");
        copyPlayerBtn.setOnAction(e -> copyToClipboard(playerClientBase == null || playerClientBase.isBlank()
                ? RuntimeConfig.defaultPlayerClientUrl()
                : playerClientBase));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox titleRow = new HBox(10, title, spacer, subtitle);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        FlowPane actions = new FlowPane(8, 8, zoomOutBtn, zoomInBtn, fitBtn, centerBtn, copyPlayerBtn, copySessionBtn);
        actions.setPrefWrapLength(780);
        actions.getStyleClass().add("dm-header-actions");

        HBox chips = new HBox(8, sessionSummaryLabel, sessionCountsLabel);
        chips.getStyleClass().add("dm-summary-row");
        chips.setPadding(new Insets(0, 14, 12, 14));

        VBox header = new VBox(8, titleRow, actions, chips);
        header.setStyle("-fx-background-color: rgba(7, 12, 20, 0.92); -fx-border-color: rgba(148, 163, 184, 0.12); -fx-border-width: 0 0 1 0;");
        return header;
    }

    private VBox buildSidebarIntro(String playerClientBase, String sessionId) {
        Label title = new Label("Session orbit");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #f8fafc;");

        Label hint = new Label("Use the rail below to manage tokens, objects, map layers, HP and initiative. Drag the map to pan, wheel to zoom.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #9fb0c8; -fx-font-size: 12px; -fx-line-spacing: 2px;");

        Label meta = new Label("Session " + shortId(sessionId) + " · "
                + (playerClientBase == null || playerClientBase.isBlank()
                ? RuntimeConfig.defaultPlayerClientUrl()
                : playerClientBase));
        meta.setWrapText(true);
        meta.setStyle("-fx-text-fill: #c9d6ea; -fx-font-size: 11px; -fx-font-family: Consolas, 'SFMono-Regular', monospace;");

        VBox shell = new VBox(8, title, hint, meta);
        shell.setPadding(new Insets(12));
        shell.getStyleClass().add("dm-summary-shell");
        return shell;
    }

    private Node buildSidebarWorkspace(String sessionId) {
        List<SidebarSection> sections = List.of(
                new SidebarSection("tokens", "🗡 Токены", buildTokenTab().getContent()),
                new SidebarSection("objects", "🧱 Объекты", buildObjectTab().getContent()),
                new SidebarSection("grid", "🗺 Карта", buildGridTab(sessionId).getContent()),
                new SidebarSection("hp", "❤ HP", buildHpTab().getContent()),
                new SidebarSection("initiative", "🎲 Инициатива", buildInitiativeTab().getContent()),
                new SidebarSection("session", "💾 Сессия", buildSessionTab(sessionId).getContent())
        );

        FlowPane nav = new FlowPane(8, 8);
        nav.setPrefWrapLength(350);
        nav.getStyleClass().add("dm-nav-bar");

        StackPane contentStack = new StackPane();
        contentStack.getStyleClass().add("dm-section-stack");

        Map<String, Node> contentById = new LinkedHashMap<>();
        ToggleGroup group = new ToggleGroup();
        final ToggleButton[] first = {null};

        for (SidebarSection section : sections) {
            ToggleButton button = new ToggleButton(section.title());
            button.setToggleGroup(group);
            button.setMaxWidth(Double.MAX_VALUE);
            button.setMinWidth(0);
            button.getStyleClass().add("dm-nav-toggle");

            Node wrapped = wrapSidebarSection(section.content());
            wrapped.setVisible(false);
            wrapped.setManaged(false);
            contentStack.getChildren().add(wrapped);
            contentById.put(section.id(), wrapped);

            button.setOnAction(e -> showSidebarSection(section.id(), group, contentById));
            nav.getChildren().add(button);
            if (first[0] == null) {
                first[0] = button;
            }
        }

        if (first[0] != null) {
            first[0].setSelected(true);
            showSidebarSection(sections.get(0).id(), group, contentById);
        }

        VBox shell = new VBox(12, buildSidebarIntro(currentPlayerClientBase, sessionId), nav, contentStack);
        shell.getStyleClass().add("dm-sidebar-shell");
        return shell;
    }

    private static Node wrapSidebarSection(Node content) {
        VBox box = new VBox(content);
        box.setPadding(new Insets(10));
        box.getStyleClass().add("dm-section-card");
        return box;
    }

    private static void showSidebarSection(String selectedId,
                                           ToggleGroup group,
                                           Map<String, Node> contentById) {
        for (var entry : contentById.entrySet()) {
            boolean active = Objects.equals(entry.getKey(), selectedId);
            entry.getValue().setVisible(active);
            entry.getValue().setManaged(active);
        }
        if (group.getSelectedToggle() == null) {
            for (Toggle toggle : group.getToggles()) {
                if (toggle instanceof ToggleButton button && button.getText() != null) {
                    // no-op; selection already set by caller
                    break;
                }
            }
        }
    }

    private record SidebarSection(String id, String title, Node content) {}

    private void refreshBattleSummary() {
        String sid = currentSessionId == null || currentSessionId.isBlank() ? "—" : shortId(currentSessionId);
        sessionSummaryLabel.setText("Session " + sid);

        int players = ClientState.getInstance().getPlayers().size();
        int tokens = ClientState.getInstance().getTokens().size();
        int objects = ClientState.getInstance().getObjects().size();
        sessionCountsLabel.setText(String.format(java.util.Locale.ROOT,
                "%d players • %d tokens • %d objects", players, tokens, objects));
    }

    // ================================================================ Catalog

    private void loadCatalog(String serverUrl, Runnable onDone) {
        String baseUrl = RuntimeConfig.normalize(serverUrl);
        Thread loader = new Thread(() -> {
            List<JsonNode> loadedTokens = new ArrayList<>();
            List<JsonNode> loadedObjects = new ArrayList<>();
            try {
                var req = new okhttp3.Request.Builder()
                        .url(baseUrl + "/api/assets/catalog").build();
                try (var r = new okhttp3.OkHttpClient().newCall(req).execute()) {
                    if (r.isSuccessful() && r.body() != null) {
                        var m = new com.fasterxml.jackson.databind.ObjectMapper();
                        JsonNode root = m.readTree(r.body().string());
                        if (root.has("tokens"))  root.get("tokens").forEach(loadedTokens::add);
                        if (root.has("objects")) root.get("objects").forEach(loadedObjects::add);
                    }
                }
            } catch (Exception ex) { System.err.println("Catalog: " + ex.getMessage()); }
            Platform.runLater(() -> {
                tokenCatalog.clear();
                tokenCatalog.addAll(loadedTokens);
                objectCatalog.clear();
                objectCatalog.addAll(loadedObjects);
                onDone.run();
            });
        }, "dm-load-catalog");
        loader.setDaemon(true);
        loader.start();
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

        currentSessionId = sessionId;
        currentPlayerClientBase = playerClientBase;

        if (mapCanvas != null) {
            mapCanvas.dispose();
        }
        mapCanvas = new BattleMapCanvas();
        mapCanvas.setServerBaseUrl(currentServerUrl);

        // FIX: backgroundUrl is already a relative path like "/uploads/maps/..."
        // Prepend the server base only once here; the listener below does the same.
        String bg = ClientState.getInstance().getBackgroundUrl();
        if (bg != null && !bg.isEmpty()) {
            mapCanvas.setBackground(bg);
        }

        // FIX: keep the reference so we can remove it on next switchToBattleMap
        backgroundChangeListener = () -> {
            String url = ClientState.getInstance().getBackgroundUrl();
            if (url != null && !url.isEmpty() && mapCanvas != null) {
                mapCanvas.setBackground(url);
            }
        };
        ClientState.getInstance().addChangeListener(backgroundChangeListener);

        VBox header = buildBattleOverview(playerClientBase, sessionId);
        header.setPadding(new Insets(0));

        mapScrollPane = new ScrollPane(mapCanvas);
        mapScrollPane.setFitToWidth(false);
        mapScrollPane.setFitToHeight(false);
        mapScrollPane.setPannable(true);
        mapScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        mapScrollPane.getStyleClass().add("dm-map-scroll");
        setMapZoom(1.0);
        java.util.function.Consumer<javafx.scene.input.ScrollEvent> zoomHandler = event -> {
            double delta = event.getDeltaY();
            if (Math.abs(delta) < 1e-3) {
                return;
            }
            zoomMap(delta > 0 ? 1.08 : 0.92);
            event.consume();
        };
        mapScrollPane.setOnScroll(zoomHandler::accept);
        mapCanvas.setOnScroll(zoomHandler::accept);

        VBox sidebar = new VBox(12, buildSidebarWorkspace(sessionId));
        sidebar.getStyleClass().add("dm-sidebar");
        sidebar.setPrefWidth(380);
        sidebar.setMaxWidth(520);

        ScrollPane sidebarScroll = new ScrollPane(sidebar);
        sidebarScroll.setFitToWidth(true);
        sidebarScroll.setPrefWidth(380);
        sidebarScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        sidebarScroll.getStyleClass().add("dm-sidebar-scroll");

        SplitPane workspace = new SplitPane(mapScrollPane, sidebarScroll);
        workspace.setDividerPositions(0.80);
        workspace.getStyleClass().add("dm-workspace");

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(workspace);
        root.getStyleClass().add("dm-root");
        stage.getScene().setRoot(root);
        refreshBattleSummary();
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

        Button addBtn = new Button("Добавить токен");
        addBtn.setOnAction(e -> {
            addToken(nameField.getText(), catCombo.getSelectionModel().getSelectedItem(), hpSpin.getValue());
            playerAssignCombo.getSelectionModel().clearSelection();
        });

        tokenActionsCombo = makeCombo(320, this::formatTokenLabel);
        configureTokenCombo(tokenActionsCombo);

        playerAssignCombo = makeCombo(180,
                p -> p == null ? "" : p.getName() + " · " + p.getRole());

        Button assignBtn   = new Button("Назначить игроку"); assignBtn.setOnAction(e -> assignToken());
        Button unassignBtn = new Button("Снять");     unassignBtn.setOnAction(e -> unassignToken());
        Button removeBtn   = new Button("Удалить");         removeBtn.setOnAction(e -> removeToken());

        Button browseBtn = new Button("Каталог");
        browseBtn.setOnAction(e -> AssetBrowserWindow.showTokenBrowser(stage, tokenCatalog, asset -> {
            if (asset != null) {
                catCombo.getSelectionModel().select(asset);
            }
        }));

        FlowPane r1 = flowRow(8, 8, new Label("Каталог:"), catCombo, browseBtn, new Label("Имя:"), nameField,
                new Label("HP:"), hpSpin, new Label("Размер:"), sizeLbl, addBtn);
        FlowPane r2 = flowRow(8, 8, new Label("Токен:"), tokenActionsCombo,
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
        Button placeBtn = new Button("Разместить объект");
        placeBtn.setOnAction(e -> placeObject(catCombo.getSelectionModel().getSelectedItem(),
                objectColSpinner.getValue(), objectRowSpinner.getValue(),
                wSpin.getValue(), hSpin.getValue()));
        objectRemoveCombo = makeCombo(210, o -> o == null ? "" :
                o.getType() + " @(" + o.getCol() + "," + o.getRow() + ")");
        Button removeBtn = new Button("Удалить"); removeBtn.setOnAction(e -> removeObject());
        Button browseBtn = new Button("Каталог");
        browseBtn.setOnAction(e -> AssetBrowserWindow.showObjectBrowser(stage, objectCatalog, asset -> {
            if (asset != null) {
                catCombo.getSelectionModel().select(asset);
            }
        }));

        FlowPane r1 = flowRow(8, 8, new Label("Тип:"), catCombo, browseBtn, new Label("col:"), objectColSpinner,
                new Label("row:"), objectRowSpinner, new Label("W:"), wSpin,
                new Label("H:"), hSpin, prevLbl, placeBtn);
        FlowPane r2 = flowRow(8, 8, new Label("Удалить:"), objectRemoveCombo, removeBtn);
        tab.setContent(new VBox(r1, new Separator(), r2)); return tab;
    }

    // ================================================================ Tab: 🗺 Карта

    private Tab buildGridTab(String sessionId) {
        Tab tab = new Tab("🗺 Карта");
        var g0 = ClientState.getInstance().getGrid();
        Spinner<Integer> cols = makeSpinner(4, 60, g0.getCols());
        Spinner<Integer> rows = makeSpinner(4, 60, g0.getRows());
        Spinner<Integer> cell = makeSpinner(24, 128, g0.getCellSize());
        Button applyBtn = new Button("Применить сетку");
        applyBtn.setOnAction(e -> applyGrid(cols.getValue(), rows.getValue(), cell.getValue()));

        Button uploadBtn = new Button("Загрузить фон");
        Button importMapBtn = new Button("Импорт карты");
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
        importMapBtn.setOnAction(e -> {
            javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
            chooser.setTitle("Выберите папку сохранённой карты");
            java.io.File defaultDir = resolveProjectUploadsDir("uploads/maps/finished");
            if (defaultDir != null && defaultDir.isDirectory()) {
                chooser.setInitialDirectory(defaultDir);
            }
            java.io.File dir = chooser.showDialog(stage);
            if (dir == null) return;
            importMapBtn.setDisable(true);
            upSt.setText("Импорт...");
            ServerConnection.getInstance().importMapWorkspace(
                    currentServerUrl,
                    ClientState.getInstance().getSessionId(),
                    dir.toPath(),
                    ok -> {
                        importMapBtn.setDisable(false);
                        upSt.setText(ok ? "✅ Карта загружена" : "❌ Ошибка импорта");
                    }
            );
        });
        FlowPane row = flowRow(8, 8, new Label("cols:"), cols, new Label("rows:"), rows,
                new Label("cell px:"), cell, applyBtn, new Separator(), uploadBtn, importMapBtn, upSt);
        tab.setContent(row); return tab;
    }

    // ================================================================ Tab: ❤ HP

    private Tab buildHpTab() {
        Tab tab = new Tab("❤ HP");
        ComboBox<TokenDto> hpCombo = makeCombo(340,
                this::formatTokenLabel);
        configureTokenCombo(hpCombo);
        Spinner<Integer> hpSpin  = makeSpinner(0, 9999, 20);
        Spinner<Integer> maxSpin = makeSpinner(1, 9999, 20);
        Label curLbl = new Label("Выбран: —");

        hpCombo.setOnAction(e -> {
            TokenDto t = hpCombo.getSelectionModel().getSelectedItem(); if (t == null) return;
            hpSpin.getValueFactory().setValue(t.getHp()); maxSpin.getValueFactory().setValue(t.getMaxHp());
            curLbl.setText("Выбран: " + formatTokenLabel(t));
        });

        Button dmg  = new Button("Урон"); dmg.setStyle("-fx-base:#c0392b;");
        Spinner<Integer> delta = makeSpinner(1, 999, 5);
        Button heal = new Button("Лечение"); heal.setStyle("-fx-base:#27ae60;");
        Button set  = new Button("Применить HP");
        Button kill = new Button("Обнулить"); kill.setStyle("-fx-base:#7f8c8d;");

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
            if (s != null) curLbl.setText("Выбран: " + formatTokenLabel(s));
        };
        ClientState.getInstance().addChangeListener(hpRefreshListener);

        FlowPane r1 = flowRow(8, 8, new Label("Токен:"), hpCombo, curLbl);
        FlowPane r2 = flowRow(8, 8, new Label("Урон/Лечение:"), dmg, delta, heal,
                new Separator(), new Label("HP:"), hpSpin, new Label("/ max:"), maxSpin, set, kill);
        tab.setContent(new VBox(r1, new Separator(), r2)); return tab;
    }

    // ================================================================ Tab: 🎲 Инициатива

    private Tab buildInitiativeTab() {
        Tab tab = new Tab("🎲 Инициатива");
        ListView<InitEntry> listView = new ListView<>();
        listView.setPrefHeight(130);
        listView.setCellFactory(lv -> createInitiativeCell(listView));
        ComboBox<TokenDto> addCombo = makeCombo(340, this::formatTokenLabel);
        configureTokenCombo(addCombo);
        Spinner<Integer> iniSpin = makeSpinner(1, 30, 10);
        Button addBtn     = new Button("Добавить в инициативу");
        Button removeBtn  = new Button("Удалить");
        Button clearBtn   = new Button("Сбросить");
        Button nextBtn    = new Button("Следующий ход");
        Button publishBtn = new Button("Публиковать");
        publishBtn.setStyle("-fx-base: #2980b9;");
        Label curTurnLbl = new Label("Ход: —"); curTurnLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        initiativeRefreshListener = () -> {
            String keep = selId(addCombo, TokenDto::getId);
            addCombo.getItems().setAll(ClientState.getInstance().getTokens().values());
            selById(addCombo, keep, TokenDto::getId);
            listView.refresh();
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
            curTurnLbl.setText("Ход: " + formatInitiativeEntry(iniQueue.get(iniIndex)));
            publishInitiative();
        });
        publishBtn.setOnAction(e -> publishInitiative());

        FlowPane r1 = flowRow(8, 8, new Label("Токен:"), addCombo, new Label("Иниц:"), iniSpin, addBtn, removeBtn);
        FlowPane r2 = flowRow(8, 8, nextBtn, clearBtn, publishBtn, curTurnLbl);
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

    private void refreshIniList(ListView<InitEntry> list) {
        list.getItems().setAll(iniQueue);
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

        refreshBattleSummary();

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
        int gs = 1; String img = null; int dayVision = 0; int nightVision = 0;
        if (ce != null) {
            gs = ce.path("gridSize").asInt(1);
            dayVision = ce.path("dayVision").asInt(0);
            nightVision = ce.path("nightVision").asInt(0);
            String ip = firstCatalogImageUrl(ce);
            if (ip != null && !ip.isBlank()) img = normalizeCatalogImageUrl(ip);
        }
        PlayerDto p = playerAssignCombo.getSelectionModel().getSelectedItem();
        String ownerId = (p != null) ? p.getId() : null;
        ServerConnection.getInstance().createToken(name, col, row, hp, hp, gs, img, ownerId, dayVision, nightVision);
    }

    private void placeObject(JsonNode ce, int col, int row, int w, int h) {
        String type = "wall"; String img = null;
        if (ce != null) {
            type = ce.path("id").asText("wall");
            String ip = firstCatalogImageUrl(ce);
            if (ip != null && !ip.isBlank()) img = normalizeCatalogImageUrl(ip);
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

    private static String firstCatalogImageUrl(JsonNode node) {
        if (node == null || node.isNull()) return null;
        for (String key : List.of("imageUrl", "imagePath", "image", "path", "file", "src", "url", "assetPath", "sprite", "thumbnail")) {
            JsonNode field = node.get(key);
            if (field == null || field.isNull()) continue;
            String value = field.asText(null);
            if (value != null && !value.isBlank() && looksLikeImagePath(value)) return value;
        }
        return null;
    }

    private static boolean looksLikeImagePath(String value) {
        if (value == null) return false;
        String lower = value.trim().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        if (lower.isBlank() || lower.endsWith("/")) return false;
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".svg")
                || lower.contains("/uploads/") || lower.contains("/assets/") || lower.contains(".png?") || lower.contains(".jpg?");
    }

    private static String normalizeCatalogImageUrl(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replace('\\', '/');
        if (value.isBlank()) return null;
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("file:") || value.startsWith("data:") || value.startsWith("jar:")) {
            return value;
        }
        if (value.startsWith("/uploads/") || value.startsWith("uploads/")) {
            return value.startsWith("/") ? value : "/" + value;
        }
        if (value.startsWith("/assets/") || value.startsWith("assets/")) {
            return value.startsWith("/") ? value : "/" + value;
        }
        value = value.startsWith("/") ? value.substring(1) : value;
        return "/uploads/assets/" + value;
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
        GridConfig current = ClientState.getInstance().getGrid();
        GridConfig g = new GridConfig();
        g.setCols(cols);
        g.setRows(rows);
        g.setCellSize(cellSize);
        g.setOffsetX(current.getOffsetX());
        g.setOffsetY(current.getOffsetY());
        ServerConnection.getInstance().send("/map.grid.update", g);
    }

    // ================================================================ Utils

    private String formatTokenLabel(TokenDto t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(t.getName() == null || t.getName().isBlank() ? "—" : t.getName());
        if (t.getId() != null && !t.getId().isBlank()) {
            sb.append(" · #").append(shortId(t.getId()));
        }
        sb.append(" · HP ").append(t.getHp()).append('/').append(t.getMaxHp());
        if (t.getGridSize() > 1) {
            sb.append(" · ").append(t.getGridSize()).append('×').append(t.getGridSize());
        }
        String owner = t.getOwnerId() == null ? "NPC" : getPlayerName(t.getOwnerId());
        if (owner != null && !owner.isBlank()) {
            sb.append(" · ").append(owner);
        }
        sb.append(" · @ ").append(t.getCol()).append(',').append(t.getRow());
        return sb.toString();
    }

    private String formatInitiativeEntry(InitEntry e) {
        if (e == null) return "";
        TokenDto live = e.id() == null ? null : ClientState.getInstance().getTokens().get(e.id());
        StringBuilder sb = new StringBuilder();
        if (live != null) {
            sb.append(formatTokenLabel(live));
        } else {
            sb.append(e.name() == null || e.name().isBlank() ? "—" : e.name());
            if (e.id() != null && !e.id().isBlank()) {
                sb.append(" · #").append(shortId(e.id()));
            }
            sb.append(" · HP —/— · @ —");
        }
        sb.append(" · Init ").append(e.initiative());
        return sb.toString();
    }

    private static String shortId(String id) {
        return id == null ? "" : id.length() <= 8 ? id : id.substring(0, 8) + "…";
    }

    private void configureTokenCombo(ComboBox<TokenDto> combo) {
        if (combo == null) return;
        combo.setButtonCell(createTokenCell(combo));
        combo.setCellFactory(lv -> createTokenCell(combo));
    }

    private ListCell<TokenDto> createTokenCell(ComboBox<TokenDto> combo) {
        return new ListCell<>() {
            private final Label title = new Label();
            private final Label meta = new Label();
            private final Label badge = new Label();
            private final VBox text = new VBox(1, title, meta);
            private final HBox root = new HBox(8, text, badge);

            {
                text.getStyleClass().add("dm-token-cell-text");
                badge.getStyleClass().add("dm-duplicate-token-badge");
                root.getStyleClass().add("dm-token-cell-root");
                root.setAlignment(Pos.CENTER_LEFT);
                badge.setManaged(false);
                badge.setVisible(false);
            }

            @Override
            protected void updateItem(TokenDto item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    pseudoClassStateChanged(DUPLICATE_PSEUDO_CLASS, false);
                    return;
                }
                title.setText(item.getName() == null || item.getName().isBlank() ? "—" : item.getName());
                meta.setText(buildTokenMetaLine(item));
                int duplicateCount = countDuplicateTokens(combo.getItems(), item);
                boolean duplicate = duplicateCount > 1;
                badge.setText(duplicate ? "×" + duplicateCount : "");
                badge.setVisible(duplicate);
                badge.setManaged(duplicate);
                pseudoClassStateChanged(DUPLICATE_PSEUDO_CLASS, duplicate);
                setText(null);
                setGraphic(root);
            }
        };
    }

    private ListCell<InitEntry> createInitiativeCell(ListView<InitEntry> list) {
        return new ListCell<>() {
            private final Label title = new Label();
            private final Label meta = new Label();
            private final Label badge = new Label();
            private final VBox text = new VBox(1, title, meta);
            private final HBox root = new HBox(8, text, badge);

            {
                text.getStyleClass().add("dm-token-cell-text");
                badge.getStyleClass().add("dm-duplicate-token-badge");
                root.getStyleClass().add("dm-token-cell-root");
                root.setAlignment(Pos.CENTER_LEFT);
                badge.setManaged(false);
                badge.setVisible(false);
            }

            @Override
            protected void updateItem(InitEntry item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    pseudoClassStateChanged(DUPLICATE_PSEUDO_CLASS, false);
                    return;
                }
                title.setText(item.name() == null || item.name().isBlank() ? "—" : item.name());
                meta.setText(formatInitiativeEntry(item));
                int duplicateCount = countDuplicateInitEntries(list.getItems(), item);
                boolean duplicate = duplicateCount > 1;
                badge.setText(duplicate ? "×" + duplicateCount : "");
                badge.setVisible(duplicate);
                badge.setManaged(duplicate);
                pseudoClassStateChanged(DUPLICATE_PSEUDO_CLASS, duplicate);
                setText(null);
                setGraphic(root);
            }
        };
    }

    private static final PseudoClass DUPLICATE_PSEUDO_CLASS = PseudoClass.getPseudoClass("duplicate");

    private static int countDuplicateTokens(Collection<TokenDto> tokens, TokenDto token) {
        if (token == null) return 0;
        String key = normalizeName(token.getName());
        if (key.isEmpty()) return 1;
        int count = 0;
        for (TokenDto t : tokens) {
            if (t != null && key.equals(normalizeName(t.getName()))) count++;
        }
        return count;
    }

    private static int countDuplicateInitEntries(Collection<InitEntry> entries, InitEntry entry) {
        if (entry == null) return 0;
        String key = normalizeName(entry.name());
        if (key.isEmpty()) return 1;
        int count = 0;
        for (InitEntry e : entries) {
            if (e != null && key.equals(normalizeName(e.name()))) count++;
        }
        return count;
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String buildTokenMetaLine(TokenDto t) {
        StringBuilder sb = new StringBuilder();
        if (t.getId() != null && !t.getId().isBlank()) {
            sb.append("#").append(shortId(t.getId()));
        }
        sb.append(" · HP ").append(t.getHp()).append('/').append(t.getMaxHp());
        if (t.getGridSize() > 1) {
            sb.append(" · ").append(t.getGridSize()).append('×').append(t.getGridSize());
        }
        String owner = t.getOwnerId() == null ? "NPC" : getPlayerName(t.getOwnerId());
        if (owner != null && !owner.isBlank()) {
            sb.append(" · ").append(owner);
        }
        sb.append(" · @ ").append(t.getCol()).append(',').append(t.getRow());
        return sb.toString();
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
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(6));
        h.setFillHeight(false);
        return h;
    }

    private static FlowPane flowRow(int hgap, int vgap, javafx.scene.Node... nodes) {
        FlowPane p = new FlowPane(hgap, vgap, nodes);
        p.setAlignment(Pos.CENTER_LEFT);
        p.setPadding(new Insets(6));
        p.setPrefWrapLength(430);
        return p;
    }
    private java.io.File resolveProjectUploadsDir(String relative) {
        try {
            java.nio.file.Path current = java.nio.file.Paths.get("").toAbsolutePath().normalize();
            while (current != null) {
                if (java.nio.file.Files.exists(current.resolve("settings.gradle")) || java.nio.file.Files.exists(current.resolve("settings.gradle.kts"))) {
                    java.io.File candidate = current.resolve(relative).toFile();
                    if (candidate.exists()) {
                        return candidate;
                    }
                    return candidate;
                }
                current = current.getParent();
            }
        } catch (Exception ignored) {
        }
        return new java.io.File(relative);
    }

}