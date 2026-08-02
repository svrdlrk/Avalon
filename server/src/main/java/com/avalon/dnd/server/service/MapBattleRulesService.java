package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.MapObject;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.JsonPayloads;
import com.fasterxml.jackson.databind.JsonNode;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.MapObjectDto;
import com.avalon.dnd.shared.TokenVisibilitySnapshotDto;
import com.avalon.dnd.shared.VisibilityShareSuggestionDto;
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

import static com.avalon.dnd.server.service.MapBattleRulesFogSupport.isNightMode;
import static com.avalon.dnd.server.service.MapBattleRulesFogSupport.resolveVisionRadius;
import static com.avalon.dnd.server.service.MapBattleRulesGeometrySupport.firstNonNull;

/**
 * Server-side gameplay rules derived from map-editor metadata.
 * Keeps the implementation independent from the editor module by reading
 * opaque JsonNode payloads and converting them to maps/lists only at the boundary.
 */
@Service
public class MapBattleRulesService {

    private static final int DEFAULT_SHARED_VISION_DISTANCE = 8;
    private static final int SHARED_SUGGESTION_MAX_GROUP_SIZE = 8;

    private final MapLayoutAssembler mapLayoutAssembler;

    public MapBattleRulesService(MapLayoutAssembler mapLayoutAssembler) {
        this.mapLayoutAssembler = mapLayoutAssembler;
    }

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
        return !MapBattleRulesGeometrySupport.intersectsAnyToken(session, null, col, row, tokenSize, tokenSize);
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
        if (MapBattleRulesGeometrySupport.intersectsBlocked(blocked, toCol, toRow, size, size)) {
            return false;
        }

        if (MapBattleRulesGeometrySupport.intersectsAnyToken(session, token.getId(), toCol, toRow, size, size)) {
            return false;
        }

        return !intersectsWallSegment(session, token.getCol(), token.getRow(), size, toCol, toRow, true)
                && MapBattleRulesGeometrySupport.hasLineOfSight(token.getCol() + size / 2, token.getRow() + size / 2,
                toCol + size / 2, toRow + size / 2, blocked);
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
        return !MapBattleRulesGeometrySupport.intersectsBlocked(blocked, col, row, width, height);
    }

    public VisibilityStateDto computeVisibility(GameSession session) {
        VisibilityComputationResult result = computeVisibilitySnapshot(session);
        if (session != null) {
            session.setVisibilityStatesByPlayer(result.perPlayerStates());
            session.setSharedVisibilityState(result.sharedState());
            session.setVisibilityShareSuggestions(result.suggestions());
            session.setVisibilityState(result.mergedState());
            session.clearVisibilityDirty();
            if (isInitiativePublished(session)) {
                MapBattleRulesFacingSupport.updateNpcFacing(session);
            } else {
                MapBattleRulesFacingSupport.resetNpcFacing(session);
            }
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
        // A projector is intentionally shown the union of what all players
        // have discovered, never a private player view and never the DM view.
        if (player != null && player.getRole() == com.avalon.dnd.server.model.Role.OBSERVER) {
            VisibilityStateDto merged = session.getVisibilityState();
            return merged == null ? emptyVisibilityState() : merged;
        }

        VisibilityStateDto shared = session.getSharedVisibilityState();
        Map<String, VisibilityStateDto> states = session.getVisibilityStatesByPlayer();
        VisibilityStateDto privateState = null;
        if (states != null && playerId != null) {
            privateState = states.get(playerId);
        }

        VisibilityStateDto effective = MapBattleRulesVisibilitySupport.mergeVisibilityStates(shared, privateState);
        if (effective != null) {
            return effective;
        }

        VisibilityStateDto fallback = session.getVisibilityState();
        return fallback == null ? emptyVisibilityState() : fallback;
    }

    public MapLayoutUpdateDto buildMapLayout(GameSession session, String forPlayerId) {
        if (forPlayerId == null || forPlayerId.isBlank()) {
            return mapLayoutAssembler.build(session, null);
        }
        Player player = session == null ? null : session.getPlayers().get(forPlayerId);
        boolean isDm = player != null && player.getRole() == com.avalon.dnd.server.model.Role.DM;
        return mapLayoutAssembler.build(session, isDm ? null : getVisibilityForPlayer(session, forPlayerId), forPlayerId, isDm);
    }

    public boolean[][] computeVisibility(GameSession session, String viewerPlayerId, int revealRadius) {
        return getVisibilityForPlayer(session, viewerPlayerId).getVisibleCells();
    }

    private VisibilityComputationResult computeVisibilitySnapshot(GameSession session) {
        GridConfig grid = session == null ? null : session.getGrid();
        if (grid == null) {
            VisibilityStateDto empty = emptyVisibilityState();
            return new VisibilityComputationResult(empty, Map.of(), Map.of(), empty, List.of());
        }

        int rows = Math.max(0, grid.getRows());
        int cols = Math.max(0, grid.getCols());
        JsonNode fogSettings = session == null ? null : session.getFogSettings();
        MapBattleRulesFogSupport.FogSnapshot fog = MapBattleRulesFogSupport.snapshot(fogSettings, DEFAULT_SHARED_VISION_DISTANCE);
        boolean enabled = fog.enabled();
        boolean revealFromTokens = fog.revealFromTokens();
        boolean retainExploredCells = fog.retainExploredCells();
        int sharedVisionDistance = fog.sharedVisionDistance();
        int sharedVisionDistanceSq = sharedVisionDistance * sharedVisionDistance;

        Map<String, List<VisibilitySource>> sourcesByPlayer = buildSourcesByPlayer(session, fogSettings, revealFromTokens);
        Map<String, Set<String>> zonesByPlayer = buildZonesByPlayer(session);
        List<PlayerVisibilityGroup> groups = buildVisibilityGroups(session, sourcesByPlayer, zonesByPlayer, sharedVisionDistanceSq);
        Map<String, VisibilityStateDto> previousStates = session == null ? Map.of() : session.getVisibilityStatesByPlayer();
        VisibilityStateDto sharedState = session == null ? null : session.getSharedVisibilityState();

        Map<String, VisibilityStateDto> perPlayerStates = new LinkedHashMap<>();
        Map<String, VisibilityStateDto> effectiveStates = new LinkedHashMap<>();
        VisibilityStateDto merged = null;
        List<VisibilityShareSuggestionDto> suggestions = new ArrayList<>();

        if (!enabled) {
            VisibilityStateDto full = buildFullVisibilityState(session, rows, cols, previousStates, retainExploredCells);
            for (Player player : sortedPlayers(session)) {
                perPlayerStates.put(player.getId(), full);
                VisibilityStateDto effective = MapBattleRulesVisibilitySupport.mergeVisibilityStates(sharedState, full);
                effectiveStates.put(player.getId(), effective == null ? full : effective);
            }
            merged = MapBattleRulesVisibilitySupport.mergeVisibilityStates(sharedState, full);
        } else {
            for (PlayerVisibilityGroup group : groups) {
                VisibilityStateDto previous = mergePreviousStates(session, previousStates, group.playerIds());
                VisibilityStateDto privateState = buildVisibilityStateForSources(
                        session,
                        rows,
                        cols,
                        fogSettings,
                        retainExploredCells,
                        previous,
                        group.sources()
                );
                for (String playerId : group.playerIds()) {
                    perPlayerStates.put(playerId, privateState);
                    VisibilityStateDto effective = MapBattleRulesVisibilitySupport.mergeVisibilityStates(sharedState, privateState);
                    effectiveStates.put(playerId, effective == null ? privateState : effective);
                }
                merged = MapBattleRulesVisibilitySupport.mergeVisibilityStates(merged, privateState);
                String shareReason = shareReasonFor(session, group);
                if (MapBattleRulesShareSupport.shouldSuggestShare(sharedState, privateState, group.playerIds(), SHARED_SUGGESTION_MAX_GROUP_SIZE)) {
                    suggestions.add(new VisibilityShareSuggestionDto(
                            suggestionIdFor(group.playerIds()),
                            group.playerIds(),
                            shareReason,
                            true,
                            shareTriggerFor(group)));
                }
            }
            merged = MapBattleRulesVisibilitySupport.mergeVisibilityStates(sharedState, merged);
        }

        if (merged == null) {
            merged = emptyVisibilityState(rows, cols);
        }

        return new VisibilityComputationResult(merged, perPlayerStates, effectiveStates, sharedState == null ? emptyVisibilityState(rows, cols) : sharedState, suggestions);
    }

    private List<PlayerVisibilityGroup> buildVisibilityGroups(GameSession session,
                                                              Map<String, List<VisibilitySource>> sourcesByPlayer,
                                                              Map<String, Set<String>> zonesByPlayer,
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
        for (int i = 0; i < players.size(); i++) {
            String leftId = players.get(i).getId();
            for (int j = i + 1; j < players.size(); j++) {
                String rightId = players.get(j).getId();
                if (sharesAnyZone(zonesByPlayer.get(leftId), zonesByPlayer.get(rightId))) {
                    uf.union(i, j);
                }
            }
        }
        if (sharedVisionDistanceSq > 0) {
            for (int i = 0; i < players.size(); i++) {
                String leftId = players.get(i).getId();
                for (int j = i + 1; j < players.size(); j++) {
                    String rightId = players.get(j).getId();
                    if (shouldShareVision(sourcesByPlayer.get(leftId), sourcesByPlayer.get(rightId),
                            zonesByPlayer.get(leftId), zonesByPlayer.get(rightId), sharedVisionDistanceSq)) {
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
            Set<String> groupZones = new LinkedHashSet<>();
            for (String playerId : playerIds) {
                Set<String> playerZones = zonesByPlayer.get(playerId);
                if (playerZones != null) {
                    groupZones.addAll(playerZones);
                }
            }
            groups.add(new PlayerVisibilityGroup(groupId, playerIds, sources, groupZones));
        }
        return groups;
    }

    private boolean shouldShareVision(List<VisibilitySource> leftSources,
                                      List<VisibilitySource> rightSources,
                                      Set<String> leftZones,
                                      Set<String> rightZones,
                                      int sharedVisionDistanceSq) {
        if (sharesAnyZone(leftZones, rightZones)) {
            return true;
        }
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


    private Map<String, Set<String>> buildZonesByPlayer(GameSession session) {
        Map<String, Set<String>> zonesByPlayer = new LinkedHashMap<>();
        if (session == null || session.getTokens() == null || session.getMicroLocations() == null || session.getMicroLocations().isEmpty()) {
            return zonesByPlayer;
        }

        for (Token token : session.getTokens().values()) {
            if (token == null || token.getOwnerId() == null) continue;
            Set<String> tokenZones = resolveTokenZones(session, token);
            if (tokenZones.isEmpty()) continue;
            zonesByPlayer.computeIfAbsent(token.getOwnerId(), k -> new LinkedHashSet<>()).addAll(tokenZones);
        }
        return zonesByPlayer;
    }

    private Set<String> resolveTokenZones(GameSession session, Token token) {
        Set<String> zones = new LinkedHashSet<>();
        if (session == null || token == null || session.getMicroLocations() == null || session.getMicroLocations().isEmpty()) {
            return zones;
        }

        int gs = Math.max(1, token.getGridSize());
        for (int row = token.getRow(); row < token.getRow() + gs; row++) {
            for (int col = token.getCol(); col < token.getCol() + gs; col++) {
                String zoneId = resolveMicroLocationId(session, col, row);
                if (zoneId != null) {
                    zones.add(zoneId);
                }
            }
        }
        return zones;
    }

    private String resolveMicroLocationId(GameSession session, int col, int row) {
        if (session == null || session.getMicroLocations() == null) {
            return null;
        }
        for (var zone : session.getMicroLocations()) {
            if (zone == null) continue;
            int width = Math.max(1, zone.getWidth());
            int height = Math.max(1, zone.getHeight());
            if (col >= zone.getCol() && col < zone.getCol() + width && row >= zone.getRow() && row < zone.getRow() + height) {
                return zone.getId();
            }
        }
        return null;
    }

    private boolean sharesAnyZone(Set<String> leftZones, Set<String> rightZones) {
        if (leftZones == null || rightZones == null || leftZones.isEmpty() || rightZones.isEmpty()) {
            return false;
        }
        for (String zoneId : leftZones) {
            if (rightZones.contains(zoneId)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInitiativePublished(GameSession session) {
        if (session == null || session.getInitiativeState() == null || session.getInitiativeState().getEntries() == null) {
            return false;
        }
        return !session.getInitiativeState().getEntries().isEmpty();
    }

    private Map<String, List<VisibilitySource>> buildSourcesByPlayer(GameSession session,
                                                                      JsonNode fogSettings,
                                                                      boolean revealFromTokens) {
        Map<String, List<VisibilitySource>> sourcesByPlayer = new LinkedHashMap<>();
        if (session == null || !revealFromTokens) {
            return sourcesByPlayer;
        }

        boolean nightMode = MapBattleRulesFogSupport.snapshot(fogSettings, 6).nightMode();
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
                                                              JsonNode fogSettings,
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
                    if (hasLineOfSight(session, source.xFloor(), source.yFloor(), col, row, blockers)) {
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
        MapBattleRulesGeometrySupport.fillAllVisible(visible);
        VisibilityStateDto previous = MapBattleRulesVisibilitySupport.mergeVisibilityStates(previousStates == null ? null : new ArrayList<>(previousStates.values()));
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
        LinkedHashMap<String, TokenVisibilitySnapshotDto> tokenSnapshots = new LinkedHashMap<>();
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
                if (MapBattleRulesGeometrySupport.isAnyCellVisible(visible, token.getCol(), token.getRow(), gs, gs)) {
                    tokenSnapshots.put(token.getId(), MapBattleRulesMappers.toTokenSnapshotDto(token));
                }
            });
            session.getObjects().values().forEach(obj -> {
                if (obj == null) return;
                int w = Math.max(1, obj.getWidth());
                int h = Math.max(1, obj.getHeight());
                if (MapBattleRulesGeometrySupport.isAnyCellVisible(visible, obj.getCol(), obj.getRow(), w, h)) {
                    objectSnapshots.put(obj.getId(), MapBattleRulesMappers.toObjectDto(obj));
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

    private VisibilityStateDto emptyVisibilityState() {
        return emptyVisibilityState(0, 0);
    }

    private VisibilityStateDto emptyVisibilityState(int rows, int cols) {
        VisibilityStateDto state = new VisibilityStateDto();
        state.setVisibleCells(new boolean[Math.max(0, rows)][Math.max(0, cols)]);
        state.setExploredCells(List.of());
        state.setTokenSnapshots(Map.of());
        state.setObjectSnapshots(Map.of());
        return state;
    }

    private List<Player> sortedPlayers(GameSession session) {
        if (session == null || session.getPlayers() == null || session.getPlayers().isEmpty()) {
            return List.of();
        }
        List<Player> players = new ArrayList<>(session.getPlayers().values());
        players.removeIf(Objects::isNull);
        players.sort(Comparator.comparing(player -> player.getId() == null ? "" : player.getId()));
        return players;
    }

    private VisibilityStateDto mergePreviousStates(GameSession session,
                                                   Map<String, VisibilityStateDto> previousStates,
                                                   List<String> playerIds) {
        VisibilityStateDto merged = null;
        if (previousStates != null && playerIds != null) {
            for (String playerId : playerIds) {
                if (playerId == null) continue;
                merged = MapBattleRulesVisibilitySupport.mergeVisibilityStates(merged, previousStates.get(playerId));
            }
        }
        if (merged == null && session != null) {
            merged = session.getVisibilityState();
        }
        return merged;
    }

    public boolean approveVisibilityShare(GameSession session, String suggestionId) {
        if (session == null || suggestionId == null || suggestionId.isBlank()) {
            return false;
        }

        VisibilityShareSuggestionDto suggestion = null;
        for (VisibilityShareSuggestionDto s : session.getVisibilityShareSuggestions()) {
            if (suggestionId.equals(s.getSuggestionId())) {
                suggestion = s;
                break;
            }
        }
        if (suggestion == null) {
            return false;
        }

        VisibilityStateDto merged = session.getSharedVisibilityState();
        for (String playerId : suggestion.getPlayerIds()) {
            VisibilityStateDto playerState = session.getVisibilityStatesByPlayer().get(playerId);
            merged = MapBattleRulesVisibilitySupport.mergeVisibilityStates(merged, playerState);
        }
        if (merged == null) {
            merged = emptyVisibilityState();
        }
        session.setSharedVisibilityState(merged);
        session.getVisibilityShareSuggestions().removeIf(s -> suggestionId.equals(s.getSuggestionId()));
        computeVisibility(session);
        return true;
    }

    private String suggestionIdFor(List<String> playerIds) {
        if (playerIds == null || playerIds.isEmpty()) {
            return java.util.UUID.randomUUID().toString();
        }
        return String.join("|", playerIds);
    }

    private String shareReasonFor(GameSession session, PlayerVisibilityGroup group) {
        if (group == null || group.playerIds() == null || group.playerIds().isEmpty()) {
            return "Visibility can be shared";
        }
        if (group.playerIds().size() == 1) {
            return "Visibility can be shared";
        }
        if (group.groupZones() != null && !group.groupZones().isEmpty()) {
            return "Одна комната: " + describeZones(session, group.groupZones()) + " — " + String.join(", ", group.playerIds());
        }
        return "Игроки достаточно близко для совместного обзора: " + String.join(", ", group.playerIds());
    }


    private String shareTriggerFor(PlayerVisibilityGroup group) {
        if (group == null) {
            return "manual";
        }
        if (group.groupZones() != null && !group.groupZones().isEmpty()) {
            return "room";
        }
        return "distance";
    }

    private String describeZones(GameSession session, Set<String> zoneIds) {
        if (session == null || zoneIds == null || zoneIds.isEmpty() || session.getMicroLocations() == null) {
            return String.join(", ", zoneIds == null ? java.util.List.of() : zoneIds);
        }
        Map<String, String> namesById = new LinkedHashMap<>();
        for (var zone : session.getMicroLocations()) {
            if (zone == null || zone.getId() == null) continue;
            String label = zone.getName();
            if (label == null || label.isBlank()) label = zone.getId();
            namesById.put(zone.getId(), label);
        }
        List<String> names = new ArrayList<>();
        for (String zoneId : zoneIds) {
            names.add(namesById.getOrDefault(zoneId, zoneId));
        }
        return String.join(", ", names);
    }

    private boolean[][] buildBlockedCells(GameSession session, boolean forSight) {
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
            // map-editor может экспортировать под разными ключами
            Object paths = firstNonNull(
                    wallMap.get("paths"),
                    wallMap.get("walls"),
                    wallMap.get("segments"),
                    wallMap.get("polylines"),
                    wallMap.get("lines")
            );
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
                    Object points = MapBattleRulesGeometrySupport.firstNonNull(
                            path.get("points"),
                            path.get("vertices"),
                            path.get("coords"),
                            path.get("pts")
                    );
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

    private boolean hasLineOfSight(GameSession session, int startCol, int startRow, int endCol, int endRow, boolean[][] blockers) {
        return MapBattleRulesGeometrySupport.hasLineOfSight(startCol, startRow, endCol, endRow, blockers)
                && !intersectsWallSegment(session, startCol, startRow, 1, endCol, endRow, false);
    }

    private boolean intersectsWallSegment(GameSession session,
                                          int fromCol,
                                          int fromRow,
                                          int tokenSize,
                                          int toCol,
                                          int toRow,
                                          boolean forMovement) {
        if (session == null || session.getGrid() == null) {
            return false;
        }
        GridConfig grid = session.getGrid();
        double cellSize = Math.max(1.0, grid.getCellSize());
        double ax = grid.getOffsetX() + (fromCol + Math.max(1, tokenSize) / 2.0) * cellSize;
        double ay = grid.getOffsetY() + (fromRow + Math.max(1, tokenSize) / 2.0) * cellSize;
        double bx = grid.getOffsetX() + (toCol + Math.max(1, tokenSize) / 2.0) * cellSize;
        double by = grid.getOffsetY() + (toRow + Math.max(1, tokenSize) / 2.0) * cellSize;
        Map<String, Object> wallMap = JsonPayloads.toMap(session.getWallLayer());
        Object paths = firstNonNull(
                wallMap.get("paths"),
                wallMap.get("walls"),
                wallMap.get("segments"),
                wallMap.get("polylines"),
                wallMap.get("lines")
        );
        if (!(paths instanceof List<?> list)) {
            return false;
        }
        for (Object pathObj : list) {
            if (!(pathObj instanceof Map<?, ?> path)) continue;
            boolean blocks = forMovement
                    ? MapBattleRulesGeometrySupport.readBoolean(path.get("blocksMovement"), true)
                    : MapBattleRulesGeometrySupport.readBoolean(path.get("blocksSight"), MapBattleRulesGeometrySupport.readBoolean(path.get("blocksMovement"), true));
            if (!blocks) continue;

            Object points = firstNonNull(
                    path.get("points"),
                    path.get("vertices"),
                    path.get("coords"),
                    path.get("pts")
            );
            if (!(points instanceof List<?> pts) || pts.size() < 2) continue;

            Map<?, ?> prev = null;
            for (Object p : pts) {
                if (!(p instanceof Map<?, ?> pm)) continue;
                if (prev != null) {
                    double wx1 = MapBattleRulesGeometrySupport.readDouble(prev.get("x"), 0.0);
                    double wy1 = MapBattleRulesGeometrySupport.readDouble(prev.get("y"), 0.0);
                    double wx2 = MapBattleRulesGeometrySupport.readDouble(pm.get("x"), 0.0);
                    double wy2 = MapBattleRulesGeometrySupport.readDouble(pm.get("y"), 0.0);
                    if (segmentsIntersect(ax, ay, bx, by, wx1, wy1, wx2, wy2)) {
                        return true;
                    }
                }
                prev = pm;
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(double ax, double ay, double bx, double by,
                                             double cx, double cy, double dx, double dy) {
        double o1 = orientation(ax, ay, bx, by, cx, cy);
        double o2 = orientation(ax, ay, bx, by, dx, dy);
        double o3 = orientation(cx, cy, dx, dy, ax, ay);
        double o4 = orientation(cx, cy, dx, dy, bx, by);
        if (((o1 > 0 && o2 < 0) || (o1 < 0 && o2 > 0))
                && ((o3 > 0 && o4 < 0) || (o3 < 0 && o4 > 0))) {
            return true;
        }
        return isOnSegment(ax, ay, bx, by, cx, cy)
                || isOnSegment(ax, ay, bx, by, dx, dy)
                || isOnSegment(cx, cy, dx, dy, ax, ay)
                || isOnSegment(cx, cy, dx, dy, bx, by);
    }

    private static double orientation(double ax, double ay, double bx, double by, double px, double py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }

    private static boolean isOnSegment(double ax, double ay, double bx, double by, double px, double py) {
        double epsilon = 0.000001;
        return Math.abs(orientation(ax, ay, bx, by, px, py)) <= epsilon
                && px >= Math.min(ax, bx) - epsilon && px <= Math.max(ax, bx) + epsilon
                && py >= Math.min(ay, by) - epsilon && py <= Math.max(ay, by) + epsilon;
    }
    private record VisibilitySource(double x, double y, int radius) {
        int xFloor() { return (int) Math.floor(x); }
        int yFloor() { return (int) Math.floor(y); }
        int xCeil() { return (int) Math.ceil(x); }
        int yCeil() { return (int) Math.ceil(y); }
    }

    private record PlayerVisibilityGroup(String groupId, List<String> playerIds, List<VisibilitySource> sources, Set<String> groupZones) {}
    private record VisibilityComputationResult(VisibilityStateDto mergedState,
                                              Map<String, VisibilityStateDto> perPlayerStates,
                                              Map<String, VisibilityStateDto> effectiveStates,
                                              VisibilityStateDto sharedState,
                                              List<VisibilityShareSuggestionDto> suggestions) {}

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
