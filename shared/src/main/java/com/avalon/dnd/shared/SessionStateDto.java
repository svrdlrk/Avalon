package com.avalon.dnd.shared;

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

    // Optional editor metadata (opaque to battle clients).
    private Object referenceOverlayLayer;
    private Object terrainLayer;
    private Object wallLayer;
    private Object fogSettings;
    private List<String> assetPackIds = new ArrayList<>();

    public SessionStateDto() {}

    public SessionStateDto(String myPlayerId,
                           GridConfig grid,
                           List<TokenDto> tokens,
                           List<PlayerDto> players,
                           List<MapObjectDto> objects,
                           String backgroundUrl) {
        this(myPlayerId, grid, tokens, players, objects, backgroundUrl, null, null, null, null, null, null);
    }

    public SessionStateDto(String myPlayerId,
                           GridConfig grid,
                           List<TokenDto> tokens,
                           List<PlayerDto> players,
                           List<MapObjectDto> objects,
                           String backgroundUrl,
                           InitiativeStateDto initiative) {
        this(myPlayerId, grid, tokens, players, objects, backgroundUrl, initiative, null, null, null, null, null);
    }

    public SessionStateDto(String myPlayerId,
                           GridConfig grid,
                           List<TokenDto> tokens,
                           List<PlayerDto> players,
                           List<MapObjectDto> objects,
                           String backgroundUrl,
                           InitiativeStateDto initiative,
                           Object referenceOverlayLayer,
                           Object terrainLayer,
                           Object wallLayer,
                           Object fogSettings,
                           List<String> assetPackIds) {
        this.myPlayerId = myPlayerId;
        this.grid = grid;
        this.tokens = tokens;
        this.players = players;
        this.objects = objects;
        this.backgroundUrl = backgroundUrl;
        this.initiative = initiative;
        this.referenceOverlayLayer = referenceOverlayLayer;
        this.terrainLayer = terrainLayer;
        this.wallLayer = wallLayer;
        this.fogSettings = fogSettings;
        setAssetPackIds(assetPackIds);
    }

    public String getMyPlayerId() { return myPlayerId; }
    public GridConfig getGrid() { return grid; }
    public List<TokenDto> getTokens() { return tokens; }
    public List<PlayerDto> getPlayers() { return players; }
    public List<MapObjectDto> getObjects() { return objects; }
    public String getBackgroundUrl() { return backgroundUrl; }
    public InitiativeStateDto getInitiative() { return initiative; }
    public Object getReferenceOverlayLayer() { return referenceOverlayLayer; }
    public Object getTerrainLayer() { return terrainLayer; }
    public Object getWallLayer() { return wallLayer; }
    public Object getFogSettings() { return fogSettings; }
    public List<String> getAssetPackIds() { return assetPackIds; }

    public void setMyPlayerId(String v) { this.myPlayerId = v; }
    public void setGrid(GridConfig v) { this.grid = v; }
    public void setTokens(List<TokenDto> v) { this.tokens = v; }
    public void setPlayers(List<PlayerDto> v) { this.players = v; }
    public void setObjects(List<MapObjectDto> v) { this.objects = v; }
    public void setBackgroundUrl(String v) { this.backgroundUrl = v; }
    public void setInitiative(InitiativeStateDto v) { this.initiative = v; }
    public void setReferenceOverlayLayer(Object v) { this.referenceOverlayLayer = v; }
    public void setTerrainLayer(Object v) { this.terrainLayer = v; }
    public void setWallLayer(Object v) { this.wallLayer = v; }
    public void setFogSettings(Object v) { this.fogSettings = v; }
    public void setAssetPackIds(List<String> assetPackIds) {
        this.assetPackIds.clear();
        if (assetPackIds != null) this.assetPackIds.addAll(assetPackIds);
    }
}
