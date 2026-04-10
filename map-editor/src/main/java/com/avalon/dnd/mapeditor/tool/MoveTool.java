package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import com.avalon.dnd.shared.GridConfig;
import javafx.scene.input.MouseEvent;

public class MoveTool implements Tool {

    private boolean dragging;
    private boolean historyRecorded;
    private int grabOffsetCol;
    private int grabOffsetRow;

    @Override
    public String getId() { return "move"; }

    @Override
    public String getDisplayName() { return "Move"; }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        MapPlacement hit = canvas.findPlacementAt(event.getX(), event.getY());
        if (hit == null || hit.isLocked() || state.isLayerLocked(hit.getLayerId())) {
            dragging = false;
            historyRecorded = false;
            return;
        }

        state.selectPlacement(hit.getId());
        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) {
            dragging = false;
            historyRecorded = false;
            return;
        }

        dragging = true;
        historyRecorded = false;
        grabOffsetCol = cell[0] - hit.getCol();
        grabOffsetRow = cell[1] - hit.getRow();
        canvas.requestRender();
    }

    @Override
    public void onMouseDragged(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (!dragging || state.getProject() == null) {
            return;
        }
        MapPlacement selected = state.selectedPlacement();
        if (selected == null || selected.isLocked() || state.isLayerLocked(selected.getLayerId())) {
            return;
        }

        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) {
            return;
        }

        if (!historyRecorded) {
            state.recordHistory();
            historyRecorded = true;
        }

        GridConfig grid = state.grid();
        int newCol = clamp(cell[0] - grabOffsetCol, 0, Math.max(0, grid.getCols() - selected.effectiveWidth()));
        int newRow = clamp(cell[1] - grabOffsetRow, 0, Math.max(0, grid.getRows() - selected.effectiveHeight()));
        selected.setCol(newCol);
        selected.setRow(newRow);
        canvas.requestRender();
    }

    @Override
    public void onMouseReleased(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        dragging = false;
        historyRecorded = false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
