package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.model.WallPath;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import com.avalon.dnd.shared.GridConfig;
import javafx.scene.input.MouseEvent;

public class MoveTool implements Tool {

    private boolean dragging;
    private boolean historyRecorded;
    private int grabOffsetCol;
    private int grabOffsetRow;

    private boolean draggingWall;
    private boolean wallHistoryRecorded;
    private String wallPathId;
    private double lastWorldX;
    private double lastWorldY;

    @Override
    public String getId() { return "move"; }

    @Override
    public String getDisplayName() { return "Move"; }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        MapPlacement hit = canvas.findPlacementAt(event.getX(), event.getY());
        if (hit != null && !hit.isLocked() && !state.isLayerLocked(hit.getLayerId())) {
            state.selectPlacement(hit.getId());
            int[] cell = canvas.screenToCell(event.getX(), event.getY());
            if (cell == null) {
                reset();
                return;
            }

            dragging = true;
            historyRecorded = false;
            draggingWall = false;
            wallHistoryRecorded = false;
            grabOffsetCol = cell[0] - hit.getCol();
            grabOffsetRow = cell[1] - hit.getRow();
            canvas.requestRender();
            return;
        }

        WallPath wallHit = canvas.findWallPathAt(event.getX(), event.getY());
        WallPath selectedWall = state.selectedWallPath();
        if (wallHit == null) {
            wallHit = selectedWall;
        }
        if (wallHit == null || state.getProject() == null || state.getProject().getWallLayer() == null) {
            reset();
            return;
        }
        if (wallHit.isLocked() || state.getProject().getWallLayer().isLocked()) {
            reset();
            return;
        }

        state.selectWallPath(wallHit.getId());
        dragging = false;
        historyRecorded = false;
        draggingWall = true;
        wallHistoryRecorded = false;
        wallPathId = wallHit.getId();
        lastWorldX = canvas.screenToWorldX(event.getX());
        lastWorldY = canvas.screenToWorldY(event.getY());
        canvas.requestRender();
    }

    @Override
    public void onMouseDragged(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (!dragging && !draggingWall || state.getProject() == null) {
            return;
        }

        if (dragging) {
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
            return;
        }

        if (!draggingWall) {
            return;
        }

        WallPath selectedWall = state.selectedWallPath();
        if (selectedWall == null || !selectedWall.getId().equals(wallPathId)) {
            return;
        }

        double worldX = canvas.screenToWorldX(event.getX());
        double worldY = canvas.screenToWorldY(event.getY());
        if (state.isSnapToGrid()) {
            worldX = snapX(state, worldX);
            worldY = snapY(state, worldY);
        }

        double dx = worldX - lastWorldX;
        double dy = worldY - lastWorldY;
        if (dx == 0.0 && dy == 0.0) {
            return;
        }

        if (!wallHistoryRecorded) {
            state.recordHistory();
            wallHistoryRecorded = true;
        }

        selectedWall.translate(dx, dy);
        lastWorldX = worldX;
        lastWorldY = worldY;
        canvas.requestRender();
    }

    @Override
    public void onMouseReleased(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        reset();
    }

    private void reset() {
        dragging = false;
        historyRecorded = false;
        draggingWall = false;
        wallHistoryRecorded = false;
        wallPathId = null;
    }

    private double snapX(EditorState state, double worldX) {
        int cell = state.grid().getCellSize();
        int ox = state.grid().getOffsetX();
        double col = Math.round((worldX - ox) / cell);
        return ox + col * cell;
    }

    private double snapY(EditorState state, double worldY) {
        int cell = state.grid().getCellSize();
        int oy = state.grid().getOffsetY();
        double row = Math.round((worldY - oy) / cell);
        return oy + row * cell;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
