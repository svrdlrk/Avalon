package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.ReferenceOverlay;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;

public class ReferenceOverlayTool implements Tool {

    private boolean dragging;
    private boolean historyRecorded;
    private double lastX;
    private double lastY;

    @Override
    public String getId() {
        return "reference";
    }

    @Override
    public String getDisplayName() {
        return "Reference";
    }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        ReferenceOverlay overlay = reference(state);
        if (overlay == null || !overlay.isVisible() || overlay.isLocked() || overlay.getImageUrl() == null || overlay.getImageUrl().isBlank()) {
            dragging = false;
            historyRecorded = false;
            return;
        }

        dragging = true;
        historyRecorded = false;
        lastX = event.getX();
        lastY = event.getY();
    }

    @Override
    public void onMouseDragged(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        ReferenceOverlay overlay = reference(state);
        if (!dragging || overlay == null || overlay.isLocked() || !overlay.isVisible() || overlay.getImageUrl() == null || overlay.getImageUrl().isBlank()) {
            return;
        }

        double dx = (event.getX() - lastX) / state.getZoom();
        double dy = (event.getY() - lastY) / state.getZoom();
        lastX = event.getX();
        lastY = event.getY();

        if (!historyRecorded) {
            state.recordHistory();
            historyRecorded = true;
        }

        overlay.setOffsetX(overlay.getOffsetX() + dx);
        overlay.setOffsetY(overlay.getOffsetY() + dy);
        canvas.requestRender();
    }

    @Override
    public void onMouseReleased(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        dragging = false;
        historyRecorded = false;
    }

    @Override
    public void onScroll(ScrollEvent event, MapEditorCanvas canvas, EditorState state) {
        ReferenceOverlay overlay = reference(state);
        if (overlay == null || !overlay.isVisible() || overlay.isLocked() || overlay.getImageUrl() == null || overlay.getImageUrl().isBlank()) {
            return;
        }

        state.recordHistory();
        if (event.isShiftDown()) {
            double delta = event.getDeltaY() > 0 ? 5.0 : -5.0;
            overlay.setRotation(overlay.getRotation() + delta);
        } else {
            double factor = event.getDeltaY() > 0 ? 1.05 : 1.0 / 1.05;
            overlay.setScale(overlay.getScale() * factor);
        }
        canvas.requestRender();
        event.consume();
    }

    private ReferenceOverlay reference(EditorState state) {
        return state.getProject() == null ? null : state.getProject().getReferenceOverlay();
    }
}
