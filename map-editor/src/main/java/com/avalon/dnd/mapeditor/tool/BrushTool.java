package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.AssetDefinition;
import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BrushTool implements Tool {

    private boolean dragging;
    private boolean historyRecorded;
    private int lastCol = Integer.MIN_VALUE;
    private int lastRow = Integer.MIN_VALUE;
    private final Set<String> painted = new HashSet<>();

    @Override
    public String getId() { return "brush"; }

    @Override
    public String getDisplayName() { return "Brush"; }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        dragging = true;
        historyRecorded = false;
        painted.clear();
        paintAt(event, canvas, state);
    }

    @Override
    public void onMouseDragged(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (!dragging) return;
        paintLineTo(event, canvas, state);
    }

    @Override
    public void onMouseReleased(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        dragging = false;
        historyRecorded = false;
        painted.clear();
        lastCol = Integer.MIN_VALUE;
        lastRow = Integer.MIN_VALUE;
    }

    private void paintLineTo(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) return;
        if (lastCol == Integer.MIN_VALUE || lastRow == Integer.MIN_VALUE) {
            paintCell(cell[0], cell[1], canvas, state);
            return;
        }

        int x0 = lastCol;
        int y0 = lastRow;
        int x1 = cell[0];
        int y1 = cell[1];

        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            paintCell(x0, y0, canvas, state);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x0 += sx; }
            if (e2 < dx) { err += dx; y0 += sy; }
        }
    }

    private void paintAt(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) return;
        paintCell(cell[0], cell[1], canvas, state);
    }

    private void paintCell(int col, int row, MapEditorCanvas canvas, EditorState state) {
        AssetDefinition asset = state.selectedObjectAsset();
        if (asset == null || state.getProject() == null) return;

        MapLayer layer = state.selectedLayer();
        String layerId = layer == null ? null : layer.getId();
        if (state.isLayerLocked(layerId)) {
            return;
        }
        String key = col + ":" + row + ":" + layerId + ":" + asset.getId();
        if (painted.contains(key)) {
            lastCol = col;
            lastRow = row;
            return;
        }
        if (state.getProject().hasPlacementAt(layerId, col, row)) {
            lastCol = col;
            lastRow = row;
            return;
        }

        if (!historyRecorded) {
            state.recordHistory();
            historyRecorded = true;
        }

        MapPlacement placement = MapPlacement.fromAsset(UUID.randomUUID().toString(), asset, col, row, layerId);
        state.getProject().addPlacement(placement);
        state.selectPlacement(placement.getId());
        painted.add(key);
        lastCol = col;
        lastRow = row;
        canvas.requestRender();
    }
}
