package com.avalon.dnd.dm.model;

import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.JsonPayloads;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.MapObjectDto;
import com.avalon.dnd.shared.MicroLocationDto;
import com.avalon.dnd.shared.PlayerDto;
import com.avalon.dnd.shared.SessionStateDto;
import com.avalon.dnd.shared.TokenDto;

import javafx.application.Platform;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientState {

    private static final GridConfig DEFAULT_GRID = new GridConfig(64, 20, 20);

    private static final ClientState INSTANCE = new ClientState();

    public static ClientState getInstance() {
        return INSTANCE;
    }

    private String sessionId;
    private String playerId;
    private GridConfig grid = copyGrid(DEFAULT_GRID);
    private String backgroundUrl;
    private JsonNode referenceOverlayLayer;
    private JsonNode terrainLayer;
    private JsonNode wallLayer;
    private JsonNode fogSettings;

    private Map<String, Object> referenceOverlayLayerMap = Collections.emptyMap();
    private Map<String, Object> terrainLayerMap = Collections.emptyMap();
    private Map<String, Object> wallLayerMap = Collections.emptyMap();
    private Map<String, Object> fogSettingsMap = Collections.emptyMap();
    private List<MicroLocationDto> microLocations = new CopyOnWriteArrayList<>();
    private List<String> assetPackIds = new CopyOnWriteArrayList<>();

    private final Map<String, TokenDto> tokens = new LinkedHashMap<>();
    private final Map<String, MapObjectDto> objects = new LinkedHashMap<>();
    private final Map<String, PlayerDto> players = new LinkedHashMap<>();

    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean dispatchScheduled = new AtomicBoolean(false);
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private long lastAppliedVersion = -1L;

    private int pendingPlaceCol;
    private int pendingPlaceRow;

    private ClientState() {}

    public void applyState(SessionStateDto state, String sessionId, String playerId) {
        if (state == null) {
            return;
        }

        String nextSessionId = safeText(sessionId, this.sessionId);
        if (!Objects.equals(this.sessionId, nextSessionId)) {
            resetVersion();
        }
        this.sessionId = nextSessionId;
        this.playerId = safeText(playerId, this.playerId);
        this.grid = copyGrid(state.getGrid() != null ? state.getGrid() : this.grid);
        this.backgroundUrl = resolveBackgroundUrl(state.getBackgroundUrl(), state.getReferenceOverlayLayer());
        this.referenceOverlayLayer = state.getReferenceOverlayLayer();
        this.terrainLayer = state.getTerrainLayer();
        this.wallLayer = state.getWallLayer();
        this.fogSettings = state.getFogSettings();
        cacheOpaqueLayers();
        this.microLocations = new CopyOnWriteArrayList<>(safeList(state.getMicroLocations()));
        this.assetPackIds = new CopyOnWriteArrayList<>(safeList(state.getAssetPackIds()));

        replaceTokens(state.getTokens());
        replaceObjects(state.getObjects());
        replacePlayers(state.getPlayers());
        clampPendingPlaceCell();
        notifyMapChanged();
    }

    public void applyMapLayoutUpdate(MapLayoutUpdateDto dto) {
        if (dto == null) {
            return;
        }

        if (dto.getGrid() != null) {
            this.grid = copyGrid(dto.getGrid());
        }
        this.backgroundUrl = resolveBackgroundUrl(dto.getBackgroundUrl(), dto.getReferenceOverlayLayer());
        this.referenceOverlayLayer = dto.getReferenceOverlayLayer();
        this.terrainLayer = dto.getTerrainLayer();
        this.wallLayer = dto.getWallLayer();
        this.fogSettings = dto.getFogSettings();
        cacheOpaqueLayers();
        this.assetPackIds = new CopyOnWriteArrayList<>(safeList(dto.getAssetPackIds()));

        replaceTokens(dto.getTokens());
        replaceObjects(dto.getObjects());
        clampPendingPlaceCell();
        notifyMapChanged();
    }

    public void moveToken(TokenDto token) {
        if (token == null || token.getId() == null) {
            return;
        }
        tokens.put(token.getId(), token);
        notifyMapChanged();
    }

    public void addToken(TokenDto token) {
        if (token == null || token.getId() == null) {
            return;
        }
        tokens.put(token.getId(), token);
        notifyMapChanged();
    }

    public void removeToken(String id) {
        if (id == null) {
            return;
        }
        tokens.remove(id);
        notifyMapChanged();
    }

    public void addObject(MapObjectDto obj) {
        if (obj == null || obj.getId() == null) {
            return;
        }
        objects.put(obj.getId(), obj);
        notifyMapChanged();
    }

    public void removeObject(String id) {
        if (id == null) {
            return;
        }
        objects.remove(id);
        notifyMapChanged();
    }

    public void addPlayer(PlayerDto player) {
        if (player == null || player.getId() == null) {
            return;
        }
        players.put(player.getId(), player);
        notifyMapChanged();
    }

    public void removePlayer(String playerId) {
        if (playerId == null) {
            return;
        }
        players.remove(playerId);
        notifyMapChanged();
    }

    public void setPendingPlaceCell(int col, int row) {
        this.pendingPlaceCol = Math.max(0, col);
        this.pendingPlaceRow = Math.max(0, row);
        clampPendingPlaceCell();
        notifyMapChanged();
    }

    public int getPendingPlaceCol() {
        return pendingPlaceCol;
    }

    public int getPendingPlaceRow() {
        return pendingPlaceRow;
    }

    public void addChangeListener(Runnable r) {
        if (r != null) {
            changeListeners.add(r);
        }
    }

    public void removeChangeListener(Runnable r) {
        changeListeners.remove(r);
    }

    public void notifyMapChanged() {
        dirty.set(true);
        if (!dispatchScheduled.compareAndSet(false, true)) {
            return;
        }

        Runnable task = () -> {
            try {
                while (dirty.getAndSet(false)) {
                    for (Runnable r : changeListeners) {
                        try {
                            r.run();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            } finally {
                dispatchScheduled.set(false);
                if (dirty.get()) {
                    notifyMapChanged();
                }
            }
        };

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(task);
        } else {
            task.run();
        }
    }

    public synchronized void resetVersion() {
        lastAppliedVersion = -1L;
    }

    public synchronized boolean shouldApplyVersion(long version) {
        if (version < 0) {
            return true;
        }
        if (version < lastAppliedVersion) {
            return false;
        }
        lastAppliedVersion = version;
        return true;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public GridConfig getGrid() {
        return copyGrid(grid);
    }

    public Map<String, TokenDto> getTokens() {
        return Collections.unmodifiableMap(tokens);
    }

    public Map<String, MapObjectDto> getObjects() {
        return Collections.unmodifiableMap(objects);
    }

    public Map<String, PlayerDto> getPlayers() {
        return Collections.unmodifiableMap(players);
    }

    public String getBackgroundUrl() {
        return backgroundUrl;
    }

    public void setBackgroundUrl(String url) {
        this.backgroundUrl = url;
        notifyMapChanged();
    }

    public JsonNode getReferenceOverlayLayer() {
        return referenceOverlayLayer;
    }

    public Map<String, Object> getReferenceOverlayLayerMap() {
        return referenceOverlayLayerMap;
    }

    public void setReferenceOverlayLayer(Object value) {
        this.referenceOverlayLayer = JsonPayloads.toNode(value);
        this.referenceOverlayLayerMap = frozenMap(this.referenceOverlayLayer);
        notifyMapChanged();
    }

    public JsonNode getTerrainLayer() {
        return terrainLayer;
    }

    public Map<String, Object> getTerrainLayerMap() {
        return terrainLayerMap;
    }

    public void setTerrainLayer(Object value) {
        this.terrainLayer = JsonPayloads.toNode(value);
        this.terrainLayerMap = frozenMap(this.terrainLayer);
        notifyMapChanged();
    }

    public JsonNode getWallLayer() {
        return wallLayer;
    }

    public Map<String, Object> getWallLayerMap() {
        return wallLayerMap;
    }

    public void setWallLayer(Object value) {
        this.wallLayer = JsonPayloads.toNode(value);
        this.wallLayerMap = frozenMap(this.wallLayer);
        notifyMapChanged();
    }

    public JsonNode getFogSettings() {
        return fogSettings;
    }

    public Map<String, Object> getFogSettingsMap() {
        return fogSettingsMap;
    }

    public void setFogSettings(Object value) {
        this.fogSettings = JsonPayloads.toNode(value);
        this.fogSettingsMap = frozenMap(this.fogSettings);
        notifyMapChanged();
    }

    public List<MicroLocationDto> getMicroLocations() {
        return Collections.unmodifiableList(microLocations);
    }

    public void setMicroLocations(List<MicroLocationDto> value) {
        this.microLocations = new CopyOnWriteArrayList<>(safeList(value));
    }

    public List<String> getAssetPackIds() {
        return Collections.unmodifiableList(assetPackIds);
    }

    public void setAssetPackIds(List<String> value) {
        this.assetPackIds = new CopyOnWriteArrayList<>(safeList(value));
    }

    private String resolveBackgroundUrl(String backgroundUrl, JsonNode referenceOverlayLayer) {
        if (backgroundUrl != null && !backgroundUrl.isBlank()) {
            return backgroundUrl;
        }
        return extractLayerImageUrl(referenceOverlayLayer);
    }

    private String extractLayerImageUrl(JsonNode layer) {
        if (layer == null || layer.isNull() || layer.isMissingNode()) {
            return null;
        }
        for (String key : new String[]{"imageUrl", "image", "path", "src", "url", "file", "imagePath", "assetPath", "backgroundUrl"}) {
            JsonNode value = layer.get(key);
            if (value != null && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private void cacheOpaqueLayers() {
        this.referenceOverlayLayerMap = frozenMap(this.referenceOverlayLayer);
        this.terrainLayerMap = frozenMap(this.terrainLayer);
        this.wallLayerMap = frozenMap(this.wallLayer);
        this.fogSettingsMap = frozenMap(this.fogSettings);
    }

    private Map<String, Object> frozenMap(JsonNode node) {
        Map<String, Object> map = JsonPayloads.toMap(node);
        if (map.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private void replaceTokens(List<TokenDto> source) {
        tokens.clear();
        for (TokenDto token : safeList(source)) {
            if (token != null && token.getId() != null) {
                tokens.put(token.getId(), token);
            }
        }
    }

    private void replaceObjects(List<MapObjectDto> source) {
        objects.clear();
        for (MapObjectDto obj : safeList(source)) {
            if (obj != null && obj.getId() != null) {
                objects.put(obj.getId(), obj);
            }
        }
    }

    private void replacePlayers(List<PlayerDto> source) {
        players.clear();
        for (PlayerDto player : safeList(source)) {
            if (player != null && player.getId() != null) {
                players.put(player.getId(), player);
            }
        }
    }

    private void clampPendingPlaceCell() {
        int maxCol = Math.max(0, grid.getCols() - 1);
        int maxRow = Math.max(0, grid.getRows() - 1);
        pendingPlaceCol = Math.max(0, Math.min(pendingPlaceCol, maxCol));
        pendingPlaceRow = Math.max(0, Math.min(pendingPlaceRow, maxRow));
    }

    private static GridConfig copyGrid(GridConfig source) {
        GridConfig in = source != null ? source : DEFAULT_GRID;
        GridConfig copy = new GridConfig();
        copy.setCellSize(Math.max(1, in != null ? in.getCellSize() : 64));
        copy.setCols(Math.max(1, in != null ? in.getCols() : 20));
        copy.setRows(Math.max(1, in != null ? in.getRows() : 20));
        copy.setOffsetX(Math.max(0, in != null ? in.getOffsetX() : 0));
        copy.setOffsetY(Math.max(0, in != null ? in.getOffsetY() : 0));
        return copy;
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    @SuppressWarnings("unchecked")
    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
