package com.avalon.dnd.server.model;

import com.avalon.dnd.shared.GridConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Import DTO matching map-editor export JSON.
 * Complex layer objects are kept opaque so the server can preserve them
 * without depending on the map-editor module.
 */
public class MapEditorProjectImportDto {

    private int schemaVersion = 1;
    private String id;
    private String name;
    private String description;
    private GridConfig grid;
    private Object backgroundLayer;
    private Object referenceOverlayLayer;
    private Object terrainLayer;
    private Object wallLayer;
    private Object fogSettings;
    private List<String> assetPackIds = new ArrayList<>();
    private List<Object> layers = new ArrayList<>();
    private List<PlacementDto> placements = new ArrayList<>();

    public MapEditorProjectImportDto() {}

    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public GridConfig getGrid() { return grid; }
    public void setGrid(GridConfig grid) { this.grid = grid; }
    public Object getBackgroundLayer() { return backgroundLayer; }
    public void setBackgroundLayer(Object backgroundLayer) { this.backgroundLayer = backgroundLayer; }
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
        this.assetPackIds = assetPackIds == null ? new ArrayList<>() : new ArrayList<>(assetPackIds);
    }
    public List<Object> getLayers() { return layers; }
    public void setLayers(List<Object> layers) {
        this.layers = layers == null ? new ArrayList<>() : new ArrayList<>(layers);
    }
    public List<PlacementDto> getPlacements() { return placements; }
    public void setPlacements(List<PlacementDto> placements) {
        this.placements = placements == null ? new ArrayList<>() : new ArrayList<>(placements);
    }

    public static class PlacementDto {
        private String id;
        private String kind;
        private String assetId;
        private String name;
        private String layerId;
        private int col;
        private int row;
        private int width = 1;
        private int height = 1;
        private int gridSize = 1;
        private double rotation;
        private boolean blocksMovement;
        private boolean blocksSight;
        private String imageUrl;
        private boolean selected;
        private boolean locked;

        public PlacementDto() {}

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getKind() { return kind; }
        public void setKind(String kind) { this.kind = kind; }
        public String getAssetId() { return assetId; }
        public void setAssetId(String assetId) { this.assetId = assetId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getLayerId() { return layerId; }
        public void setLayerId(String layerId) { this.layerId = layerId; }
        public int getCol() { return col; }
        public void setCol(int col) { this.col = col; }
        public int getRow() { return row; }
        public void setRow(int row) { this.row = row; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
        public int getGridSize() { return gridSize; }
        public void setGridSize(int gridSize) { this.gridSize = gridSize; }
        public double getRotation() { return rotation; }
        public void setRotation(double rotation) { this.rotation = rotation; }
        public boolean isBlocksMovement() { return blocksMovement; }
        public void setBlocksMovement(boolean blocksMovement) { this.blocksMovement = blocksMovement; }
        public boolean isBlocksSight() { return blocksSight; }
        public void setBlocksSight(boolean blocksSight) { this.blocksSight = blocksSight; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
        public boolean isLocked() { return locked; }
        public void setLocked(boolean locked) { this.locked = locked; }
    }
}
