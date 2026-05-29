package com.avalon.dnd.dm.ui;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.util.StringConverter;

import java.util.function.Function;

/**
 * Small JavaFX control helpers extracted from the DM stage.
 */
public final class DmUiControls {

    private DmUiControls() {
    }

    public static <T> ComboBox<T> makeCombo(int width, Function<T, String> textSupplier) {
        ComboBox<T> combo = new ComboBox<>();
        combo.setPrefWidth(width);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(T value) {
                return textSupplier.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        });
        return combo;
    }

    public static ComboBox<JsonNode> makeJsonCombo(int width, Function<JsonNode, String> textSupplier) {
        return makeCombo(width, node -> node == null ? "" : textSupplier.apply(node));
    }

    public static Spinner<Integer> makeSpinner(int min, int max, int init) {
        Spinner<Integer> spinner = new Spinner<>(min, max, init);
        spinner.setEditable(true);
        spinner.setPrefWidth(72);
        return spinner;
    }

    public static HBox hbox(int gap, Node... nodes) {
        HBox box = new HBox(gap, nodes);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(6));
        box.setFillHeight(false);
        return box;
    }

    public static FlowPane flowRow(int hgap, int vgap, Node... nodes) {
        FlowPane pane = new FlowPane(hgap, vgap, nodes);
        pane.setAlignment(Pos.CENTER_LEFT);
        pane.setPadding(new Insets(6));
        pane.setPrefWrapLength(430);
        return pane;
    }


    public static <T> void configureDuplicateBadgeCombo(ComboBox<T> combo,
                                                        Function<T, String> textSupplier,
                                                        Function<T, String> metaSupplier,
                                                        Function<T, Integer> duplicateCountSupplier) {
        if (combo == null) {
            return;
        }
        combo.setButtonCell(DmDuplicateBadgeCellFactory.create(
                combo.getItems(),
                textSupplier,
                metaSupplier,
                duplicateCountSupplier
        ));
        combo.setCellFactory(lv -> DmDuplicateBadgeCellFactory.create(
                combo.getItems(),
                textSupplier,
                metaSupplier,
                duplicateCountSupplier
        ));
    }

    public static <T> T selected(ComboBox<T> box) {
        return box.getSelectionModel().getSelectedItem();
    }

    public static <T> String selectedId(ComboBox<T> box, java.util.function.Function<T, String> extractor) {
        T value = selected(box);
        return value == null ? null : extractor.apply(value);
    }

    public static <T> void selectById(ComboBox<T> box, String id, java.util.function.Function<T, String> extractor) {
        if (id == null) {
            return;
        }
        box.getItems().stream()
                .filter(item -> id.equals(extractor.apply(item)))
                .findFirst()
                .ifPresent(item -> box.getSelectionModel().select(item));
    }

    public static <T> void refreshItemsPreservingSelection(ComboBox<T> box,
                                                            java.util.Collection<T> items,
                                                            String selectedId,
                                                            Function<T, String> extractor) {
        if (box == null) {
            return;
        }
        box.getItems().setAll(items);
        selectById(box, selectedId, extractor);
    }
}
