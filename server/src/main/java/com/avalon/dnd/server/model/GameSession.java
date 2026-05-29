package com.avalon.dnd.server.model;

import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.InitiativeStateDto;
import com.avalon.dnd.shared.MicroLocationDto;
import com.avalon.dnd.shared.VisibilityStateDto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class GameSession {

    private String id;
    private String dmSecret;
    private GridConfig grid = new GridConfig(64, 20, 20);
    private String backgroundUrl;
    private final AtomicLong version = new AtomicLong(0);

    // Opaque editor metadata preserved for save/load/import round-trips.
    private JsonNode referenceOverlayLayer;
    private JsonNode terrainLayer;
    private JsonNode wallLayer;
    private JsonNode fogSettings;
    private List<MicroLocationDto> microLocations = new CopyOnWriteArrayList<>();
    private List<String> assetPackIds = new CopyOnWriteArrayList<>();

    /** Текущее состояние инициативы (null = не активна). */
    private InitiativeStateDto initiativeState;
    private VisibilityStateDto visibilityState;
    private VisibilityStateDto sharedVisibilityState;
    private java.util.List<com.avalon.dnd.shared.VisibilityShareSuggestionDto> visibilityShareSuggestions = new CopyOnWriteArrayList<>();
    private Map<String, VisibilityStateDto> visibilityStatesByPlayer = new ConcurrentHashMap<>();
    private volatile boolean visibilityDirty = true;

    private Map<String, Player>    players = new ConcurrentHashMap<>();
    private Map<String, Token>     tokens  = new ConcurrentHashMap<>();
    private Map<String, MapObject> objects = new ConcurrentHashMap<>();

    public GameSession(String id) {
        this.id = id;
        this.dmSecret = java.util.UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public String getDmSecret() { return dmSecret; }
    public void setDmSecret(String dmSecret) {
        this.dmSecret = dmSecret == null || dmSecret.isBlank()
                ? java.util.UUID.randomUUID().toString()
                : dmSecret;
    }

    public Map<String, Player>    getPlayers() { return players; }
    public Map<String, Token>     getTokens()  { return tokens; }
    public Map<String, MapObject> getObjects() { return objects; }

    public GridConfig getGrid() { return grid; }
    public void setGrid(GridConfig g) {
        if (sameGrid(this.grid, g)) {
            return;
        }
        this.grid = g;
        markVisibilityDirty();
    }

    public String getBackgroundUrl() { return backgroundUrl; }
    public void setBackgroundUrl(String url) { this.backgroundUrl = url; }

    public JsonNode getReferenceOverlayLayer() { return referenceOverlayLayer; }
    @JsonIgnore
    public void setReferenceOverlayLayer(Object referenceOverlayLayer) {
        JsonNode normalized = com.avalon.dnd.shared.JsonPayloads.toNode(referenceOverlayLayer);
        if (Objects.equals(this.referenceOverlayLayer, normalized)) {
            return;
        }
        this.referenceOverlayLayer = normalized;
        markVisibilityDirty();
    }
    public void setReferenceOverlayLayer(JsonNode referenceOverlayLayer) {
        if (Objects.equals(this.referenceOverlayLayer, referenceOverlayLayer)) {
            return;
        }
        this.referenceOverlayLayer = referenceOverlayLayer;
        markVisibilityDirty();
    }

    public JsonNode getTerrainLayer() { return terrainLayer; }
    @JsonIgnore
    public void setTerrainLayer(Object terrainLayer) {
        JsonNode normalized = com.avalon.dnd.shared.JsonPayloads.toNode(terrainLayer);
        if (Objects.equals(this.terrainLayer, normalized)) {
            return;
        }
        this.terrainLayer = normalized;
        markVisibilityDirty();
    }
    public void setTerrainLayer(JsonNode terrainLayer) {
        if (Objects.equals(this.terrainLayer, terrainLayer)) {
            return;
        }
        this.terrainLayer = terrainLayer;
        markVisibilityDirty();
    }

    public JsonNode getWallLayer() { return wallLayer; }
    @JsonIgnore
    public void setWallLayer(Object wallLayer) {
        JsonNode normalized = com.avalon.dnd.shared.JsonPayloads.toNode(wallLayer);
        if (Objects.equals(this.wallLayer, normalized)) {
            return;
        }
        this.wallLayer = normalized;
        markVisibilityDirty();
    }
    public void setWallLayer(JsonNode wallLayer) {
        if (Objects.equals(this.wallLayer, wallLayer)) {
            return;
        }
        this.wallLayer = wallLayer;
        markVisibilityDirty();
    }

    public JsonNode getFogSettings() { return fogSettings; }
    @JsonIgnore
    public void setFogSettings(Object fogSettings) {
        JsonNode normalized = com.avalon.dnd.shared.JsonPayloads.toNode(fogSettings);
        if (Objects.equals(this.fogSettings, normalized)) {
            return;
        }
        this.fogSettings = normalized;
        markVisibilityDirty();
    }
    public void setFogSettings(JsonNode fogSettings) {
        if (Objects.equals(this.fogSettings, fogSettings)) {
            return;
        }
        this.fogSettings = fogSettings;
        markVisibilityDirty();
    }

    public List<MicroLocationDto> getMicroLocations() { return microLocations; }
    public void setMicroLocations(List<MicroLocationDto> microLocations) {
        List<MicroLocationDto> normalized = microLocations == null ? new CopyOnWriteArrayList<>() : new CopyOnWriteArrayList<>(microLocations);
        if (sameMicroLocations(this.microLocations, normalized)) {
            return;
        }
        this.microLocations = normalized;
    }

    public List<String> getAssetPackIds() { return assetPackIds; }
    public void setAssetPackIds(List<String> assetPackIds) {
        List<String> normalized = assetPackIds == null ? new CopyOnWriteArrayList<>() : new CopyOnWriteArrayList<>(assetPackIds);
        if (Objects.equals(this.assetPackIds, normalized)) {
            return;
        }
        this.assetPackIds = normalized;
    }

    public InitiativeStateDto getInitiativeState() { return initiativeState; }
    public void setInitiativeState(InitiativeStateDto s) {
        if (Objects.equals(this.initiativeState, s)) {
            return;
        }
        this.initiativeState = s;
    }

    public VisibilityStateDto getVisibilityState() { return visibilityState; }
    public void setVisibilityState(VisibilityStateDto visibilityState) {
        if (Objects.equals(this.visibilityState, visibilityState)) {
            return;
        }
        this.visibilityState = visibilityState;
    }

    public VisibilityStateDto getSharedVisibilityState() { return sharedVisibilityState; }
    public void setSharedVisibilityState(VisibilityStateDto sharedVisibilityState) {
        if (Objects.equals(this.sharedVisibilityState, sharedVisibilityState)) {
            return;
        }
        this.sharedVisibilityState = sharedVisibilityState;
    }

    public java.util.List<com.avalon.dnd.shared.VisibilityShareSuggestionDto> getVisibilityShareSuggestions() { return visibilityShareSuggestions; }
    public void setVisibilityShareSuggestions(java.util.List<com.avalon.dnd.shared.VisibilityShareSuggestionDto> suggestions) {
        java.util.List<com.avalon.dnd.shared.VisibilityShareSuggestionDto> normalized = suggestions == null ? new CopyOnWriteArrayList<>() : new CopyOnWriteArrayList<>(suggestions);
        if (sameVisibilityShareSuggestions(this.visibilityShareSuggestions, normalized)) {
            return;
        }
        this.visibilityShareSuggestions = normalized;
    }

    public Map<String, VisibilityStateDto> getVisibilityStatesByPlayer() { return visibilityStatesByPlayer; }
    public void setVisibilityStatesByPlayer(Map<String, VisibilityStateDto> visibilityStatesByPlayer) {
        Map<String, VisibilityStateDto> normalized = visibilityStatesByPlayer == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(visibilityStatesByPlayer);
        if (Objects.equals(this.visibilityStatesByPlayer, normalized)) {
            return;
        }
        this.visibilityStatesByPlayer = normalized;
    }

    public boolean isVisibilityDirty() { return visibilityDirty; }
    public void markVisibilityDirty() { this.visibilityDirty = true; }
    public void clearVisibilityDirty() { this.visibilityDirty = false; }

    private static boolean sameMicroLocations(List<MicroLocationDto> a, List<MicroLocationDto> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!sameMicroLocation(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameMicroLocation(MicroLocationDto a, MicroLocationDto b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getId(), b.getId())
                && Objects.equals(a.getName(), b.getName())
                && a.getCol() == b.getCol()
                && a.getRow() == b.getRow()
                && a.getWidth() == b.getWidth()
                && a.getHeight() == b.getHeight()
                && a.isLocked() == b.isLocked()
                && Objects.equals(a.getHint(), b.getHint())
                && Objects.equals(a.getInteriorMapPath(), b.getInteriorMapPath());
    }

    private static boolean sameVisibilityShareSuggestions(List<com.avalon.dnd.shared.VisibilityShareSuggestionDto> a,
                                                           List<com.avalon.dnd.shared.VisibilityShareSuggestionDto> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!sameVisibilityShareSuggestion(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameVisibilityShareSuggestion(com.avalon.dnd.shared.VisibilityShareSuggestionDto a,
                                                         com.avalon.dnd.shared.VisibilityShareSuggestionDto b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getSuggestionId(), b.getSuggestionId())
                && Objects.equals(a.getPlayerIds(), b.getPlayerIds())
                && Objects.equals(a.getReason(), b.getReason())
                && a.isAutoSuggested() == b.isAutoSuggested()
                && Objects.equals(a.getTrigger(), b.getTrigger());
    }

    private static boolean sameGrid(GridConfig a, GridConfig b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getCellSize() == b.getCellSize()
                && a.getCols() == b.getCols()
                && a.getRows() == b.getRows()
                && a.getOffsetX() == b.getOffsetX()
                && a.getOffsetY() == b.getOffsetY();
    }

    public long getVersion() { return version.get(); }
    public void setVersion(long value) { this.version.set(Math.max(0L, value)); }
    public long incrementVersion() { return version.incrementAndGet(); }
}
