package com.avalon.dnd.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout update sent from server to clients.
 * Keeps the common battle-state fields plus optional map-editor metadata.
 */
public class MapLayoutUpdateDto {

    private GridConfig grid;
    private List<TokenDto> tokens;
    private List<MapObjectDto> objects;
    private String backgroundUrl;

    /** Optional editor metadata (opaque to battle clients). */
    private Object referenceOverlayLayer;
    private Object terrainLayer;
    private Object wallLayer;
    private Object fogSettings;
    private List<MicroLocationDto> microLocations = new ArrayList<>();
    private List<String> assetPackIds = new ArrayList<>();

    public MapLayoutUpdateDto() {}

    public MapLayoutUpdateDto(GridConfig grid, List<TokenDto> tokens, List<MapObjectDto> objects, String backgroundUrl) {
        this(grid, tokens, objects, backgroundUrl, null, null, null, null, null, null);
    }

    public MapLayoutUpdateDto(GridConfig grid,
                              List<TokenDto> tokens,
                              List<MapObjectDto> objects,
                              String backgroundUrl,
                              Object referenceOverlayLayer,
                              Object terrainLayer,
                              Object wallLayer,
                              Object fogSettings,
                              List<MicroLocationDto> microLocations,
                              List<String> assetPackIds) {
        this.grid = grid;
        this.tokens = tokens;
        this.objects = objects;
        this.backgroundUrl = backgroundUrl;
        this.referenceOverlayLayer = referenceOverlayLayer;
        this.terrainLayer = terrainLayer;
        this.wallLayer = wallLayer;
        this.fogSettings = fogSettings;
        setMicroLocations(microLocations);
        setAssetPackIds(assetPackIds);
    }

    public GridConfig getGrid() { return grid; }
    public void setGrid(GridConfig grid) { this.grid = grid; }

    public List<TokenDto> getTokens() { return tokens; }
    public void setTokens(List<TokenDto> tokens) { this.tokens = tokens; }

    public List<MapObjectDto> getObjects() { return objects; }
    public void setObjects(List<MapObjectDto> objects) { this.objects = objects; }

    public String getBackgroundUrl() { return backgroundUrl; }
    public void setBackgroundUrl(String backgroundUrl) { this.backgroundUrl = backgroundUrl; }

    public Object getReferenceOverlayLayer() { return referenceOverlayLayer; }
    public void setReferenceOverlayLayer(Object referenceOverlayLayer) { this.referenceOverlayLayer = referenceOverlayLayer; }

    public Object getTerrainLayer() { return terrainLayer; }
    public void setTerrainLayer(Object terrainLayer) { this.terrainLayer = terrainLayer; }

    public Object getWallLayer() { return wallLayer; }
    public void setWallLayer(Object wallLayer) { this.wallLayer = wallLayer; }

    public Object getFogSettings() { return fogSettings; }
    public void setFogSettings(Object fogSettings) { this.fogSettings = fogSettings; }

    public List<MicroLocationDto> getMicroLocations() { return microLocations; }
    public void setMicroLocations(List<MicroLocationDto> microLocations) {
        this.microLocations.clear();
        if (microLocations != null) this.microLocations.addAll(microLocations);
    }

    public List<String> getAssetPackIds() { return assetPackIds; }
    public void setAssetPackIds(List<String> assetPackIds) {
        this.assetPackIds.clear();
        if (assetPackIds != null) this.assetPackIds.addAll(assetPackIds);
    }
}
