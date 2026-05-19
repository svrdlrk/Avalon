package com.avalon.dnd.mapeditor.ui;

import javafx.scene.Scene;

import java.util.Objects;

public final class MapEditorStyles {
    private static final String STYLESHEET = "/com/avalon/dnd/mapeditor/ui/map-editor-theme.css";

    private MapEditorStyles() {
    }

    public static void apply(Scene scene) {
        if (scene == null) {
            return;
        }
        String stylesheet = Objects.requireNonNull(
                MapEditorStyles.class.getResource(STYLESHEET),
                "Missing map editor stylesheet"
        ).toExternalForm();
        if (!scene.getStylesheets().contains(stylesheet)) {
            scene.getStylesheets().add(stylesheet);
        }
    }
}
