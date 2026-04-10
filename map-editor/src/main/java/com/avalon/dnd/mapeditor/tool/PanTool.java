package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;

public class PanTool implements Tool {

    private double lastX;
    private double lastY;
    private boolean dragging;

    @Override
    public String getId() { return "pan"; }

    @Override
    public String getDisplayName() { return "Pan"; }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        dragging = true;
        lastX = event.getX();
        lastY = event.getY();
    }

    @Override
    public void onMouseDragged(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (!dragging) return;
        double dx = event.getX() - lastX;
        double dy = event.getY() - lastY;
        state.pan(dx, dy);
        lastX = event.getX();
        lastY = event.getY();
        canvas.requestRender();
    }

    @Override
    public void onMouseReleased(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        dragging = false;
    }
}
