package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.GridConfig;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Server-side gameplay rules derived from map-editor metadata.
 * Keeps the implementation independent from the editor module by reading
 * opaque JSON-like objects (LinkedHashMap / ArrayList) produced by Jackson.
 */
@Service
public class MapBattleRulesService {

    public boolean isTokenPlacementAllowed(GameSession session, int col, int row, int size) {
        if (session == null || session.getGrid() == null) {
            return true;
        }
        int tokenSize = Math.max(1, size);
        GridConfig grid = session.getGrid();
        int maxCol = Math.max(0, grid.getCols() - tokenSize);
        int maxRow = Math.max(0, grid.getRows() - tokenSize);
        if (col < 0 || col > maxCol || row < 0 || row > maxRow) {
            return false;
        }
        if (!isAreaClear(session, col, row, tokenSize, tokenSize)) {
            return false;
        }
        return !intersectsAnyToken(session, null, col, row, tokenSize, tokenSize);
    }

    public boolean isTokenMoveAllowed(GameSession session, Token token, int toCol, int toRow) {
        if (session == null || token == null || session.getGrid() == null) {
            return true;
        }

        int size = Math.max(1, token.getGridSize());
        GridConfig grid = session.getGrid();
        int maxCol = Math.max(0, grid.getCols() - size);
        int maxRow = Math.max(0, grid.getRows() - size);
        if (toCol < 0 || toCol > maxCol || toRow < 0 || toRow > maxRow) {
            return false;
        }

        boolean[][] blocked = buildBlockedCells(session, false);
        if (intersectsBlocked(blocked, toCol, toRow, size, size)) {
            return false;
        }

        if (intersectsAnyToken(session, token.getId(), toCol, toRow, size, size)) {
            return false;
        }

        for (Cell step : lineCells(token.getCol(), token.getRow(), toCol, toRow)) {
            if (intersectsBlocked(blocked, step.col, step.row, size, size)) {
                return false;
            }
            if (intersectsAnyToken(session, token.getId(), step.col, step.row, size, size)) {
                return false;
            }
        }
        return true;
    }

    public boolean isAreaClear(GameSession session, int col, int row, int width, int height) {
        if (session == null || session.getGrid() == null) {
            return true;
        }
        GridConfig grid = session.getGrid();
        if (col < 0 || row < 0 || col + width > grid.getCols() || row + height > grid.getRows()) {
            return false;
        }
        boolean[][] blocked = buildBlockedCells(session, false);
        return !intersectsBlocked(blocked, col, row, width, height);
    }

    public boolean[][] computeVisibility(GameSession session, String viewerPlayerId, int revealRadius) {
        GridConfig grid = session == null ? null : session.getGrid();
        if (grid == null) {
            return new boolean[0][0];
        }
        int rows = Math.max(0, grid.getRows());
        int cols = Math.max(0, grid.getCols());
        boolean[][] visible = new boolean[rows][cols];
        if (session == null) {
            fillAllVisible(visible);
            return visible;
        }

        boolean[][] blockers = buildBlockedCells(session, true);
        List<Cell> sources = new ArrayList<>();
        session.getTokens().values().forEach(token -> {
            if (token == null) return;
            if (token.getOwnerId() != null) {
                int gs = Math.max(1, token.getGridSize());
                sources.add(new Cell(token.getCol() + gs / 2, token.getRow() + gs / 2));
            }
        });

        if (sources.isEmpty()) {
            fillAllVisible(visible);
            return visible;
        }

        int radius = Math.max(0, revealRadius);
        int radiusSq = radius * radius;
        for (Cell source : sources) {
            int minCol = Math.max(0, source.col - radius);
            int maxCol = Math.min(cols - 1, source.col + radius);
            int minRow = Math.max(0, source.row - radius);
            int maxRow = Math.min(rows - 1, source.row + radius);
            for (int row = minRow; row <= maxRow; row++) {
                for (int col = minCol; col <= maxCol; col++) {
                    int dx = col - source.col;
                    int dy = row - source.row;
                    if (dx * dx + dy * dy > radiusSq) continue;
                    if (hasLineOfSight(source.col, source.row, col, row, blockers)) {
                        visible[row][col] = true;
                    }
                }
            }
        }
        return visible;
    }

    private boolean[][] buildBlockedCells(GameSession session, boolean forSight) {
        GridConfig grid = session.getGrid();
        int rows = Math.max(0, grid.getRows());
        int cols = Math.max(0, grid.getCols());
        boolean[][] blocked = new boolean[rows][cols];

        // Props / objects that block movement or sight.
        for (var obj : session.getObjects().values()) {
            if (obj == null) continue;
            boolean blocks = forSight ? obj.isBlocksSight() : obj.isBlocksMovement();
            if (!blocks) continue;
            markRect(blocked, obj.getCol(), obj.getRow(), Math.max(1, obj.getWidth()), Math.max(1, obj.getHeight()));
        }

        Object terrainLayer = session.getTerrainLayer();
        if (terrainLayer instanceof Map<?, ?> terrainMap) {
            Object cells = terrainMap.get("cells");
            if (cells instanceof List<?> list) {
                for (Object cellObj : list) {
                    if (!(cellObj instanceof Map<?, ?> cell)) continue;
                    boolean blocks = forSight
                            ? readBoolean(cell.get("blocksSight"), readBoolean(cell.get("blocksMovement"), false))
                            : readBoolean(cell.get("blocksMovement"), false);
                    if (!blocks) continue;
                    int col = readInt(cell.get("col"), 0);
                    int row = readInt(cell.get("row"), 0);
                    int width = Math.max(1, readInt(cell.get("width"), 1));
                    int height = Math.max(1, readInt(cell.get("height"), 1));
                    markRect(blocked, col, row, width, height);
                }
            }
        }

        Object wallLayer = session.getWallLayer();
        if (wallLayer instanceof Map<?, ?> wallMap) {
            Object paths = wallMap.get("paths");
            if (paths instanceof List<?> list) {
                double cellSize = Math.max(1.0, grid.getCellSize());
                double ox = grid.getOffsetX();
                double oy = grid.getOffsetY();
                for (Object pathObj : list) {
                    if (!(pathObj instanceof Map<?, ?> path)) continue;
                    boolean blocks = forSight
                            ? readBoolean(path.get("blocksSight"), readBoolean(path.get("blocksMovement"), true))
                            : readBoolean(path.get("blocksMovement"), true);
                    if (!blocks) continue;
                    double thickness = Math.max(0.5, readDouble(path.get("thickness"), 2.5));
                    int expand = Math.max(0, (int) Math.ceil(thickness / cellSize));
                    Object points = path.get("points");
                    if (!(points instanceof List<?> pts) || pts.size() < 2) continue;
                    Point prev = null;
                    for (Object p : pts) {
                        if (!(p instanceof Map<?, ?> pm)) continue;
                        Point curr = new Point(readDouble(pm.get("x"), 0.0), readDouble(pm.get("y"), 0.0));
                        if (prev != null) {
                            markSegment(blocked, prev, curr, ox, oy, cellSize, expand);
                        }
                        prev = curr;
                    }
                }
            }
        }

        return blocked;
    }

    private void markRect(boolean[][] blocked, int col, int row, int width, int height) {
        for (int r = Math.max(0, row); r < Math.min(blocked.length, row + height); r++) {
            for (int c = Math.max(0, col); blocked.length > 0 && c < Math.min(blocked[r].length, col + width); c++) {
                blocked[r][c] = true;
            }
        }
    }

    private void markSegment(boolean[][] blocked, Point a, Point b, double ox, double oy, double cellSize, int expand) {
        int startCol = (int) Math.floor((a.x - ox) / cellSize);
        int startRow = (int) Math.floor((a.y - oy) / cellSize);
        int endCol = (int) Math.floor((b.x - ox) / cellSize);
        int endRow = (int) Math.floor((b.y - oy) / cellSize);
        for (Cell cell : lineCells(startCol, startRow, endCol, endRow)) {
            for (int r = cell.row - expand; r <= cell.row + expand; r++) {
                if (r < 0 || r >= blocked.length) continue;
                for (int c = cell.col - expand; c <= cell.col + expand; c++) {
                    if (c < 0 || c >= blocked[r].length) continue;
                    blocked[r][c] = true;
                }
            }
        }
    }

    private boolean intersectsBlocked(boolean[][] blocked, int col, int row, int width, int height) {
        for (int r = row; r < row + height; r++) {
            if (r < 0 || r >= blocked.length) return true;
            for (int c = col; c < col + width; c++) {
                if (c < 0 || c >= blocked[r].length) return true;
                if (blocked[r][c]) return true;
            }
        }
        return false;
    }

    private boolean intersectsAnyToken(GameSession session, String ignoreTokenId, int col, int row, int width, int height) {
        if (session == null) return false;
        for (Token other : session.getTokens().values()) {
            if (other == null) continue;
            if (ignoreTokenId != null && ignoreTokenId.equals(other.getId())) continue;
            int otherSize = Math.max(1, other.getGridSize());
            if (intersects(col, row, width, height, other.getCol(), other.getRow(), otherSize, otherSize)) {
                return true;
            }
        }
        return false;
    }
    private boolean intersects(int x1, int y1, int w1, int h1,
                               int x2, int y2, int w2, int h2) {
        return x1 < x2 + w2
                && x1 + w1 > x2
                && y1 < y2 + h2
                && y1 + h1 > y2;
    }


    private boolean hasLineOfSight(int startCol, int startRow, int endCol, int endRow, boolean[][] blocked) {
        for (Cell cell : lineCells(startCol, startRow, endCol, endRow)) {
            if (cell.col == startCol && cell.row == startRow) continue;
            if (cell.col == endCol && cell.row == endRow) continue;
            if (cell.row >= 0 && cell.row < blocked.length && cell.col >= 0 && cell.col < blocked[cell.row].length && blocked[cell.row][cell.col]) {
                return false;
            }
        }
        return true;
    }

    private List<Cell> lineCells(int startCol, int startRow, int endCol, int endRow) {
        List<Cell> cells = new ArrayList<>();
        int x = startCol;
        int y = startRow;
        int dx = Math.abs(endCol - startCol);
        int dy = Math.abs(endRow - startRow);
        int sx = startCol < endCol ? 1 : -1;
        int sy = startRow < endRow ? 1 : -1;
        int err = dx - dy;
        while (true) {
            cells.add(new Cell(x, y));
            if (x == endCol && y == endRow) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
        return cells;
    }

    private void fillAllVisible(boolean[][] visible) {
        for (boolean[] row : visible) {
            java.util.Arrays.fill(row, true);
        }
    }

    private static boolean readBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) return b;
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static int readInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return defaultValue;
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return defaultValue; }
    }

    private static double readDouble(Object value, double defaultValue) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return defaultValue;
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return defaultValue; }
    }

    private record Point(double x, double y) {}
    private record Cell(int col, int row) {}
}
