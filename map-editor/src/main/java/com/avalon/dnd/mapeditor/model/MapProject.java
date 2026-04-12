package com.avalon.dnd.mapeditor.model;

import com.avalon.dnd.shared.GridConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class MapProject {

    private String id;
    private String name;
    private String description;
    private String backgroundUrl; // legacy compatibility for older saves/layouts
    private BackgroundLayer backgroundLayer = new BackgroundLayer();
    private ReferenceOverlay referenceOverlay = new ReferenceOverlay();
    private TerrainLayer terrainLayer = new TerrainLayer();
    private WallLayer wallLayer = new WallLayer();
    private FogSettings fogSettings = new FogSettings();
    private GridConfig grid = new GridConfig(64, 40, 30);

    private final List<MapLayer> layers = new ArrayList<>();
    private final List<MapPlacement> placements = new ArrayList<>();

    public MapProject() {}

    public static MapProject createBlank(String id, String name) {
        MapProject project = new MapProject();
        project.setId(id == null ? UUID.randomUUID().toString() : id);
        project.setName(name == null ? "Untitled Map" : name);
        project.ensureDefaultLayers();
        return project;
    }

    public MapProject copy() {
        MapProject copy = new MapProject();
        copy.id = this.id;
        copy.name = this.name;
        copy.description = this.description;
        copy.backgroundLayer = this.backgroundLayer == null ? new BackgroundLayer() : this.backgroundLayer.copy();
        copy.referenceOverlay = this.referenceOverlay == null ? new ReferenceOverlay() : this.referenceOverlay.copy();
        copy.terrainLayer = this.terrainLayer == null ? new TerrainLayer() : this.terrainLayer.copy();
        copy.wallLayer = this.wallLayer == null ? new WallLayer() : this.wallLayer.copy();
        copy.fogSettings = this.fogSettings == null ? new FogSettings() : this.fogSettings.copy();
        copy.backgroundUrl = copy.backgroundLayer.getImageUrl();

        GridConfig gridCopy = new GridConfig();
        if (this.grid != null) {
            gridCopy.setCellSize(this.grid.getCellSize());
            gridCopy.setCols(this.grid.getCols());
            gridCopy.setRows(this.grid.getRows());
            gridCopy.setOffsetX(this.grid.getOffsetX());
            gridCopy.setOffsetY(this.grid.getOffsetY());
        } else {
            gridCopy.setCellSize(64);
            gridCopy.setCols(40);
            gridCopy.setRows(30);
        }
        copy.grid = gridCopy;

        for (MapLayer layer : layers) {
            copy.layers.add(layer.copy());
        }
        for (MapPlacement placement : placements) {
            copy.placements.add(placement.copy());
        }
        if (copy.layers.isEmpty()) {
            copy.ensureDefaultLayers();
        }
        return copy;
    }

    public void ensureDefaultLayers() {
        if (!layers.isEmpty()) {
            return;
        }
        addLayer(new MapLayer("floor", "Floor", LayerKind.FLOOR));
        addLayer(new MapLayer("structure", "Structure", LayerKind.STRUCTURE));
        addLayer(new MapLayer("objects", "Objects", LayerKind.OBJECTS));
        addLayer(new MapLayer("tokens", "Tokens", LayerKind.TOKENS));
        addLayer(new MapLayer("notes", "Notes", LayerKind.NOTES));
    }

    public void addLayer(MapLayer layer) {
        if (layer != null) {
            layers.add(layer);
        }
    }

    public Optional<MapLayer> findLayer(String layerId) {
        if (layerId == null) return Optional.empty();
        return layers.stream().filter(l -> layerId.equals(l.getId())).findFirst();
    }

    public MapLayer defaultLayerFor(PlacementKind kind) {
        if (kind == null) return findLayer("objects").orElseGet(() -> layers.isEmpty() ? null : layers.get(0));
        return switch (kind) {
            case WALL, DOOR -> findLayer("structure").orElseGet(() -> layers.isEmpty() ? null : layers.get(0));
            case TOKEN, SPAWN -> findLayer("tokens").orElseGet(() -> layers.isEmpty() ? null : layers.get(0));
            case DECOR, OBJECT -> findLayer("objects").orElseGet(() -> layers.isEmpty() ? null : layers.get(0));
        };
    }

    public void addPlacement(MapPlacement placement) {
        if (placement != null) {
            if (placement.getLayerId() == null || placement.getLayerId().isBlank()) {
                MapLayer layer = defaultLayerFor(placement.getKind());
                if (layer != null) {
                    placement.setLayerId(layer.getId());
                }
            }
            placements.add(placement);
        }
    }

    public boolean removePlacementById(String placementId) {
        return placements.removeIf(p -> placementId != null && placementId.equals(p.getId()));
    }

    public Optional<MapPlacement> findPlacement(String placementId) {
        if (placementId == null) return Optional.empty();
        return placements.stream().filter(p -> placementId.equals(p.getId())).findFirst();
    }

    public List<MapPlacement> getPlacements() {
        return Collections.unmodifiableList(placements);
    }

    public List<MapPlacement> mutablePlacements() {
        return placements;
    }

    public List<MapLayer> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    public List<MapLayer> mutableLayers() {
        return layers;
    }

    public boolean hasPlacementAt(String layerId, int col, int row) {
        for (MapPlacement placement : placements) {
            if ((layerId == null || layerId.equals(placement.getLayerId())) && placement.containsCell(col, row)) {
                return true;
            }
        }
        return false;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getBackgroundUrl() {
        if (backgroundLayer != null && backgroundLayer.getImageUrl() != null && !backgroundLayer.getImageUrl().isBlank()) {
            return backgroundLayer.getImageUrl();
        }
        return backgroundUrl;
    }

    public BackgroundLayer getBackgroundLayer() { return backgroundLayer; }
    public ReferenceOverlay getReferenceOverlay() { return referenceOverlay; }
    public TerrainLayer getTerrainLayer() { return terrainLayer; }
    public WallLayer getWallLayer() { return wallLayer; }
    public FogSettings getFogSettings() { return fogSettings; }
    public GridConfig getGrid() { return grid; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setBackgroundUrl(String backgroundUrl) {
        this.backgroundUrl = backgroundUrl;
        if (this.backgroundLayer == null) {
            this.backgroundLayer = new BackgroundLayer();
        }
        this.backgroundLayer.setImageUrl(backgroundUrl);
    }
    public void setBackgroundLayer(BackgroundLayer backgroundLayer) {
        this.backgroundLayer = backgroundLayer == null ? new BackgroundLayer() : backgroundLayer;
        this.backgroundUrl = this.backgroundLayer.getImageUrl();
    }
    public void setReferenceOverlay(ReferenceOverlay referenceOverlay) {
        this.referenceOverlay = referenceOverlay == null ? new ReferenceOverlay() : referenceOverlay;
    }
    public void setTerrainLayer(TerrainLayer terrainLayer) {
        this.terrainLayer = terrainLayer == null ? new TerrainLayer() : terrainLayer;
    }
    public void setWallLayer(WallLayer wallLayer) {
        this.wallLayer = wallLayer == null ? new WallLayer() : wallLayer;
    }
    public void setFogSettings(FogSettings fogSettings) {
        this.fogSettings = fogSettings == null ? new FogSettings() : fogSettings;
    }
    public void setGrid(GridConfig grid) { this.grid = grid == null ? new GridConfig(64, 40, 30) : grid; }
}
