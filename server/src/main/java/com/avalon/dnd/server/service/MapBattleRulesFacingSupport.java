package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.JsonPayloads;

import java.util.Map;

/**
 * NPC facing helpers extracted from the battle rules service.
 */
final class MapBattleRulesFacingSupport {

    private MapBattleRulesFacingSupport() {
    }

    static void updateNpcFacing(GameSession session) {
        if (session == null || session.getTokens() == null || session.getTokens().isEmpty()) {
            return;
        }
        GridConfig grid = session.getGrid();
        if (grid == null) {
            return;
        }
        boolean nightMode = MapBattleRulesFogSupport.snapshot(session.getFogSettings(), 6).nightMode();
        boolean[][] blockers = buildBlockedCells(session, true);
        for (Token npc : session.getTokens().values()) {
            if (npc == null || npc.getOwnerId() != null) continue;
            int angle = computeNpcFacingAngle(session, npc, blockers, nightMode);
            if (angle != Integer.MIN_VALUE) {
                npc.setFacingAngleDeg(angle);
            }
        }
    }

    static void resetNpcFacing(GameSession session) {
        if (session == null || session.getTokens() == null || session.getTokens().isEmpty()) {
            return;
        }
        for (Token token : session.getTokens().values()) {
            if (token != null && token.getOwnerId() == null) {
                token.setFacingAngleDeg(0);
            }
        }
    }

    static int computeNpcFacingAngle(GameSession session, Token npc, boolean[][] blockers, boolean nightMode) {
        if (session == null || npc == null) {
            return Integer.MIN_VALUE;
        }
        double originX = npc.getCol() + Math.max(1, npc.getGridSize()) / 2.0;
        double originY = npc.getRow() + Math.max(1, npc.getGridSize()) / 2.0;
        int radius = MapBattleRulesFogSupport.resolveVisionRadius(npc, nightMode, 6);
        double bestDistSq = Double.POSITIVE_INFINITY;
        Integer bestAngle = null;
        for (Token playerToken : session.getTokens().values()) {
            if (playerToken == null || playerToken.getOwnerId() == null) continue;
            double targetX = playerToken.getCol() + Math.max(1, playerToken.getGridSize()) / 2.0;
            double targetY = playerToken.getRow() + Math.max(1, playerToken.getGridSize()) / 2.0;
            double dx = targetX - originX;
            double dy = targetY - originY;
            double distSq = dx * dx + dy * dy;
            if (radius > 0 && distSq > (double) radius * radius) continue;
            int targetCol = (int) Math.round(targetX);
            int targetRow = (int) Math.round(targetY);
            if (blockers != null && !MapBattleRulesGeometrySupport.hasLineOfSight((int) Math.round(originX), (int) Math.round(originY), targetCol, targetRow, blockers)) {
                continue;
            }
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestAngle = MapBattleRulesGeometrySupport.normalizeFacingAngleDeg(Math.toDegrees(Math.atan2(dy, dx)) - 90.0);
            }
        }
        return bestAngle == null ? Integer.MIN_VALUE : bestAngle;
    }

    private static boolean[][] buildBlockedCells(GameSession session, boolean forSight) {
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
            if (cells instanceof java.util.List<?> list) {
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
            if (paths instanceof java.util.List<?> list) {
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
                    if (!(points instanceof java.util.List<?> pts) || pts.size() < 2) continue;
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
