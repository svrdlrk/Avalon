package com.avalon.dnd.dm.ui;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.Objects;

public final class DmKeyboardShortcuts {

    private DmKeyboardShortcuts() {
    }

    public static void install(Scene scene,
                               Runnable zoomIn,
                               Runnable zoomOut,
                               Runnable resetZoom,
                               Runnable fitView,
                               Runnable centerView,
                               Runnable panLeft,
                               Runnable panRight,
                               Runnable panUp,
                               Runnable panDown) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(zoomIn, "zoomIn");
        Objects.requireNonNull(zoomOut, "zoomOut");
        Objects.requireNonNull(resetZoom, "resetZoom");
        Objects.requireNonNull(fitView, "fitView");
        Objects.requireNonNull(centerView, "centerView");
        Objects.requireNonNull(panLeft, "panLeft");
        Objects.requireNonNull(panRight, "panRight");
        Objects.requireNonNull(panUp, "panUp");
        Objects.requireNonNull(panDown, "panDown");

        scene.setOnKeyPressed(event -> handle(event, zoomIn, zoomOut, resetZoom, fitView, centerView, panLeft, panRight, panUp, panDown));
    }

    private static void handle(KeyEvent event,
                               Runnable zoomIn,
                               Runnable zoomOut,
                               Runnable resetZoom,
                               Runnable fitView,
                               Runnable centerView,
                               Runnable panLeft,
                               Runnable panRight,
                               Runnable panUp,
                               Runnable panDown) {
        switch (event.getCode()) {
            case EQUALS, PLUS -> { zoomIn.run(); event.consume(); }
            case MINUS, SUBTRACT -> { zoomOut.run(); event.consume(); }
            case DIGIT0 -> { resetZoom.run(); centerView.run(); event.consume(); }
            case F -> { fitView.run(); event.consume(); }
            case C -> { centerView.run(); event.consume(); }
            case LEFT -> { panLeft.run(); event.consume(); }
            case RIGHT -> { panRight.run(); event.consume(); }
            case UP -> { panUp.run(); event.consume(); }
            case DOWN -> { panDown.run(); event.consume(); }
            default -> { }
        }
    }
}
