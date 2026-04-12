package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

public interface Tool {

    String getId();

    String getDisplayName();

    default void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {}
    default void onMouseDragged(MouseEvent event, MapEditorCanvas canvas, EditorState state) {}
    default void onMouseReleased(MouseEvent event, MapEditorCanvas canvas, EditorState state) {}
    default void onMouseMoved(MouseEvent event, MapEditorCanvas canvas, EditorState state) {}
    default void onScroll(ScrollEvent event, MapEditorCanvas canvas, EditorState state) {}
}
