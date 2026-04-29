package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.shared.MicroLocationDto;
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

    private boolean draggingMicroLocation;
    private boolean microLocationHistoryRecorded;
    private String microLocationId;
    private MapEditorCanvas.MicroLocationHandle microLocationHandle = MapEditorCanvas.MicroLocationHandle.NONE;
    private int microLocationStartCol;
    private int microLocationStartRow;
    private int microLocationStartWidth;
    private int microLocationStartHeight;
    private int microLocationGrabOffsetCol;
    private int microLocationGrabOffsetRow;

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

            resetMicroLocationDrag();
            dragging = true;
            historyRecorded = false;
            draggingWall = false;
            wallHistoryRecorded = false;
            grabOffsetCol = cell[0] - hit.getCol();
            grabOffsetRow = cell[1] - hit.getRow();
            canvas.requestRender();
            return;
        }

        MapEditorCanvas.MicroLocationHit microHit = canvas.findMicroLocationHitAt(event.getX(), event.getY());
        if (microHit != null && microHit.zone != null && !microHit.zone.isLocked()) {
            state.setSelectedMicroLocationId(microHit.zone.getId());
            if (microHit.handle != MapEditorCanvas.MicroLocationHandle.NONE) {
                startMicroLocationResize(microHit.zone, microHit.handle);
            } else {
                startMicroLocationDrag(microHit.zone, canvas, event);
            }
            dragging = false;
            historyRecorded = false;
            draggingWall = false;
            wallHistoryRecorded = false;
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
        resetMicroLocationDrag();
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
        if (state.getProject() == null) {
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

        if (draggingMicroLocation) {
            MicroLocationDto zone = state.getProject().findMicroLocation(microLocationId).orElse(null);
            if (zone == null || zone.isLocked()) {
                return;
            }

            if (!microLocationHistoryRecorded) {
                state.recordHistory();
                microLocationHistoryRecorded = true;
            }

            applyMicroLocationDrag(zone, canvas, event, state);
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
        resetMicroLocationDrag();
    }

    private void resetMicroLocationDrag() {
        draggingMicroLocation = false;
        microLocationHistoryRecorded = false;
        microLocationId = null;
        microLocationHandle = MapEditorCanvas.MicroLocationHandle.NONE;
        microLocationStartCol = 0;
        microLocationStartRow = 0;
        microLocationStartWidth = 0;
        microLocationStartHeight = 0;
        microLocationGrabOffsetCol = 0;
        microLocationGrabOffsetRow = 0;
    }

    private void startMicroLocationDrag(MicroLocationDto zone, MapEditorCanvas canvas, MouseEvent event) {
        if (zone == null) return;
        draggingMicroLocation = true;
        microLocationId = zone.getId();
        microLocationHandle = MapEditorCanvas.MicroLocationHandle.MOVE;
        microLocationStartCol = zone.getCol();
        microLocationStartRow = zone.getRow();
        microLocationStartWidth = Math.max(1, zone.getWidth());
        microLocationStartHeight = Math.max(1, zone.getHeight());
        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell != null) {
            microLocationGrabOffsetCol = cell[0] - zone.getCol();
            microLocationGrabOffsetRow = cell[1] - zone.getRow();
        }
    }

    private void startMicroLocationResize(MicroLocationDto zone, MapEditorCanvas.MicroLocationHandle handle) {
        if (zone == null) return;
        draggingMicroLocation = true;
        microLocationId = zone.getId();
        microLocationHandle = handle == null ? MapEditorCanvas.MicroLocationHandle.NONE : handle;
        microLocationStartCol = zone.getCol();
        microLocationStartRow = zone.getRow();
        microLocationStartWidth = Math.max(1, zone.getWidth());
        microLocationStartHeight = Math.max(1, zone.getHeight());
    }

    private void applyMicroLocationDrag(MicroLocationDto zone, MapEditorCanvas canvas, MouseEvent event, EditorState state) {
        GridConfig grid = state.grid();
        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) return;

        switch (microLocationHandle) {
            case MOVE -> {
                int newCol = clamp(cell[0] - microLocationGrabOffsetCol, 0, Math.max(0, grid.getCols() - microLocationStartWidth));
                int newRow = clamp(cell[1] - microLocationGrabOffsetRow, 0, Math.max(0, grid.getRows() - microLocationStartHeight));
                zone.setCol(newCol);
                zone.setRow(newRow);
            }
            case NW, N, NE, E, SE, S, SW, W, NONE -> {
                int anchorCol = microLocationStartCol;
                int anchorRow = microLocationStartRow;
                int anchorRight = microLocationStartCol + microLocationStartWidth;
                int anchorBottom = microLocationStartRow + microLocationStartHeight;

                int newCol = microLocationStartCol;
                int newRow = microLocationStartRow;
                int newWidth = microLocationStartWidth;
                int newHeight = microLocationStartHeight;

                if (microLocationHandle == MapEditorCanvas.MicroLocationHandle.W || microLocationHandle == MapEditorCanvas.MicroLocationHandle.NW || microLocationHandle == MapEditorCanvas.MicroLocationHandle.SW) {
                    newCol = clamp(cell[0], 0, anchorRight - 1);
                    newWidth = anchorRight - newCol;
                }
                if (microLocationHandle == MapEditorCanvas.MicroLocationHandle.E || microLocationHandle == MapEditorCanvas.MicroLocationHandle.NE || microLocationHandle == MapEditorCanvas.MicroLocationHandle.SE) {
                    newWidth = clamp(cell[0] - anchorCol + 1, 1, grid.getCols() - anchorCol);
                }
                if (microLocationHandle == MapEditorCanvas.MicroLocationHandle.N || microLocationHandle == MapEditorCanvas.MicroLocationHandle.NW || microLocationHandle == MapEditorCanvas.MicroLocationHandle.NE) {
                    newRow = clamp(cell[1], 0, anchorBottom - 1);
                    newHeight = anchorBottom - newRow;
                }
                if (microLocationHandle == MapEditorCanvas.MicroLocationHandle.S || microLocationHandle == MapEditorCanvas.MicroLocationHandle.SW || microLocationHandle == MapEditorCanvas.MicroLocationHandle.SE) {
                    newHeight = clamp(cell[1] - anchorRow + 1, 1, grid.getRows() - anchorRow);
                }

                newWidth = Math.max(1, Math.min(newWidth, grid.getCols() - newCol));
                newHeight = Math.max(1, Math.min(newHeight, grid.getRows() - newRow));
                zone.setCol(newCol);
                zone.setRow(newRow);
                zone.setWidth(newWidth);
                zone.setHeight(newHeight);
            }
        }
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
