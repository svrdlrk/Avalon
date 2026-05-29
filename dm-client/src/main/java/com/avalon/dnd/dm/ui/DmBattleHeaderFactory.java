package com.avalon.dnd.dm.ui;

import com.avalon.dnd.dm.config.RuntimeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Pure UI factory for the DM session header and connection table.
 * Keeps MainStage focused on orchestration and event wiring.
 */
public final class DmBattleHeaderFactory {

    private DmBattleHeaderFactory() {
    }

    public static VBox buildBattleOverview(String playerClientBase,
                                           String sessionId,
                                           Label sessionSummaryLabel,
                                           Label sessionCountsLabel,
                                           Runnable zoomOutAction,
                                           Runnable zoomInAction,
                                           Runnable fitAction,
                                           Runnable centerAction,
                                           Consumer<String> copyToClipboard,
                                           Supplier<String> playerUrlSupplier) {
        Label title = new Label("Battle Control");
        title.getStyleClass().add("dm-title");

        Label subtitle = new Label(resolvePlayerClientBase(playerClientBase) + "  →  " + sessionId);
        subtitle.getStyleClass().add("dm-meta");
        subtitle.setWrapText(true);

        Button zoomOutBtn = new Button("Zoom -");
        zoomOutBtn.setOnAction(e -> zoomOutAction.run());

        Button zoomInBtn = new Button("Zoom +");
        zoomInBtn.setOnAction(e -> zoomInAction.run());

        Button fitBtn = new Button("Fit");
        fitBtn.setOnAction(e -> fitAction.run());

        Button centerBtn = new Button("Center map");
        centerBtn.setOnAction(e -> centerAction.run());

        Button copySessionBtn = new Button("Copy session");
        copySessionBtn.getStyleClass().add("primary-action");
        copySessionBtn.setOnAction(e -> copyToClipboard.accept(sessionId));

        Button copyPlayerBtn = new Button("Copy player URL");
        copyPlayerBtn.setOnAction(e -> copyToClipboard.accept(resolvePlayerClientBase(playerUrlSupplier == null ? null : playerUrlSupplier.get())));

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

    public static VBox buildSidebarIntro(String playerClientBase, String sessionId) {
        Label title = new Label("Session orbit");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: 800; -fx-text-fill: #f8fafc;");

        Label hint = new Label("Use the rail below to manage tokens, objects, map layers, HP and initiative. Drag the map to pan, wheel to zoom.");
        hint.setWrapText(true);
        hint.setStyle("-fx-text-fill: #9fb0c8; -fx-font-size: 12px; -fx-line-spacing: 2px;");

        Label meta = new Label("Session " + DmUiFormatters.shortId(sessionId) + " · " + resolvePlayerClientBase(playerClientBase));
        meta.setWrapText(true);
        meta.setStyle("-fx-text-fill: #c9d6ea; -fx-font-size: 11px; -fx-font-family: Consolas, 'SFMono-Regular', monospace;");

        VBox shell = new VBox(8, title, hint, meta);
        shell.setPadding(new Insets(12));
        shell.getStyleClass().add("dm-summary-shell");
        return shell;
    }

    public static TableView<JsonNode> buildSavedSessionsTable() {
        TableView<JsonNode> table = new TableView<>();
        table.setPrefHeight(200);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<JsonNode, String> nameColumn = new TableColumn<>("Название");
        nameColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path("displayName").asText()));

        TableColumn<JsonNode, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path("sessionId").asText()));

        TableColumn<JsonNode, String> dateColumn = new TableColumn<>("Дата");
        dateColumn.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().path("savedAt").asText().replace("T", " ")));

        table.getColumns().addAll(nameColumn, idColumn, dateColumn);
        return table;
    }

    private static String resolvePlayerClientBase(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? RuntimeConfig.defaultPlayerClientUrl() : normalized;
    }
}
