
package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.WallLayer;
import com.avalon.dnd.mapeditor.model.WallLayer.EndpointSnap;
import com.avalon.dnd.mapeditor.model.WallPath;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;

public class WallEditTool implements Tool {

    private boolean dragging;
    private boolean historyRecorded;
    private String activePathId;
    private int activeVertexIndex = -1;

    @Override
    public String getId() {
        return "wallEdit";
    }

    @Override
    public String getDisplayName() {
        return "Wall edit";
    }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (state.getProject() == null) {
            return;
        }
        WallLayer wallLayer = state.getProject().getWallLayer();
        if (wallLayer == null || !wallLayer.isVisible() || wallLayer.isLocked()) {
            return;
        }

        double worldX = canvas.screenToWorldX(event.getX());
        double worldY = canvas.screenToWorldY(event.getY());
        double tolerance = 8.0 / Math.max(0.25, state.getZoom());

        WallPath selected = state.selectedWallPath();
        WallPath hit = canvas.findWallPathAt(event.getX(), event.getY());

        if (hit != null && (selected == null || !hit.getId().equals(selected.getId()))) {
            state.selectWallPath(hit.getId());
            selected = hit;
        }

        if (selected == null) {
            return;
        }

        int vertexIndex = selected.findNearestVertexIndex(worldX, worldY, tolerance);
        if (vertexIndex >= 0) {
            beginDrag(state, selected.getId(), vertexIndex);
            state.selectWallVertex(selected.getId(), vertexIndex);
            canvas.requestRender();
            return;
        }

        int insertIndex = selected.findNearestSegmentInsertIndex(worldX, worldY, tolerance);
        if (insertIndex >= 0) {
            state.recordHistory();
            historyRecorded = true;
            double snappedInsertX = snapX(canvas, state, worldX);
            double snappedInsertY = snapY(canvas, state, worldY);
            EndpointSnap insertSnap = state.getProject().getWallLayer().findNearestEndpoint(snappedInsertX, snappedInsertY, tolerance, selected.getId());
            if (insertSnap != null) {
                snappedInsertX = insertSnap.x();
                snappedInsertY = insertSnap.y();
            }
            selected.insertPoint(insertIndex, snappedInsertX, snappedInsertY);
            beginDrag(state, selected.getId(), insertIndex);
            state.selectWallVertex(selected.getId(), insertIndex);
            canvas.requestRender();
            return;
        }

        state.selectWallPath(selected.getId());
        canvas.requestRender();
    }

    @Override
    public void onMouseDragged(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (!dragging || state.getProject() == null) {
            return;
        }
        WallPath selected = state.selectedWallPath();
        if (selected == null || !selected.getId().equals(activePathId) || activeVertexIndex < 0) {
            return;
        }

        double tolerance = 8.0 / Math.max(0.25, state.getZoom());

        if (!historyRecorded) {
            state.recordHistory();
            historyRecorded = true;
        }

        double snappedX = snapX(canvas, state, canvas.screenToWorldX(event.getX()));
        double snappedY = snapY(canvas, state, canvas.screenToWorldY(event.getY()));
        if (selected.isEndpointIndex(activeVertexIndex)) {
            EndpointSnap endpointSnap = state.getProject().getWallLayer().findNearestEndpoint(snappedX, snappedY, tolerance, selected.getId());
            if (endpointSnap != null) {
                snappedX = endpointSnap.x();
                snappedY = endpointSnap.y();
            }
        }
        selected.movePoint(activeVertexIndex, snappedX, snappedY);
        canvas.requestRender();
    }

    @Override
    public void onMouseReleased(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        dragging = false;
        historyRecorded = false;
        activePathId = null;
        activeVertexIndex = -1;
        state.clearWallSnapIndicator();
        canvas.requestRender();
    }

    @Override
    public void onMouseMoved(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        if (state.getProject() == null) {
            return;
        }
        WallLayer wallLayer = state.getProject().getWallLayer();
        WallPath selected = state.selectedWallPath();
        if (wallLayer == null || selected == null || !wallLayer.isVisible() || wallLayer.isLocked() || dragging) {
            return;
        }
        double worldX = canvas.screenToWorldX(event.getX());
        double worldY = canvas.screenToWorldY(event.getY());
        double tolerance = 8.0 / Math.max(0.25, state.getZoom());
        EndpointSnap snap = wallLayer.findNearestEndpoint(worldX, worldY, tolerance, selected.getId());
        if (snap != null) {
            state.setWallSnapIndicator(snap.x(), snap.y());
            canvas.requestRender();
        } else {
            state.clearWallSnapIndicator();
            canvas.requestRender();
        }
    }

    private void beginDrag(EditorState state, String pathId, int vertexIndex) {
        dragging = true;
        activePathId = pathId;
        activeVertexIndex = vertexIndex;
        state.selectWallVertex(pathId, vertexIndex);
    }

    private double snapX(MapEditorCanvas canvas, EditorState state, double worldX) {
        if (!state.isSnapToGrid()) {
            return worldX;
        }
        int cell = state.grid().getCellSize();
        int ox = state.grid().getOffsetX();
        double col = Math.round((worldX - ox) / cell);
        return ox + col * cell;
    }

    private double snapY(MapEditorCanvas canvas, EditorState state, double worldY) {
        if (!state.isSnapToGrid()) {
            return worldY;
        }
        int cell = state.grid().getCellSize();
        int oy = state.grid().getOffsetY();
        double row = Math.round((worldY - oy) / cell);
        return oy + row * cell;
    }
}
