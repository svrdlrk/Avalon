package com.avalon.dnd.dm.model;

import com.avalon.dnd.shared.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientState {

    private static final ClientState INSTANCE = new ClientState();
    public static ClientState getInstance() { return INSTANCE; }

    private String sessionId;
    private String playerId;
    private GridConfig grid = new GridConfig(64, 20, 20);
    private String backgroundUrl;
    private Object referenceOverlayLayer;
    private Object terrainLayer;
    private Object wallLayer;
    private Object fogSettings;
    private java.util.List<MicroLocationDto> microLocations = new java.util.concurrent.CopyOnWriteArrayList<>();
    private java.util.List<String> assetPackIds = new java.util.concurrent.CopyOnWriteArrayList<>();

    private final Map<String, TokenDto>     tokens  = new ConcurrentHashMap<>();
    private final Map<String, MapObjectDto> objects = new ConcurrentHashMap<>();
    private final Map<String, PlayerDto>    players = new ConcurrentHashMap<>();

    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.atomic.AtomicBoolean notifyPending = new java.util.concurrent.atomic.AtomicBoolean(false);

    private int pendingPlaceCol;
    private int pendingPlaceRow;

    private ClientState() {}

    public void applyState(SessionStateDto state, String sessionId, String playerId) {
        this.sessionId     = sessionId;
        this.playerId      = playerId;
        this.grid          = state.getGrid();
        this.backgroundUrl = state.getBackgroundUrl();
        this.referenceOverlayLayer = state.getReferenceOverlayLayer();
        this.terrainLayer = state.getTerrainLayer();
        this.wallLayer = state.getWallLayer();
        this.fogSettings = state.getFogSettings();
        this.microLocations = new java.util.concurrent.CopyOnWriteArrayList<>(state.getMicroLocations() == null ? java.util.List.of() : state.getMicroLocations());
        this.assetPackIds = new java.util.concurrent.CopyOnWriteArrayList<>(state.getAssetPackIds() == null ? java.util.List.of() : state.getAssetPackIds());
        tokens.clear();  state.getTokens().forEach(t -> tokens.put(t.getId(), t));
        objects.clear(); state.getObjects().forEach(o -> objects.put(o.getId(), o));
        players.clear(); state.getPlayers().forEach(p -> players.put(p.getId(), p));
        notifyMapChanged();
    }

    public void applyMapLayoutUpdate(MapLayoutUpdateDto dto) {
        this.grid          = dto.getGrid();
        this.backgroundUrl = dto.getBackgroundUrl();
        this.referenceOverlayLayer = dto.getReferenceOverlayLayer();
        this.terrainLayer = dto.getTerrainLayer();
        this.wallLayer = dto.getWallLayer();
        this.fogSettings = dto.getFogSettings();
        this.assetPackIds = new java.util.concurrent.CopyOnWriteArrayList<>(dto.getAssetPackIds() == null ? java.util.List.of() : dto.getAssetPackIds());
        tokens.clear();  dto.getTokens().forEach(t -> tokens.put(t.getId(), t));
        objects.clear();
        if (dto.getObjects() != null) dto.getObjects().forEach(o -> objects.put(o.getId(), o));
        pendingPlaceCol = Math.min(pendingPlaceCol, Math.max(0, grid.getCols() - 1));
        pendingPlaceRow = Math.min(pendingPlaceRow, Math.max(0, grid.getRows() - 1));
        notifyMapChanged();
    }

    public void moveToken(TokenDto token)   { tokens.put(token.getId(), token); notifyMapChanged(); }
    public void addToken(TokenDto token)    { tokens.put(token.getId(), token); notifyMapChanged(); }
    public void removeToken(String id)      { tokens.remove(id);  notifyMapChanged(); }
    public void addObject(MapObjectDto obj) { objects.put(obj.getId(), obj); notifyMapChanged(); }
    public void removeObject(String id)     { objects.remove(id); notifyMapChanged(); }

    /** FIX: добавляем игрока при PLAYER_JOINED. */
    public void addPlayer(PlayerDto player) {
        players.put(player.getId(), player);
        notifyMapChanged();
    }

    /** FIX: убираем игрока при PLAYER_LEFT. */
    public void removePlayer(String playerId) {
        players.remove(playerId);
        notifyMapChanged();
    }

    public void setPendingPlaceCell(int col, int row) {
        this.pendingPlaceCol = col;
        this.pendingPlaceRow = row;
        notifyMapChanged();
    }

    public int getPendingPlaceCol() { return pendingPlaceCol; }
    public int getPendingPlaceRow() { return pendingPlaceRow; }

    public void addChangeListener(Runnable r)    { changeListeners.add(r); }
    public void removeChangeListener(Runnable r) { changeListeners.remove(r); }

    public void notifyMapChanged() {
        if (!notifyPending.compareAndSet(false, true)) {
            return;
        }
        javafx.application.Platform.runLater(() -> {
            try {
                for (Runnable r : changeListeners) {
                    r.run();
                }
            } finally {
                notifyPending.set(false);
            }
        });
    }

    public String getSessionId()                 { return sessionId; }
    public String getPlayerId()                  { return playerId; }
    public GridConfig getGrid()                  { return grid; }
    public Map<String, TokenDto>     getTokens() { return tokens; }
    public Map<String, MapObjectDto> getObjects(){ return objects; }
    public Map<String, PlayerDto>    getPlayers(){ return players; }
    public String getBackgroundUrl()             { return backgroundUrl; }

    public void setBackgroundUrl(String url) {
        this.backgroundUrl = url;
        notifyMapChanged();
    }

    public Object getReferenceOverlayLayer() { return referenceOverlayLayer; }
    public void setReferenceOverlayLayer(Object value) { this.referenceOverlayLayer = value; }

    public Object getTerrainLayer() { return terrainLayer; }
    public void setTerrainLayer(Object value) { this.terrainLayer = value; }

    public Object getWallLayer() { return wallLayer; }
    public void setWallLayer(Object value) { this.wallLayer = value; }

    public Object getFogSettings() { return fogSettings; }
    public void setFogSettings(Object value) { this.fogSettings = value; }

    public java.util.List<MicroLocationDto> getMicroLocations() { return microLocations; }
    public void setMicroLocations(java.util.List<MicroLocationDto> value) {
        this.microLocations = value == null ? new java.util.concurrent.CopyOnWriteArrayList<>() : new java.util.concurrent.CopyOnWriteArrayList<>(value);
    }

    public List<String> getAssetPackIds() { return assetPackIds; }
    public void setAssetPackIds(List<String> value) {
        this.assetPackIds = value == null ? new ArrayList<>() : new ArrayList<>(value);
    }
}