package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.MapObject;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.MapObjectDto;
import com.avalon.dnd.shared.TokenDto;
import com.avalon.dnd.shared.VisibilityStateDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side gameplay rules derived from map-editor metadata.
 * Keeps the implementation independent from the editor module by reading
 * opaque JSON-like objects (LinkedHashMap / ArrayList) produced by Jackson.
 */
@Service
public class MapBattleRulesService {

    private static final int DEFAULT_SHARED_VISION_DISTANCE = 8;

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

    public VisibilityStateDto computeVisibility(GameSession session) {
        VisibilityComputationResult result = computeVisibilitySnapshot(session);
        if (session != null) {
            session.setVisibilityStatesByPlayer(result.perPlayerStates());
            session.setVisibilityState(result.mergedState());
        }
        return result.mergedState();
    }

    public VisibilityStateDto getVisibilityForPlayer(GameSession session, String playerId) {
        if (session == null) {
            return emptyVisibilityState();
        }

        Player player = playerId == null ? null : session.getPlayers().get(playerId);
        if (player != null && player.getRole() == com.avalon.dnd.server.model.Role.DM) {
            VisibilityStateDto merged = session.getVisibilityState();
            return merged == null ? emptyVisibilityState() : merged;
        }

        Map<String, VisibilityStateDto> states = session.getVisibilityStatesByPlayer();
        if (states != null && playerId != null) {
            VisibilityStateDto state = states.get(playerId);
            if (state != null) {
                return state;
            }
        }

        VisibilityStateDto fallback = session.getVisibilityState();
        return fallback == null ? emptyVisibilityState() : fallback;
    }

    public MapLayoutUpdateDto buildMapLayout(GameSession session, String forPlayerId) {
        VisibilityStateDto visibility = getVisibilityForPlayer(session, forPlayerId);
        return new MapLayoutUpdateDto(
                session.getGrid(),
                session.getTokens().values().stream().map(this::toTokenDto).toList(),
                session.getObjects().values().stream().map(this::toObjectDto).toList(),
                session.getBackgroundUrl(),
                visibility,
                session.getReferenceOverlayLayer(),
                session.getTerrainLayer(),
                session.getWallLayer(),
                session.getFogSettings(),
                session.getMicroLocations(),
                session.getAssetPackIds()
        );
    }

    public boolean[][] computeVisibility(GameSession session, String viewerPlayerId, int revealRadius) {
        return getVisibilityForPlayer(session, viewerPlayerId).getVisibleCells();
    }

    private VisibilityComputationResult computeVisibilitySnapshot(GameSession session) {
        GridConfig grid = session == null ? null : session.getGrid();
        if (grid == null) {
            VisibilityStateDto empty = emptyVisibilityState();
            return new VisibilityComputationResult(empty, Map.of());
        }

        int rows = Math.max(0, grid.getRows());
        int cols = Math.max(0, grid.getCols());
        Object fogSettings = session == null ? null : session.getFogSettings();
        boolean enabled = isFogEnabled(fogSettings);
        boolean revealFromTokens = isRevealFromTokensEnabled(fogSettings);
        boolean retainExploredCells = isRetainExploredCellsEnabled(fogSettings);
        int sharedVisionDistance = Math.max(0, resolveSharedVisionDistance(fogSettings, DEFAULT_SHARED_VISION_DISTANCE));
        int sharedVisionDistanceSq = sharedVisionDistance * sharedVisionDistance;

        Map<String, List<VisibilitySource>> sourcesByPlayer = buildSourcesByPlayer(session, fogSettings, revealFromTokens);
        List<PlayerVisibilityGroup> groups = buildVisibilityGroups(session, sourcesByPlayer, sharedVisionDistanceSq);
        Map<String, VisibilityStateDto> previousStates = session == null ? Map.of() : session.getVisibilityStatesByPlayer();

        Map<String, VisibilityStateDto> perPlayerStates = new LinkedHashMap<>();
        VisibilityStateDto merged = null;

        if (!enabled) {
            VisibilityStateDto full = buildFullVisibilityState(session, rows, cols, previousStates, retainExploredCells);
            for (Player player : sortedPlayers(session)) {
                perPlayerStates.put(player.getId(), full);
            }
            merged = full;
        } else {
            for (PlayerVisibilityGroup group : groups) {
                VisibilityStateDto previous = mergePreviousStates(session, previousStates, group.playerIds());
                VisibilityStateDto state = buildVisibilityStateForSources(
                        session,
                        rows,
                        cols,
                        fogSettings,
                        retainExploredCells,
                        previous,
                        group.sources()
                );
                for (String playerId : group.playerIds()) {
                    perPlayerStates.put(playerId, state);
                }
                merged = mergeVisibilityStates(merged, state);
            }
        }

        if (merged == null) {
            merged = emptyVisibilityState(rows, cols);
        }

        return new VisibilityComputationResult(merged, perPlayerStates);
    }

    private List<PlayerVisibilityGroup> buildVisibilityGroups(GameSession session,
                                                              Map<String, List<VisibilitySource>> sourcesByPlayer,
                                                              int sharedVisionDistanceSq) {
        List<Player> players = sortedPlayers(session);
        if (players.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> playerIndex = new LinkedHashMap<>();
        for (int i = 0; i < players.size(); i++) {
            playerIndex.put(players.get(i).getId(), i);
        }

        UnionFind uf = new UnionFind(players.size());
        if (sharedVisionDistanceSq > 0) {
            for (int i = 0; i < players.size(); i++) {
                String leftId = players.get(i).getId();
                for (int j = i + 1; j < players.size(); j++) {
                    String rightId = players.get(j).getId();
                    if (shouldShareVision(sourcesByPlayer.get(leftId), sourcesByPlayer.get(rightId), sharedVisionDistanceSq)) {
                        uf.union(i, j);
                    }
                }
            }
        }

        Map<Integer, List<String>> playerIdsByRoot = new LinkedHashMap<>();
        for (Player player : players) {
            int index = playerIndex.get(player.getId());
            int root = uf.find(index);
            playerIdsByRoot.computeIfAbsent(root, k -> new ArrayList<>()).add(player.getId());
        }

        List<PlayerVisibilityGroup> groups = new ArrayList<>();
        for (List<String> playerIds : playerIdsByRoot.values()) {
            List<VisibilitySource> sources = new ArrayList<>();
            for (String playerId : playerIds) {
                List<VisibilitySource> playerSources = sourcesByPlayer.get(playerId);
                if (playerSources != null) {
                    sources.addAll(playerSources);
                }
            }
            String groupId = String.join("|", playerIds);
            groups.add(new PlayerVisibilityGroup(groupId, playerIds, sources));
        }
        return groups;
    }

    private boolean shouldShareVision(List<VisibilitySource> leftSources,
                                      List<VisibilitySource> rightSources,
                                      int sharedVisionDistanceSq) {
        if (leftSources == null || rightSources == null || leftSources.isEmpty() || rightSources.isEmpty()) {
            return false;
        }
        double minDistanceSq = Double.MAX_VALUE;
        for (VisibilitySource left : leftSources) {
            for (VisibilitySource right : rightSources) {
                double dx = left.x() - right.x();
                double dy = left.y() - right.y();
                double distanceSq = dx * dx + dy * dy;
                if (distanceSq < minDistanceSq) {
                    minDistanceSq = distanceSq;
                    if (minDistanceSq <= sharedVisionDistanceSq) {
                        return true;
                    }
                }
            }
        }
        return minDistanceSq <= sharedVisionDistanceSq;
    }

    private Map<String, List<VisibilitySource>> buildSourcesByPlayer(GameSession session,
                                                                      Object fogSettings,
                                                                      boolean revealFromTokens) {
        Map<String, List<VisibilitySource>> sourcesByPlayer = new LinkedHashMap<>();
        if (session == null || !revealFromTokens) {
            return sourcesByPlayer;
        }

        boolean nightMode = isNightMode(fogSettings);
        session.getTokens().values().forEach(token -> {
            if (token == null || token.getOwnerId() == null) return;
            int gs = Math.max(1, token.getGridSize());
            int radius = resolveVisionRadius(token, nightMode, 6);
            if (radius <= 0) return;
            VisibilitySource source = new VisibilitySource(
                    token.getCol() + gs / 2.0,
                    token.getRow() + gs / 2.0,
                    radius
            );
            sourcesByPlayer.computeIfAbsent(token.getOwnerId(), k -> new ArrayList<>()).add(source);
        });
        return sourcesByPlayer;
    }

    private VisibilityStateDto buildVisibilityStateForSources(GameSession session,
                                                              int rows,
                                                              int cols,
                                                              Object fogSettings,
                                                              boolean retainExploredCells,
                                                              VisibilityStateDto previous,
                                                              Collection<VisibilitySource> sources) {
        boolean[][] visible = new boolean[rows][cols];

        if (session == null || sources == null || sources.isEmpty()) {
            return buildStateFromVisible(session, visible, retainExploredCells, previous);
        }

        boolean[][] blockers = buildBlockedCells(session, true);
        for (VisibilitySource source : sources) {
            int radius = source.radius();
            if (radius <= 0) continue;
            int radiusSq = radius * radius;
            int minCol = Math.max(0, source.xFloor() - radius);
            int maxCol = Math.min(cols - 1, source.xCeil() + radius);
            int minRow = Math.max(0, source.yFloor() - radius);
            int maxRow = Math.min(rows - 1, source.yCeil() + radius);
            for (int row = minRow; row <= maxRow; row++) {
                for (int col = minCol; col <= maxCol; col++) {
                    int dx = col - source.xFloor();
                    int dy = row - source.yFloor();
                    if (dx * dx + dy * dy > radiusSq) continue;
                    if (hasLineOfSight(source.xFloor(), source.yFloor(), col, row, blockers)) {
                        visible[row][col] = true;
                    }
                }
            }
        }

        return buildStateFromVisible(session, visible, retainExploredCells, previous);
    }

    private VisibilityStateDto buildFullVisibilityState(GameSession session,
                                                        int rows,
                                                        int cols,
                                                        Map<String, VisibilityStateDto> previousStates,
                                                        boolean retainExploredCells) {
        boolean[][] visible = new boolean[rows][cols];
        fillAllVisible(visible);
        VisibilityStateDto previous = mergeVisibilityStates(previousStates == null ? null : new ArrayList<>(previousStates.values()));
        if (previous == null && session != null) {
            previous = session.getVisibilityState();
        }
        return buildStateFromVisible(session, visible, retainExploredCells, previous);
    }

    private VisibilityStateDto buildStateFromVisible(GameSession session,
                                                     boolean[][] visible,
                                                     boolean retainExploredCells,
                                                     VisibilityStateDto previous) {
        LinkedHashSet<String> explored = new LinkedHashSet<>();
        LinkedHashMap<String, TokenDto> tokenSnapshots = new LinkedHashMap<>();
        LinkedHashMap<String, MapObjectDto> objectSnapshots = new LinkedHashMap<>();

        if (retainExploredCells && previous != null) {
            if (previous.getExploredCells() != null) explored.addAll(previous.getExploredCells());
            if (previous.getTokenSnapshots() != null) tokenSnapshots.putAll(previous.getTokenSnapshots());
            if (previous.getObjectSnapshots() != null) objectSnapshots.putAll(previous.getObjectSnapshots());
        }

        if (!retainExploredCells) {
            explored.clear();
            tokenSnapshots.clear();
            objectSnapshots.clear();
        }

        for (int row = 0; row < visible.length; row++) {
            for (int col = 0; col < visible[row].length; col++) {
                if (visible[row][col]) explored.add(row + ":" + col);
            }
        }

        if (session != null) {
            session.getTokens().values().forEach(token -> {
                if (token == null) return;
                int gs = Math.max(1, token.getGridSize());
                if (isAnyCellVisible(visible, token.getCol(), token.getRow(), gs, gs)) {
                    tokenSnapshots.put(token.getId(), toTokenDto(token));
                }
            });
            session.getObjects().values().forEach(obj -> {
                if (obj == null) return;
                int w = Math.max(1, obj.getWidth());
                int h = Math.max(1, obj.getHeight());
                if (isAnyCellVisible(visible, obj.getCol(), obj.getRow(), w, h)) {
                    objectSnapshots.put(obj.getId(), toObjectDto(obj));
                }
            });
        }

        VisibilityStateDto state = new VisibilityStateDto();
        state.setVisibleCells(visible);
        state.setExploredCells(new ArrayList<>(explored));
        state.setTokenSnapshots(tokenSnapshots);
        state.setObjectSnapshots(objectSnapshots);
        return state;
    }

    private VisibilityStateDto mergeVisibilityStates(Collection<VisibilityStateDto> states) {
        if (states == null || states.isEmpty()) {
            return null;
        }
        VisibilityStateDto merged = null;
        for (VisibilityStateDto state : states) {
            merged = mergeVisibilityStates(merged, state);
        }
        return merged;
    }

    private VisibilityStateDto mergeVisibilityStates(VisibilityStateDto left, VisibilityStateDto right) {
        if (left == null) return right == null ? null : copyVisibilityState(right);
        if (right == null) return copyVisibilityState(left);

        boolean[][] visible = mergeVisibleCells(left.getVisibleCells(), right.getVisibleCells());
        LinkedHashSet<String> explored = new LinkedHashSet<>();
        if (left.getExploredCells() != null) explored.addAll(left.getExploredCells());
        if (right.getExploredCells() != null) explored.addAll(right.getExploredCells());
        LinkedHashMap<String, TokenDto> tokenSnapshots = new LinkedHashMap<>();
        if (left.getTokenSnapshots() != null) tokenSnapshots.putAll(left.getTokenSnapshots());
        if (right.getTokenSnapshots() != null) tokenSnapshots.putAll(right.getTokenSnapshots());
        LinkedHashMap<String, MapObjectDto> objectSnapshots = new LinkedHashMap<>();
        if (left.getObjectSnapshots() != null) objectSnapshots.putAll(left.getObjectSnapshots());
        if (right.getObjectSnapshots() != null) objectSnapshots.putAll(right.getObjectSnapshots());

        VisibilityStateDto merged = new VisibilityStateDto();
        merged.setVisibleCells(visible);
        merged.setExploredCells(new ArrayList<>(explored));
        merged.setTokenSnapshots(tokenSnapshots);
        merged.setObjectSnapshots(objectSnapshots);
        return merged;
    }

    private VisibilityStateDto copyVisibilityState(VisibilityStateDto source) {
        if (source == null) return null;
        VisibilityStateDto copy = new VisibilityStateDto();
        copy.setVisibleCells(copyVisibleCells(source.getVisibleCells()));
        copy.setExploredCells(source.getExploredCells() == null ? null : new ArrayList<>(source.getExploredCells()));
        copy.setTokenSnapshots(source.getTokenSnapshots() == null ? null : new LinkedHashMap<>(source.getTokenSnapshots()));
        copy.setObjectSnapshots(source.getObjectSnapshots() == null ? null : new LinkedHashMap<>(source.getObjectSnapshots()));
        return copy;
    }

    private boolean[][] mergeVisibleCells(boolean[][] left, boolean[][] right) {
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

    private boolean[][] copyVisibleCells(boolean[][] source) {
        if (source == null) return null;
        boolean[][] copy = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? new boolean[0] : java.util.Arrays.copyOf(source[i], source[i].length);
        }
        return copy;
    }

    private VisibilityStateDto emptyVisibilityState() {
        return emptyVisibilityState(0, 0);
    }

    private VisibilityStateDto emptyVisibilityState(int rows, int cols) {
        VisibilityStateDto empty = new VisibilityStateDto();
        empty.setVisibleCells(new boolean[Math.max(0, rows)][Math.max(0, cols)]);
        return empty;
    }

    private VisibilityStateDto mergePreviousStates(GameSession session,
                                                   Map<String, VisibilityStateDto> previousStates,
                                                   Collection<String> playerIds) {
        VisibilityStateDto merged = null;
        if (previousStates != null && playerIds != null) {
            for (String playerId : playerIds) {
                merged = mergeVisibilityStates(merged, previousStates.get(playerId));
            }
        }
        if (merged == null && session != null) {
            merged = session.getVisibilityState();
        }
        return merged;
    }

    private List<Player> sortedPlayers(GameSession session) {
        if (session == null || session.getPlayers() == null) {
            return List.of();
        }
        return session.getPlayers().values().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Player::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private boolean isNightMode(Object fogSettings) {
        if (fogSettings instanceof Map<?, ?> fogMap) {
            Object value = fogMap.get("timeOfDay");
            if (value instanceof String text) {
                String normalized = text.trim().toLowerCase();
                if (normalized.equals("night") || normalized.equals("dark")) return true;
                if (normalized.equals("day") || normalized.equals("light")) return false;
            }
            value = fogMap.get("dayNight");
            if (value instanceof String text) {
                String normalized = text.trim().toLowerCase();
                if (normalized.equals("night") || normalized.equals("dark")) return true;
                if (normalized.equals("day") || normalized.equals("light")) return false;
            }
            value = fogMap.get("mode");
            if (value instanceof String text) {
                String normalized = text.trim().toLowerCase();
                if (normalized.equals("night") || normalized.equals("dark")) return true;
                if (normalized.equals("day") || normalized.equals("light")) return false;
            }
            Object boolValue = fogMap.get("nightMode");
            if (boolValue == null) boolValue = fogMap.get("isNightMode");
            if (boolValue == null) boolValue = fogMap.get("night");
            if (boolValue == null) boolValue = fogMap.get("isNight");
            if (boolValue == null) boolValue = fogMap.get("darkness");
            return readBoolean(boolValue, false);
        }
        return false;
    }

    private int resolveVisionRadius(Token token, boolean isNightMode, int fallback) {
        int preferred = isNightMode ? token.getNightVision() : token.getDayVision();
        int alternate = isNightMode ? token.getDayVision() : token.getNightVision();
        int radius = preferred > 0 ? preferred : (alternate > 0 ? alternate : fallback);
        return Math.max(0, radius);
    }

    private int resolveSharedVisionDistance(Object fogSettings, int fallback) {
        if (fogSettings instanceof Map<?, ?> fogMap) {
            int distance = readInt(firstNonNull(
                    fogMap.get("sharedVisionDistance"),
                    fogMap.get("visibilityGroupDistance"),
                    fogMap.get("partyVisionDistance"),
                    fogMap.get("groupDistance"),
                    fogMap.get("sharedDistance")
            ), fallback);
            return Math.max(0, distance);
        }
        return Math.max(0, fallback);
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) return value;
        }
        return null;
    }

    private boolean isFogEnabled(Object fogSettings) {
        if (fogSettings instanceof Map<?, ?> fogMap) {
            return readBoolean(fogMap.get("enabled"), true);
        }
        return true;
    }

    private boolean isRevealFromTokensEnabled(Object fogSettings) {
        if (fogSettings instanceof Map<?, ?> fogMap) {
            return readBoolean(fogMap.get("revealFromTokens"), true);
        }
        return true;
    }

    private boolean isRetainExploredCellsEnabled(Object fogSettings) {
        if (fogSettings instanceof Map<?, ?> fogMap) {
            return readBoolean(fogMap.get("retainExploredCells"), true);
        }
        return true;
    }

    private TokenDto toTokenDto(Token t) {
        return new TokenDto(
                t.getId(), t.getName(), t.getCol(), t.getRow(), t.getOwnerId(),
                t.getHp(), t.getMaxHp(),
                t.getGridSize(), t.getImageUrl(),
                t.getDayVision(), t.getNightVision()
        );
    }

    private MapObjectDto toObjectDto(MapObject o) {
        MapObjectDto dto = new MapObjectDto(
                o.getId(), o.getType(),
                o.getCol(), o.getRow(),
                o.getWidth(), o.getHeight(),
                o.getGridSize(), o.getImageUrl(),
                o.isBlocksMovement(), o.isBlocksSight()
        );
        dto.setMicroLocationId(o.getMicroLocationId());
        return dto;
    }

    private boolean[][] buildBlockedCells(GameSession session, boolean forSight) {
        GridConfig grid = session.getGrid();
        int rows = Math.max(0, grid.getRows());
        int cols = Math.max(0, grid.getCols());
        boolean[][] blocked = new boolean[rows][cols];

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

    private boolean isAnyCellVisible(boolean[][] visible, int col, int row, int width, int height) {
        for (int r = row; r < row + height; r++) {
            if (r < 0 || r >= visible.length) continue;
            for (int c = col; c < col + width; c++) {
                if (c < 0 || c >= visible[r].length) continue;
                if (visible[r][c]) return true;
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

    private record VisibilitySource(double x, double y, int radius) {
        int xFloor() { return (int) Math.floor(x); }
        int yFloor() { return (int) Math.floor(y); }
        int xCeil() { return (int) Math.ceil(x); }
        int yCeil() { return (int) Math.ceil(y); }
    }

    private record PlayerVisibilityGroup(String groupId, List<String> playerIds, List<VisibilitySource> sources) {}
    private record VisibilityComputationResult(VisibilityStateDto mergedState, Map<String, VisibilityStateDto> perPlayerStates) {}
    private record Point(double x, double y) {}
    private record Cell(int col, int row) {}

    private static final class UnionFind {
        private final int[] parent;
        private final int[] rank;

        private UnionFind(int size) {
            this.parent = new int[size];
            this.rank = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        private int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }

        private void union(int a, int b) {
            int rootA = find(a);
            int rootB = find(b);
            if (rootA == rootB) return;
            if (rank[rootA] < rank[rootB]) {
                parent[rootA] = rootB;
            } else if (rank[rootA] > rank[rootB]) {
                parent[rootB] = rootA;
            } else {
                parent[rootB] = rootA;
                rank[rootA]++;
            }
        }
    }
}
