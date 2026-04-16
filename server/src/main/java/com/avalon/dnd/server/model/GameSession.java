package com.avalon.dnd.server.model;

import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.InitiativeStateDto;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class GameSession {

    private String id;
    private GridConfig grid = new GridConfig(64, 20, 20);
    private String backgroundUrl;
    private final AtomicLong version = new AtomicLong(0);

    // Opaque editor metadata preserved for save/load/import round-trips.
    private Object referenceOverlayLayer;
    private Object terrainLayer;
    private Object wallLayer;
    private Object fogSettings;
    private List<String> assetPackIds = new CopyOnWriteArrayList<>();

    /** Текущее состояние инициативы (null = не активна). */
    private InitiativeStateDto initiativeState;

    private Map<String, Player>    players = new ConcurrentHashMap<>();
    private Map<String, Token>     tokens  = new ConcurrentHashMap<>();
    private Map<String, MapObject> objects = new ConcurrentHashMap<>();

    public GameSession(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public Map<String, Player>    getPlayers() { return players; }
    public Map<String, Token>     getTokens()  { return tokens; }
    public Map<String, MapObject> getObjects() { return objects; }

    public GridConfig getGrid() { return grid; }
    public void setGrid(GridConfig g) { this.grid = g; }

    public String getBackgroundUrl() { return backgroundUrl; }
    public void setBackgroundUrl(String url) { this.backgroundUrl = url; }

    public Object getReferenceOverlayLayer() { return referenceOverlayLayer; }
    public void setReferenceOverlayLayer(Object referenceOverlayLayer) { this.referenceOverlayLayer = referenceOverlayLayer; }

    public Object getTerrainLayer() { return terrainLayer; }
    public void setTerrainLayer(Object terrainLayer) { this.terrainLayer = terrainLayer; }

    public Object getWallLayer() { return wallLayer; }
    public void setWallLayer(Object wallLayer) { this.wallLayer = wallLayer; }

    public Object getFogSettings() { return fogSettings; }
    public void setFogSettings(Object fogSettings) { this.fogSettings = fogSettings; }

    public List<String> getAssetPackIds() { return assetPackIds; }
    public void setAssetPackIds(List<String> assetPackIds) {
        this.assetPackIds = assetPackIds == null ? new CopyOnWriteArrayList<>() : new CopyOnWriteArrayList<>(assetPackIds);
    }

    public InitiativeStateDto getInitiativeState() { return initiativeState; }
    public void setInitiativeState(InitiativeStateDto s) { this.initiativeState = s; }

    public long getVersion() { return version.get(); }
    public long incrementVersion() { return version.incrementAndGet(); }
}
