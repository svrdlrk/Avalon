package com.avalon.dnd.mapeditor.model;

import com.avalon.dnd.shared.GridConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable export format for DM-client/battle use.
 * Keeps the full editor project structure, but behind a dedicated schema version.
 */
public class BattleProjectExportDto {

    private int schemaVersion = 1;
    private String id;
    private String name;
    private String description;
    private GridConfig grid = new GridConfig(64, 40, 30);
    private BackgroundLayer backgroundLayer = new BackgroundLayer();
    private ReferenceOverlayLayer referenceOverlayLayer = new ReferenceOverlayLayer();
    private TerrainLayer terrainLayer = new TerrainLayer();
    private WallLayer wallLayer = new WallLayer();
    private FogSettings fogSettings = new FogSettings();
    private final List<MapLayer> layers = new ArrayList<>();
    private final List<MapPlacement> placements = new ArrayList<>();
    private final List<String> assetPackIds = new ArrayList<>();

    public BattleProjectExportDto() {}

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
    public List<MapLayer> getLayers() { return layers; }
    public List<MapPlacement> getPlacements() { return placements; }
    public List<String> getAssetPackIds() { return assetPackIds; }

    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setGrid(GridConfig grid) { this.grid = grid == null ? new GridConfig(64, 40, 30) : grid; }
    public void setBackgroundLayer(BackgroundLayer backgroundLayer) { this.backgroundLayer = backgroundLayer == null ? new BackgroundLayer() : backgroundLayer; }
    public void setReferenceOverlayLayer(ReferenceOverlayLayer referenceOverlayLayer) { this.referenceOverlayLayer = referenceOverlayLayer == null ? new ReferenceOverlayLayer() : referenceOverlayLayer; }
    public void setTerrainLayer(TerrainLayer terrainLayer) { this.terrainLayer = terrainLayer == null ? new TerrainLayer() : terrainLayer; }
    public void setWallLayer(WallLayer wallLayer) { this.wallLayer = wallLayer == null ? new WallLayer() : wallLayer; }
    public void setFogSettings(FogSettings fogSettings) { this.fogSettings = fogSettings == null ? new FogSettings() : fogSettings; }
    public void setLayers(List<MapLayer> layers) { this.layers.clear(); if (layers != null) this.layers.addAll(layers); }
    public void setPlacements(List<MapPlacement> placements) { this.placements.clear(); if (placements != null) this.placements.addAll(placements); }
    public void setAssetPackIds(List<String> assetPackIds) { this.assetPackIds.clear(); if (assetPackIds != null) this.assetPackIds.addAll(assetPackIds); }
}
