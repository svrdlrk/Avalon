package com.avalon.dnd.mapeditor.ui;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.model.WallPath;
import com.avalon.dnd.shared.MicroLocationDto;

public final class MapEditorCanvasGeometry {

    private MapEditorCanvasGeometry() {
    }

    public static int getGridCellSize(EditorState state) {
        return state.grid().getCellSize();
    }

    public static int getGridOffsetX(EditorState state) {
        return state.grid().getOffsetX();
    }

    public static int getGridOffsetY(EditorState state) {
        return state.grid().getOffsetY();
    }

    public static int[] screenToCell(EditorState state, double screenX, double screenY) {
        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        double worldX = screenToWorldX(state, screenX);
        double worldY = screenToWorldY(state, screenY);

        int col = (int) Math.floor((worldX - ox) / cell);
        int row = (int) Math.floor((worldY - oy) / cell);

        if (col < 0 || row < 0 || col >= grid.getCols() || row >= grid.getRows()) {
            return null;
        }
        return new int[] { col, row };
    }

    public static double screenToWorldX(EditorState state, double screenX) {
        return (screenX - state.getViewOffsetX()) / state.getZoom();
    }

    public static double screenToWorldY(EditorState state, double screenY) {
        return (screenY - state.getViewOffsetY()) / state.getZoom();
    }

    public static double worldToScreenX(EditorState state, double worldX) {
        return worldX * state.getZoom() + state.getViewOffsetX();
    }

    public static double worldToScreenY(EditorState state, double worldY) {
        return worldY * state.getZoom() + state.getViewOffsetY();
    }

    public static MapPlacement findPlacementAt(EditorState state, double screenX, double screenY) {
        int[] cell = screenToCell(state, screenX, screenY);
        if (cell == null) return null;

        int col = cell[0];
        int row = cell[1];

        MapProject project = state.getProject();
        if (project == null) return null;

        for (int layerIndex = project.getLayers().size() - 1; layerIndex >= 0; layerIndex--) {
        MapLayer layer = project.getLayers().get(layerIndex);
            if (!layer.isVisible()) continue;
            for (int i = project.getPlacements().size() - 1; i >= 0; i--) {
                MapPlacement placement = project.getPlacements().get(i);
                if (!layer.getId().equals(placement.getLayerId())) continue;
                if (col >= placement.getCol() && col < placement.getCol() + placement.effectiveWidth()
                        && row >= placement.getRow() && row < placement.getRow() + placement.effectiveHeight()) {
                    return placement;
                }
            }
        }
        return null;
    }

    public static MapEditorCanvas.MicroLocationHit findMicroLocationHitAt(EditorState state, double screenX, double screenY) {
        MapProject project = state.getProject();
        if (project == null) return null;
        MicroLocationDto hitZone = null;
        for (int i = project.getMicroLocations().size() - 1; i >= 0; i--) {
            MicroLocationDto zone = project.getMicroLocations().get(i);
            if (zone == null) continue;
            if (containsMicroLocation(state, zone, screenX, screenY)) {
                hitZone = zone;
                break;
            }
        }
        if (hitZone == null) return null;
        return new MapEditorCanvas.MicroLocationHit(hitZone, findMicroLocationHandleAt(state, hitZone, screenX, screenY));
    }

    public static MicroLocationDto findMicroLocationAt(EditorState state, double screenX, double screenY) {
        MapEditorCanvas.MicroLocationHit hit = findMicroLocationHitAt(state, screenX, screenY);
        return hit == null ? null : hit.zone;
    }

    public static MapEditorCanvas.MicroLocationHandle findMicroLocationHandleAt(EditorState state, MicroLocationDto zone, double screenX, double screenY) {
        if (zone == null) return MapEditorCanvas.MicroLocationHandle.NONE;
        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        double x = ox + zone.getCol() * cell;
        double y = oy + zone.getRow() * cell;
        double w = Math.max(1, zone.getWidth()) * cell;
        double h = Math.max(1, zone.getHeight()) * cell;
        double threshold = 10.0 / state.getZoom();
        double worldX = screenToWorldX(state, screenX);
        double worldY = screenToWorldY(state, screenY);

        boolean left = Math.abs(worldX - x) <= threshold;
        boolean right = Math.abs(worldX - (x + w)) <= threshold;
        boolean top = Math.abs(worldY - y) <= threshold;
        boolean bottom = Math.abs(worldY - (y + h)) <= threshold;

        if (left && top) return MapEditorCanvas.MicroLocationHandle.NW;
        if (right && top) return MapEditorCanvas.MicroLocationHandle.NE;
        if (right && bottom) return MapEditorCanvas.MicroLocationHandle.SE;
        if (left && bottom) return MapEditorCanvas.MicroLocationHandle.SW;
        if (top && worldX >= x && worldX <= x + w) return MapEditorCanvas.MicroLocationHandle.N;
        if (bottom && worldX >= x && worldX <= x + w) return MapEditorCanvas.MicroLocationHandle.S;
        if (left && worldY >= y && worldY <= y + h) return MapEditorCanvas.MicroLocationHandle.W;
        if (right && worldY >= y && worldY <= y + h) return MapEditorCanvas.MicroLocationHandle.E;
        return MapEditorCanvas.MicroLocationHandle.MOVE;
    }

    private static boolean containsMicroLocation(EditorState state, MicroLocationDto zone, double screenX, double screenY) {
        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        double worldX = screenToWorldX(state, screenX);
        double worldY = screenToWorldY(state, screenY);
        double x = ox + zone.getCol() * cell;
        double y = oy + zone.getRow() * cell;
        double w = Math.max(1, zone.getWidth()) * cell;
        double h = Math.max(1, zone.getHeight()) * cell;
        return worldX >= x && worldX <= x + w && worldY >= y && worldY <= y + h;
    }

    public static WallPath findWallPathAt(EditorState state, double screenX, double screenY) {
        MapProject project = state.getProject();
        if (project == null || project.getWallLayer() == null || !project.getWallLayer().isVisible()) {
            return null;
        }

        double worldX = screenToWorldX(state, screenX);
        double worldY = screenToWorldY(state, screenY);
        double maxDistance = 8.0 / state.getZoom();

        for (int i = project.getWallLayer().getPaths().size() - 1; i >= 0; i--) {
            WallPath path = project.getWallLayer().getPaths().get(i);
            if (path == null || !path.isVisible() || path.getPoints().isEmpty()) {
                continue;
            }

            if (path.findNearestVertexIndex(worldX, worldY, maxDistance) >= 0) {
                return path;
            }
            if (path.getPoints().size() >= 2 && path.findNearestSegmentInsertIndex(worldX, worldY, maxDistance) >= 0) {
                return path;
            }
        }
        return null;
    }

    public static int findWallVertexAt(EditorState state, WallPath path, double screenX, double screenY) {
        if (path == null) {
            return -1;
        }
        double worldX = screenToWorldX(state, screenX);
        double worldY = screenToWorldY(state, screenY);
        return path.findNearestVertexIndex(worldX, worldY, 8.0 / state.getZoom());
    }
}
