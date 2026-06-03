package com.avalon.dnd.dm.ui;

import com.avalon.dnd.dm.canvas.BattleMapCanvas;
import com.avalon.dnd.dm.config.RuntimeConfig;
import com.avalon.dnd.dm.net.ServerConnection;
import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.*;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.util.function.Supplier;

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
    private final DmCatalogFetcher catalogFetcher = new DmCatalogFetcher();

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
        DmKeyboardShortcuts.install(scene,
                () -> zoomMap(1.08),
                () -> zoomMap(0.92),
                () -> { setMapZoom(1.0); centerMapView(); },
                this::fitMapView,
                this::centerMapView,
                () -> { if (mapScrollPane != null && mapCanvas != null) mapCanvas.panBy(64, 0); },
                () -> { if (mapScrollPane != null && mapCanvas != null) mapCanvas.panBy(-64, 0); },
                () -> { if (mapScrollPane != null && mapCanvas != null) mapCanvas.panBy(0, 64); },
                () -> { if (mapScrollPane != null && mapCanvas != null) mapCanvas.panBy(0, -64); }
        );
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
            TextField dmSecretField = new TextField();
            dmSecretField.setPromptText("DM secret");
            TextField nameField = new TextField("DM");
            Button createBtn = new Button("Создать новую сессию");
            Button connectBtn = new Button("🔗 Подключиться");
            Label statusLbl = new Label("");

            createBtn.setOnAction(e -> {
                createBtn.setDisable(true); statusLbl.setText("Создание...");
                String serverUrl = RuntimeConfig.normalize(serverField.getText().trim());
                ServerConnection.getInstance().createSession(serverUrl, handle -> {
                    createBtn.setDisable(false);
                    if (handle != null) {
                        sessionField.setText(handle.id());
                        dmSecretField.setText(handle.dmSecret());
                        statusLbl.setText("✅ " + handle.id());
                    }
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
                loadCatalog(url, () -> ServerConnection.getInstance().connect(url, sid, nm, true, dmSecretField.getText().trim(),
                        v -> Platform.runLater(() ->
                                switchToBattleMap(playerClientField.getText().trim(), sid))));
            });

            VBox c = new VBox(8, new Label("ID сессии:"), sessionField, new Label("DM secret:"), dmSecretField, new Label("Имя DM:"), nameField,
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
                ServerConnection.getInstance().loadSession(url, sid, handle -> {
                    if (handle == null) { statusLbl.setText("❌ Ошибка"); return; }
                    currentServerUrl = url;
                    loadCatalog(url, () -> ServerConnection.getInstance().connect(url, handle.id(), name, true, handle.dmSecret(),
                            v -> Platform.runLater(() ->
                                    switchToBattleMap(playerClientField.getText().trim(), handle.id()))));
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
            FlowPane row = DmUiControls.flowRow(8, 8, refresh, loadBtn, deleteBtn, new Label("Имя DM:"), dmName);
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
        return DmBattleHeaderFactory.buildSavedSessionsTable();
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
        return DmBattleHeaderFactory.buildBattleOverview(
                playerClientBase,
                sessionId,
                sessionSummaryLabel,
                sessionCountsLabel,
                () -> zoomMap(0.88),
                () -> zoomMap(1.12),
                this::fitMapView,
                this::centerMapView,
                this::copyToClipboard,
                () -> playerClientBase == null || playerClientBase.isBlank()
                        ? RuntimeConfig.defaultPlayerClientUrl()
                        : playerClientBase
        );
    }

    private VBox buildSidebarIntro(String playerClientBase, String sessionId) {
        return DmBattleHeaderFactory.buildSidebarIntro(playerClientBase, sessionId);
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
        sessionSummaryLabel.setText(DmUiFormatters.sessionSummary(currentSessionId));
        int players = ClientState.getInstance().getPlayers().size();
        int tokens = ClientState.getInstance().getTokens().size();
        int objects = ClientState.getInstance().getObjects().size();
        sessionCountsLabel.setText(DmUiFormatters.sessionCounts(players, tokens, objects));
    }

    // ================================================================ Catalog

    private void loadCatalog(String serverUrl, Runnable onDone) {
        String baseUrl = RuntimeConfig.normalize(serverUrl);
        Thread loader = new Thread(() -> {
            try {
                DmCatalogFetcher.CatalogData data = catalogFetcher.fetch(baseUrl);
                Platform.runLater(() -> {
                    tokenCatalog.clear();
                    tokenCatalog.addAll(data.tokens());
                    objectCatalog.clear();
                    objectCatalog.addAll(data.objects());
                    onDone.run();
                });
            } catch (Exception ex) {
                System.err.println("Catalog: " + ex.getMessage());
                Platform.runLater(onDone);
            }
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
        mapScrollPane.setCache(false);
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
        sidebarScroll.setCache(false);
        sidebarScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        sidebarScroll.getStyleClass().add("dm-sidebar-scroll");

        SplitPane workspace = new SplitPane(mapScrollPane, sidebarScroll);
        workspace.setDividerPositions(0.80);
        workspace.getStyleClass().add("dm-workspace");
        workspace.setCache(false);

        BorderPane root = new BorderPane();
        root.setTop(header);
        root.setCenter(workspace);
        root.getStyleClass().add("dm-root");
        root.setCache(false);
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
        VBox c = new VBox(10, idLbl, DmUiControls.hbox(8, new Label("Название:"), nameField, saveBtn), auto, saveStatus);
        c.setPadding(new Insets(10)); tab.setContent(c); return tab;
    }

    // ================================================================ Tab: 🗡 Токены

    private Tab buildTokenTab() {
        Tab tab = new Tab("🗡 Токены");
        ComboBox<JsonNode> catCombo = DmUiControls.makeJsonCombo(185, n ->
                n.path("name").asText() + " [" + n.path("size").asText() + "]");
        catCombo.getItems().addAll(tokenCatalog);
        TextField nameField = new TextField("Гоблин"); nameField.setPrefWidth(110);
        Spinner<Integer> hpSpin = DmUiControls.makeSpinner(1, 999, 20);
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

        tokenActionsCombo = DmUiControls.makeCombo(320, t -> DmUiFormatters.formatTokenLabel(t, this::getPlayerName));
        DmUiControls.configureDuplicateBadgeCombo(tokenActionsCombo,
                item -> item.getName() == null || item.getName().isBlank() ? "—" : item.getName(),
                item -> DmUiFormatters.buildTokenMetaLine(item, this::getPlayerName),
                item -> DmUiFormatters.countDuplicateTokens(tokenActionsCombo.getItems(), item));

        playerAssignCombo = DmUiControls.makeCombo(180,
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

        FlowPane r1 = DmUiControls.flowRow(8, 8, new Label("Каталог:"), catCombo, browseBtn, new Label("Имя:"), nameField,
                new Label("HP:"), hpSpin, new Label("Размер:"), sizeLbl, addBtn);
        FlowPane r2 = DmUiControls.flowRow(8, 8, new Label("Токен:"), tokenActionsCombo,
                new Label("Игрок:"), playerAssignCombo, assignBtn, unassignBtn, removeBtn);
        tab.setContent(new VBox(r1, new Separator(), r2)); return tab;
    }

    private String getPlayerName(String ownerId) {
        PlayerDto p = ClientState.getInstance().getPlayers().get(ownerId);
        return p != null ? p.getName() : DmUiFormatters.shortId(ownerId);
    }

    // ================================================================ Tab: 🧱 Объекты

    private Tab buildObjectTab() {
        Tab tab = new Tab("🧱 Объекты");
        ComboBox<JsonNode> catCombo = DmUiControls.makeJsonCombo(210, n ->
                n.path("name").asText() + " [" + n.path("category").asText() + "]");
        catCombo.getItems().addAll(objectCatalog);
        var g0 = ClientState.getInstance().getGrid();
        objectColSpinner = DmUiControls.makeSpinner(0, Math.max(0, g0.getCols() - 1), 0);
        objectRowSpinner = DmUiControls.makeSpinner(0, Math.max(0, g0.getRows() - 1), 0);
        Spinner<Integer> wSpin = DmUiControls.makeSpinner(1, 10, 1);
        Spinner<Integer> hSpin = DmUiControls.makeSpinner(1, 10, 1);
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
        objectRemoveCombo = DmUiControls.makeCombo(210, o -> o == null ? "" :
                o.getType() + " @(" + o.getCol() + "," + o.getRow() + ")");
        Button removeBtn = new Button("Удалить"); removeBtn.setOnAction(e -> removeObject());
        Button browseBtn = new Button("Каталог");
        browseBtn.setOnAction(e -> AssetBrowserWindow.showObjectBrowser(stage, objectCatalog, asset -> {
            if (asset != null) {
                catCombo.getSelectionModel().select(asset);
            }
        }));

        FlowPane r1 = DmUiControls.flowRow(8, 8, new Label("Тип:"), catCombo, browseBtn, new Label("col:"), objectColSpinner,
                new Label("row:"), objectRowSpinner, new Label("W:"), wSpin,
                new Label("H:"), hSpin, prevLbl, placeBtn);
        FlowPane r2 = DmUiControls.flowRow(8, 8, new Label("Удалить:"), objectRemoveCombo, removeBtn);
        tab.setContent(new VBox(r1, new Separator(), r2)); return tab;
    }

    // ================================================================ Tab: 🗺 Карта

    private Tab buildGridTab(String sessionId) {
        Tab tab = new Tab("🗺 Карта");
        var g0 = ClientState.getInstance().getGrid();
        Spinner<Integer> cols = DmUiControls.makeSpinner(4, 60, g0.getCols());
        Spinner<Integer> rows = DmUiControls.makeSpinner(4, 60, g0.getRows());
        Spinner<Integer> cell = DmUiControls.makeSpinner(24, 128, g0.getCellSize());
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
            java.io.File defaultDir = DmUiPaths.resolveProjectUploadsDir("uploads/maps/finished");
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
                        if (ok) {
                            javafx.application.Platform.runLater(this::fitMapView);
                        }
                    }
            );
        });
        FlowPane row = DmUiControls.flowRow(8, 8, new Label("cols:"), cols, new Label("rows:"), rows,
                new Label("cell px:"), cell, applyBtn, new Separator(), uploadBtn, importMapBtn, upSt);
        tab.setContent(row);
        ToggleButton fogBtn = new ToggleButton("🌫 Туман: ВЫКЛ");
        fogBtn.getStyleClass().add("dm-nav-toggle");
        Label fogStatus = new Label("");

        fogBtn.setOnAction(e -> {
            boolean on = fogBtn.isSelected();
            fogBtn.setText(on ? "🌫 Туман: ВКЛ" : "🌫 Туман: ВЫКЛ");
            fogStatus.setText("...");
            ServerConnection.getInstance().setFogEnabled(
                    currentServerUrl, sessionId, on,
                    ok -> fogStatus.setText(ok
                            ? (on ? "✅ Туман включён" : "☀ Туман выключен")
                            : "❌ Ошибка")
            );
        });

        FlowPane gridRow = DmUiControls.flowRow(8, 8,
                new Label("cols:"), cols, new Label("rows:"), rows,
                new Label("cell px:"), cell, applyBtn,
                new Separator(), uploadBtn, importMapBtn, upSt);
        FlowPane fogRow = DmUiControls.flowRow(8, 8, fogBtn, fogStatus);

        tab.setContent(new VBox(gridRow, new Separator(), fogRow));
        return tab;
    }

    // ================================================================ Tab: ❤ HP

    private Tab buildHpTab() {
        Tab tab = new Tab("❤ HP");
        ComboBox<TokenDto> hpCombo = DmUiControls.makeCombo(340,
                t -> DmUiFormatters.formatTokenLabel(t, this::getPlayerName));
        DmUiControls.configureDuplicateBadgeCombo(hpCombo,
                item -> item.getName() == null || item.getName().isBlank() ? "—" : item.getName(),
                item -> DmUiFormatters.buildTokenMetaLine(item, this::getPlayerName),
                item -> DmUiFormatters.countDuplicateTokens(hpCombo.getItems(), item));
        Spinner<Integer> hpSpin  = DmUiControls.makeSpinner(0, 9999, 20);
        Spinner<Integer> maxSpin = DmUiControls.makeSpinner(1, 9999, 20);
        Label curLbl = new Label("Выбран: —");

        hpCombo.setOnAction(e -> {
            TokenDto t = hpCombo.getSelectionModel().getSelectedItem(); if (t == null) return;
            hpSpin.getValueFactory().setValue(t.getHp()); maxSpin.getValueFactory().setValue(t.getMaxHp());
            curLbl.setText("Выбран: " + DmUiFormatters.formatTokenLabel(t, this::getPlayerName));
        });

        Button dmg  = new Button("Урон"); dmg.setStyle("-fx-base:#c0392b;");
        Spinner<Integer> delta = DmUiControls.makeSpinner(1, 999, 5);
        Button heal = new Button("Лечение"); heal.setStyle("-fx-base:#27ae60;");
        Button set  = new Button("Применить HP");
        Button kill = new Button("Обнулить"); kill.setStyle("-fx-base:#7f8c8d;");

        dmg.setOnAction(e -> { TokenDto t = DmUiControls.selected(hpCombo); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(), Math.max(0, t.getHp() - delta.getValue()), t.getMaxHp()); });
        heal.setOnAction(e -> { TokenDto t = DmUiControls.selected(hpCombo); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(), Math.min(t.getMaxHp(), t.getHp() + delta.getValue()), t.getMaxHp()); });
        set.setOnAction(e -> { TokenDto t = DmUiControls.selected(hpCombo); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(), hpSpin.getValue(), maxSpin.getValue()); });
        kill.setOnAction(e -> { TokenDto t = DmUiControls.selected(hpCombo); if (t == null) return;
            ServerConnection.getInstance().updateTokenHp(t.getId(), 0, t.getMaxHp()); });

        hpRefreshListener = () -> {
            String keep = DmUiControls.selectedId(hpCombo, TokenDto::getId);
            hpCombo.getItems().setAll(ClientState.getInstance().getTokens().values());
            DmUiControls.selectById(hpCombo, keep, TokenDto::getId);
            TokenDto s = DmUiControls.selected(hpCombo);
            if (s != null) curLbl.setText("Выбран: " + DmUiFormatters.formatTokenLabel(s, this::getPlayerName));
        };
        ClientState.getInstance().addChangeListener(hpRefreshListener);

        FlowPane r1 = DmUiControls.flowRow(8, 8, new Label("Токен:"), hpCombo, curLbl);
        FlowPane r2 = DmUiControls.flowRow(8, 8, new Label("Урон/Лечение:"), dmg, delta, heal,
                new Separator(), new Label("HP:"), hpSpin, new Label("/ max:"), maxSpin, set, kill);
        tab.setContent(new VBox(r1, new Separator(), r2)); return tab;
    }

    // ================================================================ Tab: 🎲 Инициатива

    private Tab buildInitiativeTab() {
        Tab tab = new Tab("🎲 Инициатива");
        ListView<InitEntry> listView = new ListView<>();
        listView.setPrefHeight(130);
        listView.setCellFactory(lv -> DmDuplicateBadgeCellFactory.create(
                listView.getItems(),
                item -> item.name() == null || item.name().isBlank() ? "—" : item.name(),
                item -> DmUiFormatters.formatInitiativeEntry(item, this::getPlayerName, id -> ClientState.getInstance().getTokens().get(id)),
                item -> DmUiFormatters.countDuplicateInitEntries(listView.getItems(), item)
        ));
        ComboBox<TokenDto> addCombo = DmUiControls.makeCombo(340, t -> DmUiFormatters.formatTokenLabel(t, this::getPlayerName));
        DmUiControls.configureDuplicateBadgeCombo(addCombo,
                item -> item.getName() == null || item.getName().isBlank() ? "—" : item.getName(),
                item -> DmUiFormatters.buildTokenMetaLine(item, this::getPlayerName),
                item -> DmUiFormatters.countDuplicateTokens(addCombo.getItems(), item));
        Spinner<Integer> iniSpin = DmUiControls.makeSpinner(1, 30, 10);
        Button addBtn     = new Button("Добавить в инициативу");
        Button removeBtn  = new Button("Удалить");
        Button clearBtn   = new Button("Сбросить");
        Button nextBtn    = new Button("Следующий ход");
        Button publishBtn = new Button("Публиковать");
        publishBtn.setStyle("-fx-base: #2980b9;");
        Label curTurnLbl = new Label("Ход: —"); curTurnLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

        initiativeRefreshListener = () -> {
            String keep = DmUiControls.selectedId(addCombo, TokenDto::getId);
            addCombo.getItems().setAll(ClientState.getInstance().getTokens().values());
            DmUiControls.selectById(addCombo, keep, TokenDto::getId);
            listView.refresh();
        };
        ClientState.getInstance().addChangeListener(initiativeRefreshListener);

        addBtn.setOnAction(e -> {
            TokenDto t = DmUiControls.selected(addCombo); if (t == null) return;
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
            curTurnLbl.setText("Ход: " + DmUiFormatters.formatInitiativeEntry(iniQueue.get(iniIndex), this::getPlayerName, id -> ClientState.getInstance().getTokens().get(id)));
            publishInitiative();
        });
        publishBtn.setOnAction(e -> publishInitiative());

        FlowPane r1 = DmUiControls.flowRow(8, 8, new Label("Токен:"), addCombo, new Label("Иниц:"), iniSpin, addBtn, removeBtn);
        FlowPane r2 = DmUiControls.flowRow(8, 8, nextBtn, clearBtn, publishBtn, curTurnLbl);
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


    // ================================================================ Refresh

    private void refreshSelectors() {
        String keepToken  = DmUiControls.selectedId(tokenActionsCombo, TokenDto::getId);
        String keepPlayer = DmUiControls.selectedId(playerAssignCombo, PlayerDto::getId);
        String keepObj    = DmUiControls.selectedId(objectRemoveCombo, MapObjectDto::getId);

        DmUiControls.refreshItemsPreservingSelection(tokenActionsCombo,
                ClientState.getInstance().getTokens().values(),
                keepToken,
                TokenDto::getId);

        DmUiControls.refreshItemsPreservingSelection(playerAssignCombo,
                ClientState.getInstance().getPlayers().values().stream()
                        .filter(p -> "PLAYER".equalsIgnoreCase(p.getRole()))
                        .toList(),
                keepPlayer,
                PlayerDto::getId);

        DmUiControls.refreshItemsPreservingSelection(objectRemoveCombo,
                ClientState.getInstance().getObjects().values(),
                keepObj,
                MapObjectDto::getId);

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
            String ip = DmUiFormatters.firstCatalogImageUrl(ce);
            if (ip != null && !ip.isBlank()) img = DmUiFormatters.normalizeCatalogImageUrl(ip);
        }
        PlayerDto p = playerAssignCombo.getSelectionModel().getSelectedItem();
        String ownerId = (p != null) ? p.getId() : null;
        ServerConnection.getInstance().createToken(name, col, row, hp, hp, gs, img, ownerId, dayVision, nightVision);
    }

    private void placeObject(JsonNode ce, int col, int row, int w, int h) {
        String type = "wall"; String img = null;
        if (ce != null) {
            type = ce.path("id").asText("wall");
            String ip = DmUiFormatters.firstCatalogImageUrl(ce);
            if (ip != null && !ip.isBlank()) img = DmUiFormatters.normalizeCatalogImageUrl(ip);
        }
        ServerConnection.getInstance().send("/map.object.create",
                new MapObjectCreateRequest(type, col, row, w, h, 1, img));
    }

    private void assignToken() {
        TokenDto t = DmUiControls.selected(tokenActionsCombo); PlayerDto p = DmUiControls.selected(playerAssignCombo);
        if (t == null || p == null) return;
        TokenAssignRequest req = new TokenAssignRequest();
        req.setTokenId(t.getId()); req.setOwnerId(p.getId());
        ServerConnection.getInstance().send("/token.assign", req);
    }

    private void unassignToken() {
        TokenDto t = DmUiControls.selected(tokenActionsCombo); if (t == null) return;
        TokenAssignRequest req = new TokenAssignRequest();
        req.setTokenId(t.getId()); req.setOwnerId(null);
        ServerConnection.getInstance().send("/token.assign", req);
    }

    private void removeToken() {
        TokenDto t = DmUiControls.selected(tokenActionsCombo); if (t == null) return;
        TokenRemoveEvent ev = new TokenRemoveEvent(); ev.setTokenId(t.getId());
        ServerConnection.getInstance().send("/token.remove", ev);
    }

    private void removeObject() {
        MapObjectDto o = DmUiControls.selected(objectRemoveCombo); if (o == null) return;
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

}
