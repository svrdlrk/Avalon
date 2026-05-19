package com.avalon.dnd.shared;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class SessionStateDto {

    private String myPlayerId;
    private GridConfig grid;
    private List<TokenDto> tokens;
    private List<PlayerDto> players;
    private List<MapObjectDto> objects;
    private String backgroundUrl;
    private InitiativeStateDto initiative;
    private VisibilityStateDto visibility;
    private java.util.List<VisibilityShareSuggestionDto> visibilityShareSuggestions = new java.util.ArrayList<>();

    // Optional editor metadata (opaque to battle clients).
    private JsonNode referenceOverlayLayer;
    private JsonNode terrainLayer;
    private JsonNode wallLayer;
    private JsonNode fogSettings;
    private List<MicroLocationDto> microLocations = new ArrayList<>();
    private List<String> assetPackIds = new ArrayList<>();

    public SessionStateDto() {}

    public SessionStateDto(String myPlayerId,
                           GridConfig grid,
                           List<TokenDto> tokens,
                           List<PlayerDto> players,
                           List<MapObjectDto> objects,
                           String backgroundUrl) {
        this(myPlayerId, grid, tokens, players, objects, backgroundUrl, null, null, (com.fasterxml.jackson.databind.JsonNode) null, (com.fasterxml.jackson.databind.JsonNode) null, (com.fasterxml.jackson.databind.JsonNode) null, (com.fasterxml.jackson.databind.JsonNode) null, null, null, null);
    }

    public SessionStateDto(String myPlayerId,
                           GridConfig grid,
                           List<TokenDto> tokens,
                           List<PlayerDto> players,
                           List<MapObjectDto> objects,
                           String backgroundUrl,
                           InitiativeStateDto initiative) {
        this(myPlayerId, grid, tokens, players, objects, backgroundUrl, initiative, null, (com.fasterxml.jackson.databind.JsonNode) null, (com.fasterxml.jackson.databind.JsonNode) null, (com.fasterxml.jackson.databind.JsonNode) null, (com.fasterxml.jackson.databind.JsonNode) null, null, null, null);
    }

    public SessionStateDto(String myPlayerId,
                           GridConfig grid,
                           List<TokenDto> tokens,
                           List<PlayerDto> players,
                           List<MapObjectDto> objects,
                           String backgroundUrl,
                           InitiativeStateDto initiative,
                           VisibilityStateDto visibility,
                           Object referenceOverlayLayer,
                           Object terrainLayer,
                           Object wallLayer,
                           Object fogSettings,
                           List<MicroLocationDto> microLocations,
                           List<String> assetPackIds,
                           java.util.List<VisibilityShareSuggestionDto> visibilityShareSuggestions) {
        this(myPlayerId, grid, tokens, players, objects, backgroundUrl, initiative, visibility,
                JsonPayloads.toNode(referenceOverlayLayer),
                JsonPayloads.toNode(terrainLayer),
                JsonPayloads.toNode(wallLayer),
                JsonPayloads.toNode(fogSettings),
                microLocations,
                assetPackIds,
                visibilityShareSuggestions);
    }

    public SessionStateDto(String myPlayerId,
                           GridConfig grid,
                           List<TokenDto> tokens,
                           List<PlayerDto> players,
                           List<MapObjectDto> objects,
                           String backgroundUrl,
                           InitiativeStateDto initiative,
                           VisibilityStateDto visibility,
                           JsonNode referenceOverlayLayer,
                           JsonNode terrainLayer,
                           JsonNode wallLayer,
                           JsonNode fogSettings,
                           List<MicroLocationDto> microLocations,
                           List<String> assetPackIds,
                           java.util.List<VisibilityShareSuggestionDto> visibilityShareSuggestions) {
        this.myPlayerId = myPlayerId;
        this.grid = grid;
        this.tokens = tokens;
        this.players = players;
        this.objects = objects;
        this.backgroundUrl = backgroundUrl;
        this.initiative = initiative;
        this.visibility = visibility;
        this.referenceOverlayLayer = referenceOverlayLayer;
        this.terrainLayer = terrainLayer;
        this.wallLayer = wallLayer;
        this.fogSettings = fogSettings;
        setMicroLocations(microLocations);
        setAssetPackIds(assetPackIds);
        setVisibilityShareSuggestions(visibilityShareSuggestions);
    }

    public String getMyPlayerId() { return myPlayerId; }
    public GridConfig getGrid() { return grid; }
    public List<TokenDto> getTokens() { return tokens; }
    public List<PlayerDto> getPlayers() { return players; }
    public List<MapObjectDto> getObjects() { return objects; }
    public String getBackgroundUrl() { return backgroundUrl; }
    public InitiativeStateDto getInitiative() { return initiative; }
    public VisibilityStateDto getVisibility() { return visibility; }
    public JsonNode getReferenceOverlayLayer() { return referenceOverlayLayer; }
    public JsonNode getTerrainLayer() { return terrainLayer; }
    public JsonNode getWallLayer() { return wallLayer; }
    public JsonNode getFogSettings() { return fogSettings; }
    public List<MicroLocationDto> getMicroLocations() { return microLocations; }
    public List<String> getAssetPackIds() { return assetPackIds; }
    public java.util.List<VisibilityShareSuggestionDto> getVisibilityShareSuggestions() { return visibilityShareSuggestions; }

    public void setMyPlayerId(String v) { this.myPlayerId = v; }
    public void setGrid(GridConfig v) { this.grid = v; }
    public void setTokens(List<TokenDto> v) { this.tokens = v; }
    public void setPlayers(List<PlayerDto> v) { this.players = v; }
    public void setObjects(List<MapObjectDto> v) { this.objects = v; }
    public void setBackgroundUrl(String v) { this.backgroundUrl = v; }
    public void setInitiative(InitiativeStateDto v) { this.initiative = v; }
    public void setVisibility(VisibilityStateDto v) { this.visibility = v; }
    public void setReferenceOverlayLayer(Object v) { this.referenceOverlayLayer = JsonPayloads.toNode(v); }
    public void setReferenceOverlayLayer(JsonNode v) { this.referenceOverlayLayer = v; }
    public void setTerrainLayer(Object v) { this.terrainLayer = JsonPayloads.toNode(v); }
    public void setTerrainLayer(JsonNode v) { this.terrainLayer = v; }
    public void setWallLayer(Object v) { this.wallLayer = JsonPayloads.toNode(v); }
    public void setWallLayer(JsonNode v) { this.wallLayer = v; }
    public void setFogSettings(Object v) { this.fogSettings = JsonPayloads.toNode(v); }
    public void setFogSettings(JsonNode v) { this.fogSettings = v; }
    public void setMicroLocations(List<MicroLocationDto> microLocations) {
        this.microLocations.clear();
        if (microLocations != null) this.microLocations.addAll(microLocations);
    }
    public void setAssetPackIds(List<String> assetPackIds) {
        this.assetPackIds.clear();
        if (assetPackIds != null) this.assetPackIds.addAll(assetPackIds);
    }
    public void setVisibilityShareSuggestions(java.util.List<VisibilityShareSuggestionDto> visibilityShareSuggestions) {
        this.visibilityShareSuggestions.clear();
        if (visibilityShareSuggestions != null) this.visibilityShareSuggestions.addAll(visibilityShareSuggestions);
    }
}
