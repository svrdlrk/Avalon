package com.avalon.dnd.mapeditor.ui;

import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

import java.util.Objects;
import java.util.function.BiConsumer;

public final class MapEditorKeyboardShortcuts {

    private MapEditorKeyboardShortcuts() {
    }

    public static void install(Scene scene,
                               Runnable saveProject,
                               Runnable loadProject,
                               Runnable undo,
                               Runnable redo,
                               Runnable duplicateSelected,
                               Runnable deleteSelected,
                               Runnable mergeSelectedWall,
                               Runnable splitSelectedWall,
                               BiConsumer<Integer, Integer> nudgeSelected) {
        Objects.requireNonNull(scene, "scene");
        Objects.requireNonNull(saveProject, "saveProject");
        Objects.requireNonNull(loadProject, "loadProject");
        Objects.requireNonNull(undo, "undo");
        Objects.requireNonNull(redo, "redo");
        Objects.requireNonNull(duplicateSelected, "duplicateSelected");
        Objects.requireNonNull(deleteSelected, "deleteSelected");
        Objects.requireNonNull(mergeSelectedWall, "mergeSelectedWall");
        Objects.requireNonNull(splitSelectedWall, "splitSelectedWall");
        Objects.requireNonNull(nudgeSelected, "nudgeSelected");

        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN), saveProject);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN), loadProject);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN), undo);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.Y, KeyCombination.SHORTCUT_DOWN), redo);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.D, KeyCombination.SHORTCUT_DOWN), duplicateSelected);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DELETE), deleteSelected);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN), mergeSelectedWall);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.M, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), splitSelectedWall);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.LEFT), () -> nudgeSelected.accept(-1, 0));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.RIGHT), () -> nudgeSelected.accept(1, 0));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.UP), () -> nudgeSelected.accept(0, -1));
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.DOWN), () -> nudgeSelected.accept(0, 1));
    }
}
