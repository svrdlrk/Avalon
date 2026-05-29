package com.avalon.dnd.dm.ui;

import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.function.Function;

/**
 * Generic cell factory for list / combo entries with duplicate badges.
 * Keeps MainStage focused on orchestration instead of cell rendering details.
 */
public final class DmDuplicateBadgeCellFactory {

    private static final PseudoClass DUPLICATE_PSEUDO_CLASS = PseudoClass.getPseudoClass("duplicate");

    private DmDuplicateBadgeCellFactory() {
    }

    public static <T> ListCell<T> create(ObservableList<T> items,
                                         Function<T, String> titleFormatter,
                                         Function<T, String> metaFormatter,
                                         Function<T, Integer> duplicateCounter) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(titleFormatter, "titleFormatter");
        Objects.requireNonNull(metaFormatter, "metaFormatter");
        Objects.requireNonNull(duplicateCounter, "duplicateCounter");

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
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    pseudoClassStateChanged(DUPLICATE_PSEUDO_CLASS, false);
                    return;
                }
                title.setText(titleFormatter.apply(item));
                meta.setText(metaFormatter.apply(item));
                int duplicateCount = duplicateCounter.apply(item);
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
}
