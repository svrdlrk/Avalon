package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Token;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class MapBattleRulesGeometrySupport {

    private MapBattleRulesGeometrySupport() {
    }

    static boolean[][] mergeVisibleCells(boolean[][] left, boolean[][] right) {
        if (left == null) return copyVisibleCells(right);
        if (right == null) return copyVisibleCells(left);
        int rows = Math.max(left.length, right.length);
        int cols = 0;
        for (boolean[] row : left) {
            cols = Math.max(cols, row == null ? 0 : row.length);
        }
        for (boolean[] row : right) {
            cols = Math.max(cols, row == null ? 0 : row.length);
        }
        boolean[][] merged = new boolean[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                boolean lv = row < left.length && left[row] != null && col < left[row].length && left[row][col];
                boolean rv = row < right.length && right[row] != null && col < right[row].length && right[row][col];
                merged[row][col] = lv || rv;
            }
        }
        return merged;
    }

    static boolean[][] copyVisibleCells(boolean[][] source) {
        if (source == null) return null;
        boolean[][] copy = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? new boolean[0] : Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }

    static void fillAllVisible(boolean[][] visible) {
        for (boolean[] row : visible) {
            Arrays.fill(row, true);
        }
    }

    static boolean intersectsBlocked(boolean[][] blocked, int col, int row, int width, int height) {
        for (int r = row; r < row + height; r++) {
            if (r < 0 || r >= blocked.length) return true;
            for (int c = col; c < col + width; c++) {
                if (c < 0 || c >= blocked[r].length) return true;
                if (blocked[r][c]) return true;
            }
        }
        return false;
    }

    static boolean isAnyCellVisible(boolean[][] visible, int col, int row, int width, int height) {
        for (int r = row; r < row + height; r++) {
            if (r < 0 || r >= visible.length) continue;
            for (int c = col; c < col + width; c++) {
                if (c < 0 || c >= visible[r].length) continue;
                if (visible[r][c]) return true;
            }
        }
        return false;
    }

    static boolean intersectsAnyToken(GameSession session, String ignoreTokenId, int col, int row, int width, int height) {
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

    static boolean intersects(int x1, int y1, int w1, int h1,
                              int x2, int y2, int w2, int h2) {
        return x1 < x2 + w2
                && x1 + w1 > x2
                && y1 < y2 + h2
                && y1 + h1 > y2;
    }

    static boolean hasLineOfSight(int startCol, int startRow, int endCol, int endRow, boolean[][] blocked) {
        for (Cell cell : lineCells(startCol, startRow, endCol, endRow)) {
            if (cell.col == startCol && cell.row == startRow) continue;
            if (cell.col == endCol && cell.row == endRow) continue;
            if (isBlockedCell(blocked, cell.col, cell.row)) {
                return false;
            }
        }
        return true;
    }

    static List<Cell> lineCells(int startCol, int startRow, int endCol, int endRow) {
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

    static void markRect(boolean[][] blocked, int col, int row, int width, int height) {
        for (int r = Math.max(0, row); r < Math.min(blocked.length, row + height); r++) {
            for (int c = Math.max(0, col); blocked.length > 0 && c < Math.min(blocked[r].length, col + width); c++) {
                blocked[r][c] = true;
            }
        }
    }

    static void markSegment(boolean[][] blocked, double ax, double ay, double bx, double by, double ox, double oy, double cellSize, int expand) {
        int startCol = (int) Math.floor((ax - ox) / cellSize);
        int startRow = (int) Math.floor((ay - oy) / cellSize);
        int endCol = (int) Math.floor((bx - ox) / cellSize);
        int endRow = (int) Math.floor((by - oy) / cellSize);
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

    static int normalizeFacingAngleDeg(double angle) {
        int normalized = (int) Math.round(angle) % 360;
        if (normalized <= -180) normalized += 360;
        if (normalized > 180) normalized -= 360;
        return normalized;
    }

    static boolean readBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean b) return b;
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    static int readInt(Object value, int defaultValue) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return defaultValue;
        try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return defaultValue; }
    }

    static double readDouble(Object value, double defaultValue) {
        if (value instanceof Number n) return n.doubleValue();
        if (value == null) return defaultValue;
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return defaultValue; }
    }

    static Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    static boolean isBlockedCell(boolean[][] blocked, int col, int row) {
        return row < 0 || row >= blocked.length || col < 0 || col >= blocked[row].length || blocked[row][col];
    }

    private record Cell(int col, int row) {}
}
