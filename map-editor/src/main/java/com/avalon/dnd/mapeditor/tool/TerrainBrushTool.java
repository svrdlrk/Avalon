package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.TerrainCell;
import com.avalon.dnd.mapeditor.model.TerrainLayer;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TerrainBrushTool implements Tool {

    private boolean dragging;
    private boolean historyRecorded;
    private int lastCol = Integer.MIN_VALUE;
    private int lastRow = Integer.MIN_VALUE;
    private final Set<String> painted = new HashSet<>();

    @Override
    public String getId() {
        return "terrain";
    }

    @Override
    public String getDisplayName() {
        return "Terrain";
    }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        dragging = true;
        historyRecorded = false;
        painted.clear();
        paintAt(event, canvas, state);
    }

    @Override
    public void onMouseDragged(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (!dragging) {
            return;
        }
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
        if (cell == null) {
            return;
        }
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
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void paintAt(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) {
            return;
        }
        paintCell(cell[0], cell[1], canvas, state);
    }

    private void paintCell(int col, int row, MapEditorCanvas canvas, EditorState state) {
        if (state.getProject() == null) {
            return;
        }
        TerrainLayer terrain = state.getProject().getTerrainLayer();
        if (terrain == null || terrain.isLocked() || !terrain.isVisible()) {
            return;
        }

        String key = col + ":" + row + ":" + terrain.getPaintType();
        if (painted.contains(key)) {
            lastCol = col;
            lastRow = row;
            return;
        }

        if (!historyRecorded) {
            state.recordHistory();
            historyRecorded = true;
        }

        TerrainCell cell = terrain.findCellAt(col, row);
        if (cell == null) {
            cell = new TerrainCell();
            cell.setId(UUID.randomUUID().toString());
            cell.setCol(col);
            cell.setRow(row);
        }
        cell.setTerrainType(terrain.getPaintType());
        terrain.upsertCell(cell);

        painted.add(key);
        lastCol = col;
        lastRow = row;
        canvas.requestRender();
    }
}
