package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.WallLayer;
import com.avalon.dnd.mapeditor.model.WallLayer.EndpointSnap;
import com.avalon.dnd.mapeditor.model.WallPath;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;

import java.util.UUID;

public class WallBrushTool implements Tool {

    private WallPath activePath;
    private boolean historyRecorded;
    private int lastCol = Integer.MIN_VALUE;
    private int lastRow = Integer.MIN_VALUE;

    @Override
    public String getId() {
        return "wall";
    }

    @Override
    public String getDisplayName() {
        return "Wall";
    }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (state.getProject() == null) {
            return;
        }
        WallLayer layer = state.getProject().getWallLayer();
        if (layer == null || layer.isLocked() || !layer.isVisible()) {
            return;
        }

        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) {
            return;
        }

        state.recordHistory();
        activePath = new WallPath();
        activePath.setId(UUID.randomUUID().toString());
        activePath.setThickness(layer.getDefaultThickness());
        activePath.setBlocksMovement(layer.isDefaultBlocksMovement());
        activePath.setBlocksSight(layer.isDefaultBlocksSight());
        addSnappedPoint(layer, canvas, state, activePath, centerX(canvas, cell[0]), centerY(canvas, cell[1]));
        layer.addPath(activePath);
        historyRecorded = true;
        lastCol = cell[0];
        lastRow = cell[1];
        canvas.requestRender();
    }

    @Override
    public void onMouseDragged(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (activePath == null || state.getProject() == null) {
            return;
        }
        WallLayer layer = state.getProject().getWallLayer();
        if (layer == null || layer.isLocked() || !layer.isVisible()) {
            return;
        }
        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) {
            return;
        }
        if (cell[0] == lastCol && cell[1] == lastRow) {
            updateSnapPreview(layer, canvas, state, activePath, centerX(canvas, cell[0]), centerY(canvas, cell[1]));
            return;
        }
        if (!historyRecorded) {
            state.recordHistory();
            historyRecorded = true;
        }
        addSnappedPoint(layer, canvas, state, activePath, centerX(canvas, cell[0]), centerY(canvas, cell[1]));
        lastCol = cell[0];
        lastRow = cell[1];
        updateSnapPreview(layer, canvas, state, activePath, centerX(canvas, cell[0]), centerY(canvas, cell[1]));
        canvas.requestRender();
    }

    @Override
    public void onMouseReleased(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        activePath = null;
        historyRecorded = false;
        lastCol = Integer.MIN_VALUE;
        lastRow = Integer.MIN_VALUE;
        state.clearWallSnapIndicator();
        canvas.requestRender();
    }

    @Override
    public void onMouseMoved(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (activePath != null || state.getProject() == null) {
            return;
        }
        WallLayer layer = state.getProject().getWallLayer();
        if (layer == null || layer.isLocked() || !layer.isVisible()) {
            state.clearWallSnapIndicator();
            return;
        }
        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) {
            state.clearWallSnapIndicator();
            return;
        }
        updateSnapPreview(layer, canvas, state, null, centerX(canvas, cell[0]), centerY(canvas, cell[1]));
    }


    private void addSnappedPoint(WallLayer layer, MapEditorCanvas canvas, EditorState state, WallPath path, double x, double y) {
        double snappedX = x;
        double snappedY = y;
        double tolerance = Math.max(8.0, canvas.getGridCellSize() * 0.35) / Math.max(0.25, state.getZoom());
        EndpointSnap snap = layer.findNearestEndpoint(snappedX, snappedY, tolerance, path == null ? null : path.getId());
        if (snap != null) {
            snappedX = snap.x();
            snappedY = snap.y();
        }
        if (path != null) {
            path.addPoint(snappedX, snappedY);
        }
        state.setWallSnapIndicator(snappedX, snappedY);
    }

    private void updateSnapPreview(WallLayer layer, MapEditorCanvas canvas, EditorState state, WallPath path, double x, double y) {
        double snappedX = x;
        double snappedY = y;
        double tolerance = Math.max(8.0, canvas.getGridCellSize() * 0.35) / Math.max(0.25, state.getZoom());
        EndpointSnap snap = layer.findNearestEndpoint(snappedX, snappedY, tolerance, path == null ? null : path.getId());
        if (snap != null) {
            snappedX = snap.x();
            snappedY = snap.y();
        }
        state.setWallSnapIndicator(snappedX, snappedY);
        canvas.requestRender();
    }

    private double centerX(MapEditorCanvas canvas, int col) {
        return canvas.getGridOffsetX() + (col + 0.5) * canvas.getGridCellSize();
    }

    private double centerY(MapEditorCanvas canvas, int row) {
        return canvas.getGridOffsetY() + (row + 0.5) * canvas.getGridCellSize();
    }
}
