package com.avalon.dnd.shared;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

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
    private VisibilityStateDto visibility;

    /** Optional editor metadata (opaque to battle clients). */
    private JsonNode referenceOverlayLayer;
    private JsonNode terrainLayer;
    private JsonNode wallLayer;
    private JsonNode fogSettings;
    private List<MicroLocationDto> microLocations = new ArrayList<>();
    private List<String> assetPackIds = new ArrayList<>();

    public MapLayoutUpdateDto() {}

    public MapLayoutUpdateDto(GridConfig grid, List<TokenDto> tokens, List<MapObjectDto> objects, String backgroundUrl) {
        this(grid, tokens, objects, backgroundUrl, null, (com.fasterxml.jackson.databind.JsonNode) null, (com.fasterxml.jackson.databind.JsonNode) null, (com.fasterxml.jackson.databind.JsonNode) null, (com.fasterxml.jackson.databind.JsonNode) null, null, null);
    }

    public MapLayoutUpdateDto(GridConfig grid,
                              List<TokenDto> tokens,
                              List<MapObjectDto> objects,
                              String backgroundUrl,
                              VisibilityStateDto visibility,
                              Object referenceOverlayLayer,
                              Object terrainLayer,
                              Object wallLayer,
                              Object fogSettings,
                              List<MicroLocationDto> microLocations,
                              List<String> assetPackIds) {
        this(grid, tokens, objects, backgroundUrl, visibility,
                JsonPayloads.toNode(referenceOverlayLayer),
                JsonPayloads.toNode(terrainLayer),
                JsonPayloads.toNode(wallLayer),
                JsonPayloads.toNode(fogSettings),
                microLocations,
                assetPackIds);
    }

    public MapLayoutUpdateDto(GridConfig grid,
                              List<TokenDto> tokens,
                              List<MapObjectDto> objects,
                              String backgroundUrl,
                              VisibilityStateDto visibility,
                              JsonNode referenceOverlayLayer,
                              JsonNode terrainLayer,
                              JsonNode wallLayer,
                              JsonNode fogSettings,
                              List<MicroLocationDto> microLocations,
                              List<String> assetPackIds) {
        this.grid = grid;
        this.tokens = tokens;
        this.objects = objects;
        this.backgroundUrl = backgroundUrl;
        this.visibility = visibility;
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

    public VisibilityStateDto getVisibility() { return visibility; }
    public void setVisibility(VisibilityStateDto visibility) { this.visibility = visibility; }

    public JsonNode getReferenceOverlayLayer() { return referenceOverlayLayer; }
    @JsonIgnore
    public void setReferenceOverlayLayer(Object referenceOverlayLayer) { this.referenceOverlayLayer = JsonPayloads.toNode(referenceOverlayLayer); }
    public void setReferenceOverlayLayer(JsonNode referenceOverlayLayer) { this.referenceOverlayLayer = referenceOverlayLayer; }

    public JsonNode getTerrainLayer() { return terrainLayer; }
    @JsonIgnore
    public void setTerrainLayer(Object terrainLayer) { this.terrainLayer = JsonPayloads.toNode(terrainLayer); }
    public void setTerrainLayer(JsonNode terrainLayer) { this.terrainLayer = terrainLayer; }

    public JsonNode getWallLayer() { return wallLayer; }
    @JsonIgnore
    public void setWallLayer(Object wallLayer) { this.wallLayer = JsonPayloads.toNode(wallLayer); }
    public void setWallLayer(JsonNode wallLayer) { this.wallLayer = wallLayer; }

    public JsonNode getFogSettings() { return fogSettings; }
    @JsonIgnore
    public void setFogSettings(Object fogSettings) { this.fogSettings = JsonPayloads.toNode(fogSettings); }
    public void setFogSettings(JsonNode fogSettings) { this.fogSettings = fogSettings; }

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
