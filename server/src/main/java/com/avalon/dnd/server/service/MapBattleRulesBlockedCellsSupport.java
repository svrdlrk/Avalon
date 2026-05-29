package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.JsonPayloads;

import java.util.List;
import java.util.Map;

final class MapBattleRulesBlockedCellsSupport {

    private MapBattleRulesBlockedCellsSupport() {
    }

    static boolean[][] buildBlockedCells(GameSession session, boolean forSight) {
        if (session == null || session.getGrid() == null) {
            return new boolean[0][0];
        }
        GridConfig grid = session.getGrid();
        int rows = Math.max(0, grid.getRows());
        int cols = Math.max(0, grid.getCols());
        boolean[][] blocked = new boolean[rows][cols];

        for (var obj : session.getObjects().values()) {
            if (obj == null) continue;
            boolean blocks = forSight ? obj.isBlocksSight() : obj.isBlocksMovement();
            if (!blocks) continue;
            MapBattleRulesGeometrySupport.markRect(blocked, obj.getCol(), obj.getRow(), Math.max(1, obj.getWidth()), Math.max(1, obj.getHeight()));
        }

        Map<String, Object> terrainMap = JsonPayloads.toMap(session.getTerrainLayer());
        if (!terrainMap.isEmpty()) {
            Object cells = terrainMap.get("cells");
            if (cells instanceof List<?> list) {
                for (Object cellObj : list) {
                    if (!(cellObj instanceof Map<?, ?> cell)) continue;
                    boolean blocks = forSight
                            ? MapBattleRulesGeometrySupport.readBoolean(cell.get("blocksSight"), MapBattleRulesGeometrySupport.readBoolean(cell.get("blocksMovement"), false))
                            : MapBattleRulesGeometrySupport.readBoolean(cell.get("blocksMovement"), false);
                    if (!blocks) continue;
                    int col = MapBattleRulesGeometrySupport.readInt(cell.get("col"), 0);
                    int row = MapBattleRulesGeometrySupport.readInt(cell.get("row"), 0);
                    int width = Math.max(1, MapBattleRulesGeometrySupport.readInt(cell.get("width"), 1));
                    int height = Math.max(1, MapBattleRulesGeometrySupport.readInt(cell.get("height"), 1));
                    MapBattleRulesGeometrySupport.markRect(blocked, col, row, width, height);
                }
            }
        }

        Map<String, Object> wallMap = JsonPayloads.toMap(session.getWallLayer());
        if (!wallMap.isEmpty()) {
            Object paths = wallMap.get("paths");
            if (paths instanceof List<?> list) {
                double cellSize = Math.max(1.0, grid.getCellSize());
                double ox = grid.getOffsetX();
                double oy = grid.getOffsetY();
                for (Object pathObj : list) {
                    if (!(pathObj instanceof Map<?, ?> path)) continue;
                    boolean blocks = forSight
                            ? MapBattleRulesGeometrySupport.readBoolean(path.get("blocksSight"), MapBattleRulesGeometrySupport.readBoolean(path.get("blocksMovement"), true))
                            : MapBattleRulesGeometrySupport.readBoolean(path.get("blocksMovement"), true);
                    if (!blocks) continue;
                    double thickness = Math.max(0.5, MapBattleRulesGeometrySupport.readDouble(path.get("thickness"), 2.5));
                    int expand = Math.max(0, (int) Math.ceil(thickness / cellSize));
                    Object points = path.get("points");
                    if (!(points instanceof List<?> pts) || pts.size() < 2) continue;
                    Map<?, ?> prev = null;
                    for (Object p : pts) {
                        if (!(p instanceof Map<?, ?> pm)) continue;
                        if (prev != null) {
                            MapBattleRulesGeometrySupport.markSegment(
                                    blocked,
                                    MapBattleRulesGeometrySupport.readDouble(prev.get("x"), 0.0),
                                    MapBattleRulesGeometrySupport.readDouble(prev.get("y"), 0.0),
                                    MapBattleRulesGeometrySupport.readDouble(pm.get("x"), 0.0),
                                    MapBattleRulesGeometrySupport.readDouble(pm.get("y"), 0.0),
                                    ox,
                                    oy,
                                    cellSize,
                                    expand);
                        }
                        prev = pm;
                    }
                }
            }
        }

        return blocked;
    }
}
