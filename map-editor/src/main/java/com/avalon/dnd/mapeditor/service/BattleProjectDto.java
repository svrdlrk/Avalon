package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.BackgroundLayer;
import com.avalon.dnd.mapeditor.model.FogSettings;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.model.ReferenceOverlayLayer;
import com.avalon.dnd.mapeditor.model.TerrainLayer;
import com.avalon.dnd.mapeditor.model.WallLayer;
import com.avalon.dnd.shared.GridConfig;

import java.util.ArrayList;
import java.util.List;

public class BattleProjectDto {

    private int schemaVersion = 1;
    private String id;
    private String name;
    private String description;
    private GridConfig grid;
    private BackgroundLayer backgroundLayer;
    private ReferenceOverlayLayer referenceOverlayLayer;
    private TerrainLayer terrainLayer;
    private WallLayer wallLayer;
    private FogSettings fogSettings;
    private List<String> assetPackIds = new ArrayList<>();
    private List<MapLayer> layers = new ArrayList<>();
    private List<MapPlacement> placements = new ArrayList<>();

    public BattleProjectDto() {}

    public int getSchemaVersion() { return schemaVersion; }
    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public GridConfig getGrid() { return grid; }
    public BackgroundLayer getBackgroundLayer() { return backgroundLayer; }
    public ReferenceOverlayLayer getReferenceOverlayLayer() { return referenceOverlayLayer; }
    public TerrainLayer getTerrainLayer() { return terrainLayer; }
    public WallLayer getWallLayer() { return wallLayer; }
    public FogSettings getFogSettings() { return fogSettings; }
    public List<String> getAssetPackIds() { return assetPackIds; }
    public List<MapLayer> getLayers() { return layers; }
    public List<MapPlacement> getPlacements() { return placements; }

    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setGrid(GridConfig grid) { this.grid = grid; }
    public void setBackgroundLayer(BackgroundLayer backgroundLayer) { this.backgroundLayer = backgroundLayer; }
    public void setReferenceOverlayLayer(ReferenceOverlayLayer referenceOverlayLayer) { this.referenceOverlayLayer = referenceOverlayLayer; }
    public void setTerrainLayer(TerrainLayer terrainLayer) { this.terrainLayer = terrainLayer; }
    public void setWallLayer(WallLayer wallLayer) { this.wallLayer = wallLayer; }
    public void setFogSettings(FogSettings fogSettings) { this.fogSettings = fogSettings; }
    public void setAssetPackIds(List<String> assetPackIds) {
        this.assetPackIds = assetPackIds == null ? new ArrayList<>() : new ArrayList<>(assetPackIds);
    }
    public void setLayers(List<MapLayer> layers) {
        this.layers = layers == null ? new ArrayList<>() : new ArrayList<>(layers);
    }
    public void setPlacements(List<MapPlacement> placements) {
        this.placements = placements == null ? new ArrayList<>() : new ArrayList<>(placements);
    }
}
