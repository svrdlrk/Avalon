package com.avalon.dnd.mapeditor.model;

import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MicroLocationDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
    private final List<String> assetPackIds = new ArrayList<>();
    private final List<MicroLocationDto> microLocations = new ArrayList<>();

    private final List<MapLayer> layers = new ArrayList<>();
    private final List<MapPlacement> placements = new ArrayList<>();

    private long revision = 0;

    public MapProject() {}

    public long getRevision() { return revision; }

    public void touch() { revision++; }

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
        if (this.assetPackIds != null) {
            copy.assetPackIds.addAll(this.assetPackIds);
        }
        if (this.microLocations != null) {
            for (MicroLocationDto zone : this.microLocations) {
                if (zone != null) copy.microLocations.add(copyMicroLocation(zone));
            }
        }

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

        if (this.layers != null) {
            for (MapLayer layer : layers) {
                if (layer != null) {
                    copy.layers.add(layer.copy());
                }
            }
        }
        if (this.placements != null) {
            for (MapPlacement placement : placements) {
                if (placement != null) {
                    copy.placements.add(placement.copy());
                }
            }
        }
        if (copy.layers.isEmpty()) {
            copy.ensureDefaultLayers();
        }
        copy.revision = this.revision;
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
            touch();
        }
    }

    public Optional<MapLayer> findLayer(String layerId) {
        if (layerId == null || layerId.isBlank()) return Optional.empty();
        for (MapLayer layer : layers) {
            if (layer != null && layerId.equals(layer.getId())) {
                return Optional.of(layer);
            }
        }
        return Optional.empty();
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
            touch();
        }
    }

    public boolean removePlacementById(String placementId) {
        boolean removed = placements.removeIf(p -> placementId != null && placementId.equals(p.getId()));
        if (removed) touch();
        return removed;
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
        if (backgroundUrl != null && !backgroundUrl.isBlank()) {
            return backgroundUrl;
        }
        if (referenceOverlay != null && referenceOverlay.getImageUrl() != null && !referenceOverlay.getImageUrl().isBlank()) {
            return referenceOverlay.getImageUrl();
        }
        return null;
    }

    public BackgroundLayer getBackgroundLayer() { return backgroundLayer; }
    public ReferenceOverlay getReferenceOverlay() { return referenceOverlay; }
    public ReferenceOverlayLayer getReferenceOverlayLayer() {
        return referenceOverlay instanceof ReferenceOverlayLayer rol ? rol : new ReferenceOverlayLayer().copy(referenceOverlay);
    }
    public TerrainLayer getTerrainLayer() { return terrainLayer; }
    public WallLayer getWallLayer() { return wallLayer; }
    public FogSettings getFogSettings() { return fogSettings; }
    public GridConfig getGrid() { return grid; }
    public List<String> getAssetPackIds() { return Collections.unmodifiableList(assetPackIds); }
    public List<MicroLocationDto> getMicroLocations() { return Collections.unmodifiableList(microLocations); }
    public List<MicroLocationDto> mutableMicroLocations() { return microLocations; }

    public Optional<MicroLocationDto> findMicroLocation(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return microLocations.stream().filter(z -> id.equals(z.getId())).findFirst();
    }

    public void addMicroLocation(MicroLocationDto microLocation) {
        if (microLocation == null) return;
        MicroLocationDto copy = copyMicroLocation(microLocation);
        if (copy.getId() == null || copy.getId().isBlank()) {
            copy.setId(UUID.randomUUID().toString());
        }
        microLocations.add(copy);
        touch();
    }

    public boolean removeMicroLocationById(String id) {
        if (id == null || id.isBlank()) return false;
        boolean removed = microLocations.removeIf(zone -> id.equals(zone.getId()));
        boolean detachedPlacements = false;
        for (MapPlacement placement : placements) {
            if (placement != null && id.equals(placement.getMicroLocationId())) {
                placement.setMicroLocationId(null);
                detachedPlacements = true;
            }
        }
        if (removed || detachedPlacements) {
            touch();
        }
        return removed || detachedPlacements;
    }

    public boolean removeWallPathById(String wallPathId) {
        if (wallPathId == null || wallPathId.isBlank() || wallLayer == null) {
            return false;
        }
        boolean removed = wallLayer.removePathById(wallPathId);
        if (removed) {
            touch();
        }
        return removed;
    }

    public boolean updateMicroLocation(String id, MicroLocationDto updated) {
        if (id == null || id.isBlank() || updated == null) return false;
        for (int i = 0; i < microLocations.size(); i++) {
            MicroLocationDto current = microLocations.get(i);
            if (current != null && id.equals(current.getId())) {
                MicroLocationDto copy = copyMicroLocation(updated);
                if (copy.getId() == null || copy.getId().isBlank()) {
                    copy.setId(id);
                }
                microLocations.set(i, copy);
                touch();
                return true;
            }
        }
        return false;
    }

    public void setId(String id) {
        if (Objects.equals(this.id, id)) {
            return;
        }
        this.id = id;
        touch();
    }

    public void setName(String name) {
        if (Objects.equals(this.name, name)) {
            return;
        }
        this.name = name;
        touch();
    }

    public void setDescription(String description) {
        if (Objects.equals(this.description, description)) {
            return;
        }
        this.description = description;
        touch();
    }
    public void setBackgroundUrl(String backgroundUrl) {
        String normalized = backgroundUrl;
        if (Objects.equals(this.backgroundUrl, normalized)
                && this.backgroundLayer != null
                && Objects.equals(this.backgroundLayer.getImageUrl(), normalized)) {
            return;
        }
        this.backgroundUrl = normalized;
        if (this.backgroundLayer == null) {
            this.backgroundLayer = new BackgroundLayer();
        }
        this.backgroundLayer.setImageUrl(normalized);
        touch();
    }
    public void setBackgroundLayer(BackgroundLayer backgroundLayer) {
        BackgroundLayer normalized = backgroundLayer == null ? new BackgroundLayer() : backgroundLayer;
        if (sameBackgroundLayer(this.backgroundLayer, normalized)) {
            return;
        }
        this.backgroundLayer = normalized;
        this.backgroundUrl = this.backgroundLayer.getImageUrl();
        touch();
    }
    public void setReferenceOverlay(ReferenceOverlay referenceOverlay) {
        ReferenceOverlay normalized = referenceOverlay == null ? new ReferenceOverlay() : referenceOverlay;
        if (sameReferenceOverlay(this.referenceOverlay, normalized)) {
            return;
        }
        this.referenceOverlay = normalized;
        touch();
    }
    public void setReferenceOverlayLayer(ReferenceOverlayLayer referenceOverlayLayer) {
        ReferenceOverlay normalized = referenceOverlayLayer == null ? new ReferenceOverlayLayer() : referenceOverlayLayer;
        if (sameReferenceOverlay(this.referenceOverlay, normalized)) {
            return;
        }
        this.referenceOverlay = normalized;
        touch();
    }
    public void setTerrainLayer(TerrainLayer terrainLayer) {
        TerrainLayer normalized = terrainLayer == null ? new TerrainLayer() : terrainLayer;
        if (sameTerrainLayer(this.terrainLayer, normalized)) {
            return;
        }
        this.terrainLayer = normalized;
        touch();
    }
    public void setWallLayer(WallLayer wallLayer) {
        WallLayer normalized = wallLayer == null ? new WallLayer() : wallLayer;
        if (sameWallLayer(this.wallLayer, normalized)) {
            return;
        }
        this.wallLayer = normalized;
        touch();
    }
    public void setFogSettings(FogSettings fogSettings) {
        FogSettings normalized = fogSettings == null ? new FogSettings() : fogSettings;
        if (sameFogSettings(this.fogSettings, normalized)) {
            return;
        }
        this.fogSettings = normalized;
        touch();
    }
    public void setAssetPackIds(List<String> assetPackIds) {
        List<String> normalized = assetPackIds == null ? List.of() : List.copyOf(assetPackIds);
        if (Objects.equals(this.assetPackIds, normalized)) {
            return;
        }
        this.assetPackIds.clear();
        this.assetPackIds.addAll(normalized);
        touch();
    }

    public void setMicroLocations(List<MicroLocationDto> microLocations) {
        List<MicroLocationDto> normalized = new ArrayList<>();
        if (microLocations != null) {
            for (MicroLocationDto zone : microLocations) {
                if (zone != null) {
                    normalized.add(copyMicroLocation(zone));
                }
            }
        }
        if (sameMicroLocations(this.microLocations, normalized)) {
            return;
        }
        this.microLocations.clear();
        this.microLocations.addAll(normalized);
        touch();
    }
    public void setGrid(GridConfig grid) {
        GridConfig normalized = grid == null ? new GridConfig(64, 40, 30) : grid;
        if (sameGrid(this.grid, normalized)) {
            return;
        }
        this.grid = normalized;
        touch();
    }

    private static boolean sameBackgroundLayer(BackgroundLayer a, BackgroundLayer b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getImageUrl(), b.getImageUrl())
                && a.getMode() == b.getMode()
                && a.isVisible() == b.isVisible()
                && Double.compare(a.getOpacity(), b.getOpacity()) == 0
                && Double.compare(a.getScale(), b.getScale()) == 0
                && Double.compare(a.getOffsetX(), b.getOffsetX()) == 0
                && Double.compare(a.getOffsetY(), b.getOffsetY()) == 0;
    }

    private static boolean sameReferenceOverlay(ReferenceOverlay a, ReferenceOverlay b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getImageUrl(), b.getImageUrl())
                && a.isVisible() == b.isVisible()
                && a.isLocked() == b.isLocked()
                && Double.compare(a.getOpacity(), b.getOpacity()) == 0
                && Double.compare(a.getScale(), b.getScale()) == 0
                && Double.compare(a.getRotation(), b.getRotation()) == 0
                && Double.compare(a.getOffsetX(), b.getOffsetX()) == 0
                && Double.compare(a.getOffsetY(), b.getOffsetY()) == 0;
    }

    private static boolean sameTerrainLayer(TerrainLayer a, TerrainLayer b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.isVisible() != b.isVisible()
                || a.isLocked() != b.isLocked()
                || Double.compare(a.getOpacity(), b.getOpacity()) != 0
                || !Objects.equals(a.getPaintType(), b.getPaintType())) {
            return false;
        }
        List<TerrainCell> left = a.getCells();
        List<TerrainCell> right = b.getCells();
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!sameTerrainCell(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameTerrainCell(TerrainCell a, TerrainCell b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Objects.equals(a.getId(), b.getId())
                && a.getCol() == b.getCol()
                && a.getRow() == b.getRow()
                && a.getWidth() == b.getWidth()
                && a.getHeight() == b.getHeight()
                && Objects.equals(a.getTerrainType(), b.getTerrainType())
                && a.isBlocksMovement() == b.isBlocksMovement()
                && a.isBlocksSight() == b.isBlocksSight();
    }

    private static boolean sameWallLayer(WallLayer a, WallLayer b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.isVisible() != b.isVisible()
                || a.isLocked() != b.isLocked()
                || Double.compare(a.getOpacity(), b.getOpacity()) != 0
                || Double.compare(a.getDefaultThickness(), b.getDefaultThickness()) != 0
                || a.isDefaultBlocksMovement() != b.isDefaultBlocksMovement()
                || a.isDefaultBlocksSight() != b.isDefaultBlocksSight()) {
            return false;
        }
        List<WallPath> left = a.getPaths();
        List<WallPath> right = b.getPaths();
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!sameWallPath(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameWallPath(WallPath a, WallPath b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (!Objects.equals(a.getId(), b.getId())
                || !Objects.equals(a.getName(), b.getName())
                || a.isVisible() != b.isVisible()
                || a.isLocked() != b.isLocked()
                || Double.compare(a.getOpacity(), b.getOpacity()) != 0
                || Double.compare(a.getThickness(), b.getThickness()) != 0
                || a.isBlocksMovement() != b.isBlocksMovement()
                || a.isBlocksSight() != b.isBlocksSight()) {
            return false;
        }
        List<WallPoint> left = a.getPoints();
        List<WallPoint> right = b.getPoints();
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            if (!sameWallPoint(left.get(i), right.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameWallPoint(WallPoint a, WallPoint b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return Double.compare(a.getX(), b.getX()) == 0
                && Double.compare(a.getY(), b.getY()) == 0;
    }

    private static boolean sameFogSettings(FogSettings a, FogSettings b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.isEnabled() == b.isEnabled()
                && a.isRevealFromTokens() == b.isRevealFromTokens()
                && a.isRevealFromSelectedPlacement() == b.isRevealFromSelectedPlacement()
                && a.getRevealRadius() == b.getRevealRadius()
                && Double.compare(a.getOpacity(), b.getOpacity()) == 0
                && a.isRetainExploredCells() == b.isRetainExploredCells()
                && a.getSharedVisionDistance() == b.getSharedVisionDistance()
                && a.isNightMode() == b.isNightMode()
                && Objects.equals(a.getTimeOfDay(), b.getTimeOfDay());
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

    private static boolean sameMicroLocations(List<MicroLocationDto> a, List<MicroLocationDto> b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            MicroLocationDto left = a.get(i);
            MicroLocationDto right = b.get(i);
            if (!sameMicroLocation(left, right)) {
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

    private static MicroLocationDto copyMicroLocation(MicroLocationDto source) {
        if (source == null) return null;
        MicroLocationDto copy = new MicroLocationDto();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setCol(source.getCol());
        copy.setRow(source.getRow());
        copy.setWidth(source.getWidth());
        copy.setHeight(source.getHeight());
        copy.setLocked(source.isLocked());
        copy.setHint(source.getHint());
        copy.setInteriorMapPath(source.getInteriorMapPath());
        return copy;
    }
}
