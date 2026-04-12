package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.FogSettings;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.model.PlacementKind;
import com.avalon.dnd.mapeditor.model.TerrainCell;
import com.avalon.dnd.mapeditor.model.TerrainLayer;
import com.avalon.dnd.mapeditor.model.WallLayer;
import com.avalon.dnd.mapeditor.model.WallPath;
import com.avalon.dnd.mapeditor.model.WallPoint;
import com.avalon.dnd.shared.GridConfig;

import java.util.ArrayList;
import java.util.List;

public final class FogCalculator {

    private FogCalculator() {}

    public static boolean[][] computeVisibleCells(MapProject project) {
        if (project == null) {
            return new boolean[0][0];
        }

        GridConfig grid = project.getGrid();
        if (grid == null) {
            return new boolean[0][0];
        }

        int rows = Math.max(0, grid.getRows());
        int cols = Math.max(0, grid.getCols());
        boolean[][] visible = new boolean[rows][cols];

        FogSettings settings = project.getFogSettings();
        if (settings == null || !settings.isEnabled()) {
            fillAllVisible(visible);
            return visible;
        }

        boolean[][] blockers = buildBlockers(project, rows, cols);
        List<Point> sources = buildSources(project, settings, rows, cols);

        if (sources.isEmpty()) {
            fillAllVisible(visible);
            return visible;
        }

        int radius = Math.max(0, settings.getRevealRadius());
        int radiusSq = radius * radius;

        for (Point source : sources) {
            int minCol = Math.max(0, source.col - radius);
            int maxCol = Math.min(cols - 1, source.col + radius);
            int minRow = Math.max(0, source.row - radius);
            int maxRow = Math.min(rows - 1, source.row + radius);

            for (int row = minRow; row <= maxRow; row++) {
                for (int col = minCol; col <= maxCol; col++) {
                    int dx = col - source.col;
                    int dy = row - source.row;
                    if ((dx * dx) + (dy * dy) > radiusSq) {
                        continue;
                    }
                    if (hasLineOfSight(source.col, source.row, col, row, blockers)) {
                        visible[row][col] = true;
                    }
                }
            }
        }

        return visible;
    }

    private static List<Point> buildSources(MapProject project, FogSettings settings, int rows, int cols) {
        List<Point> sources = new ArrayList<>();
        for (MapPlacement placement : project.getPlacements()) {
            if (placement == null) {
                continue;
            }
            if (!isSourcePlacement(project, placement, settings)) {
                continue;
            }
            sources.add(sourcePoint(placement, rows, cols));
        }
        return sources;
    }

    private static boolean isSourcePlacement(MapProject project, MapPlacement placement, FogSettings settings) {
        if (!isPlacementOnVisibleLayer(project, placement)) {
            return false;
        }
        if (placement.getKind() == PlacementKind.TOKEN || placement.getKind() == PlacementKind.SPAWN) {
            return settings.isRevealFromTokens();
        }
        return settings.isRevealFromSelectedPlacement() && placement.isSelected();
    }

    private static Point sourcePoint(MapPlacement placement, int rows, int cols) {
        int width = Math.max(1, placement.effectiveWidth());
        int height = Math.max(1, placement.effectiveHeight());
        int col = clamp(placement.getCol() + (width - 1) / 2, 0, Math.max(0, cols - 1));
        int row = clamp(placement.getRow() + (height - 1) / 2, 0, Math.max(0, rows - 1));
        return new Point(col, row);
    }

    private static boolean[][] buildBlockers(MapProject project, int rows, int cols) {
        boolean[][] blockers = new boolean[rows][cols];

        for (MapPlacement placement : project.getPlacements()) {
            if (placement == null || !placement.isBlocksSight() || !isPlacementOnVisibleLayer(project, placement)) {
                continue;
            }
            int startCol = Math.max(0, placement.getCol());
            int startRow = Math.max(0, placement.getRow());
            int endCol = Math.min(cols, placement.getCol() + placement.effectiveWidth());
            int endRow = Math.min(rows, placement.getRow() + placement.effectiveHeight());
            for (int row = startRow; row < endRow; row++) {
                for (int col = startCol; col < endCol; col++) {
                    blockers[row][col] = true;
                }
            }
        }

        TerrainLayer terrainLayer = project.getTerrainLayer();
        if (terrainLayer != null && terrainLayer.isVisible()) {
            for (TerrainCell terrainCell : terrainLayer.getCells()) {
                if (terrainCell == null || !terrainCell.isBlocksSight()) {
                    continue;
                }
                int startCol = Math.max(0, terrainCell.getCol());
                int startRow = Math.max(0, terrainCell.getRow());
                int endCol = Math.min(cols, terrainCell.getCol() + terrainCell.getWidth());
                int endRow = Math.min(rows, terrainCell.getRow() + terrainCell.getHeight());
                for (int row = startRow; row < endRow; row++) {
                    for (int col = startCol; col < endCol; col++) {
                        blockers[row][col] = true;
                    }
                }
            }
        }

        WallLayer wallLayer = project.getWallLayer();
        if (wallLayer != null && wallLayer.isVisible()) {
            double cellSize = project.getGrid() == null ? 64.0 : project.getGrid().getCellSize();
            double ox = project.getGrid() == null ? 0.0 : project.getGrid().getOffsetX();
            double oy = project.getGrid() == null ? 0.0 : project.getGrid().getOffsetY();
            for (WallPath path : wallLayer.getPaths()) {
                if (path == null || !path.isBlocksSight() || path.getPoints().size() < 2) {
                    continue;
                }
                WallPoint prev = null;
                for (WallPoint point : path.getPoints()) {
                    if (prev != null) {
                        markWallLine(blockers, prev, point, ox, oy, cellSize);
                    }
                    prev = point;
                }
            }
        }

        return blockers;
    }

    private static void markWallLine(boolean[][] blockers, WallPoint a, WallPoint b, double ox, double oy, double cellSize) {
        int startCol = (int) Math.floor((a.getX() - ox) / cellSize);
        int startRow = (int) Math.floor((a.getY() - oy) / cellSize);
        int endCol = (int) Math.floor((b.getX() - ox) / cellSize);
        int endRow = (int) Math.floor((b.getY() - oy) / cellSize);

        int dx = Math.abs(endCol - startCol);
        int dy = Math.abs(endRow - startRow);
        int sx = startCol < endCol ? 1 : -1;
        int sy = startRow < endRow ? 1 : -1;
        int err = dx - dy;

        int x = startCol;
        int y = startRow;
        while (true) {
            if (y >= 0 && y < blockers.length && x >= 0 && blockers.length > 0 && x < blockers[y].length) {
                blockers[y][x] = true;
            }
            if (x == endCol && y == endRow) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static boolean isPlacementOnVisibleLayer(MapProject project, MapPlacement placement) {
        if (project == null || placement == null) {
            return false;
        }
        String layerId = placement.getLayerId();
        if (layerId == null || layerId.isBlank()) {
            return true;
        }
        return project.findLayer(layerId).map(MapLayer::isVisible).orElse(true);
    }

    private static boolean hasLineOfSight(int startCol, int startRow, int endCol, int endRow, boolean[][] blockers) {
        int x = startCol;
        int y = startRow;
        int dx = Math.abs(endCol - startCol);
        int dy = Math.abs(endRow - startRow);
        int sx = startCol < endCol ? 1 : -1;
        int sy = startRow < endRow ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (!(x == startCol && y == startRow) && !(x == endCol && y == endRow) && isBlocked(blockers, x, y)) {
                return false;
            }

            if (x == endCol && y == endRow) {
                return true;
            }

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    private static boolean isBlocked(boolean[][] blockers, int col, int row) {
        return row >= 0 && row < blockers.length
                && col >= 0 && blockers[row].length > 0
                && col < blockers[row].length
                && blockers[row][col];
    }

    private static void fillAllVisible(boolean[][] visible) {
        for (boolean[] row : visible) {
            java.util.Arrays.fill(row, true);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Point(int col, int row) {}
}
