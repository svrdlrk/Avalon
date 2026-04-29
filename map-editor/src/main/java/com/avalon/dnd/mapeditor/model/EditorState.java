package com.avalon.dnd.mapeditor.model;

import com.avalon.dnd.mapeditor.tool.Tool;
import com.avalon.dnd.shared.GridConfig;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class EditorState {

    public static final String PROP_PROJECT = "project";
    public static final String PROP_ACTIVE_TOOL = "activeTool";
    public static final String PROP_SELECTED_ASSET = "selectedAsset";
    public static final String PROP_SELECTED_TOKEN_ASSET = "selectedTokenAsset";
    public static final String PROP_SELECTED_OBJECT_ASSET = "selectedObjectAsset";
    public static final String PROP_SELECTED_PLACEMENT = "selectedPlacement";
    public static final String PROP_SELECTED_MICRO_LOCATION = "selectedMicroLocation";
    public static final String PROP_SELECTED_LAYER = "selectedLayer";
    public static final String PROP_SELECTED_WALL = "selectedWall";
    public static final String PROP_SELECTED_WALL_VERTEX = "selectedWallVertex";
    public static final String PROP_VIEW = "view";
    public static final String PROP_UI = "ui";
    public static final String PROP_HISTORY = "history";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final ProjectHistory history = new ProjectHistory();

    private MapProject project;
    private AssetCatalog assetCatalog;
    private Tool activeTool;

    private String selectedAssetId;
    private String selectedTokenAssetId;
    private String selectedObjectAssetId;
    private String selectedPlacementId;
    private String selectedLayerId;
    private String selectedMicroLocationId;
    private String selectedWallPathId;
    private int selectedWallVertexIndex = -1;

    private double viewOffsetX = 0;
    private double viewOffsetY = 0;
    private double zoom = 1.0;
    private boolean snapToGrid = true;
    private boolean fogPreviewEnabled = true;
    private Double wallSnapX;
    private Double wallSnapY;

    public void addListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public MapProject getProject() { return project; }

    public void setProject(MapProject project) {
        MapProject old = this.project;
        clearWallSnapIndicator();
        if (old != null && selectedPlacementId != null) {
            old.findPlacement(selectedPlacementId).ifPresent(p -> p.setSelected(false));
        }
        this.project = project;
        if (this.project != null) {
            this.project.ensureDefaultLayers();
            if (selectedLayerId == null || this.project.findLayer(selectedLayerId).isEmpty()) {
                selectedLayerId = this.project.getLayers().isEmpty() ? null : this.project.getLayers().get(0).getId();
            }
        }
        clearSelection();
        setSelectedMicroLocationId(null);
        history.clear();
        pcs.firePropertyChange(PROP_PROJECT, old, project);
        pcs.firePropertyChange(PROP_SELECTED_LAYER, null, selectedLayerId);
    }

    public void recordHistory() {
        history.record(project);
        pcs.firePropertyChange(PROP_HISTORY, null, project);
    }

    public boolean undo() {
        if (!history.canUndo()) {
            return false;
        }
        this.project = history.undo(project);
        clearWallSnapIndicator();
        if (this.project != null) {
            this.project.ensureDefaultLayers();
            if (selectedLayerId == null || this.project.findLayer(selectedLayerId).isEmpty()) {
                selectedLayerId = this.project.getLayers().isEmpty() ? null : this.project.getLayers().get(0).getId();
            }
        }
        clearSelection();
        setSelectedMicroLocationId(null);
        pcs.firePropertyChange(PROP_PROJECT, null, project);
        pcs.firePropertyChange(PROP_HISTORY, null, project);
        return true;
    }

    public boolean redo() {
        if (!history.canRedo()) {
            return false;
        }
        this.project = history.redo(project);
        clearWallSnapIndicator();
        if (this.project != null) {
            this.project.ensureDefaultLayers();
            if (selectedLayerId == null || this.project.findLayer(selectedLayerId).isEmpty()) {
                selectedLayerId = this.project.getLayers().isEmpty() ? null : this.project.getLayers().get(0).getId();
            }
        }
        clearSelection();
        setSelectedMicroLocationId(null);
        pcs.firePropertyChange(PROP_PROJECT, null, project);
        pcs.firePropertyChange(PROP_HISTORY, null, project);
        return true;
    }

    public AssetCatalog getAssetCatalog() { return assetCatalog; }
    public void setAssetCatalog(AssetCatalog assetCatalog) { this.assetCatalog = assetCatalog; }

    public Tool getActiveTool() { return activeTool; }
    public void setActiveTool(Tool activeTool) {
        Tool old = this.activeTool;
        this.activeTool = activeTool;
        clearWallSnapIndicator();
        pcs.firePropertyChange(PROP_ACTIVE_TOOL, old, activeTool);
    }

    public String getSelectedAssetId() { return selectedAssetId; }
    public void setSelectedAssetId(String selectedAssetId) {
        String old = this.selectedAssetId;
        this.selectedAssetId = selectedAssetId;
        pcs.firePropertyChange(PROP_SELECTED_ASSET, old, selectedAssetId);
    }

    public String getSelectedTokenAssetId() { return selectedTokenAssetId; }
    public String getSelectedObjectAssetId() { return selectedObjectAssetId; }

    public void setSelectedTokenAssetId(String selectedTokenAssetId) {
        String old = this.selectedTokenAssetId;
        this.selectedTokenAssetId = selectedTokenAssetId;
        if (selectedTokenAssetId != null) {
            setSelectedAssetId(selectedTokenAssetId);
        }
        pcs.firePropertyChange(PROP_SELECTED_TOKEN_ASSET, old, selectedTokenAssetId);
    }

    public void setSelectedObjectAssetId(String selectedObjectAssetId) {
        String old = this.selectedObjectAssetId;
        this.selectedObjectAssetId = selectedObjectAssetId;
        if (selectedObjectAssetId != null) {
            setSelectedAssetId(selectedObjectAssetId);
        }
        pcs.firePropertyChange(PROP_SELECTED_OBJECT_ASSET, old, selectedObjectAssetId);
    }

    public String getSelectedPlacementId() { return selectedPlacementId; }
    public void setSelectedPlacementId(String selectedPlacementId) {
        String old = this.selectedPlacementId;
        if (project != null && old != null) {
            project.findPlacement(old).ifPresent(p -> p.setSelected(false));
        }
        this.selectedPlacementId = selectedPlacementId;
        if (project != null && selectedPlacementId != null) {
            project.findPlacement(selectedPlacementId).ifPresent(p -> p.setSelected(true));
        }
        pcs.firePropertyChange(PROP_SELECTED_PLACEMENT, old, selectedPlacementId);
    }

    public String getSelectedLayerId() { return selectedLayerId; }
    public void setSelectedLayerId(String selectedLayerId) {
        String old = this.selectedLayerId;
        this.selectedLayerId = selectedLayerId;
        pcs.firePropertyChange(PROP_SELECTED_LAYER, old, selectedLayerId);
    }

    public String getSelectedMicroLocationId() { return selectedMicroLocationId; }
    public void setSelectedMicroLocationId(String selectedMicroLocationId) {
        String old = this.selectedMicroLocationId;
        this.selectedMicroLocationId = selectedMicroLocationId;
        pcs.firePropertyChange(PROP_SELECTED_MICRO_LOCATION, old, selectedMicroLocationId);
    }

    public double getViewOffsetX() { return viewOffsetX; }
    public double getViewOffsetY() { return viewOffsetY; }
    public double getZoom() { return zoom; }
    public boolean isSnapToGrid() { return snapToGrid; }
    public boolean isFogPreviewEnabled() { return fogPreviewEnabled; }

    public void setSnapToGrid(boolean snapToGrid) {
        this.snapToGrid = snapToGrid;
        pcs.firePropertyChange(PROP_UI, null, null);
    }

    public void setFogPreviewEnabled(boolean fogPreviewEnabled) {
        this.fogPreviewEnabled = fogPreviewEnabled;
        pcs.firePropertyChange(PROP_UI, null, null);
    }

    public void pan(double dx, double dy) {
        viewOffsetX += dx;
        viewOffsetY += dy;
        pcs.firePropertyChange(PROP_VIEW, null, null);
    }

    public void setViewOffset(double x, double y) {
        this.viewOffsetX = x;
        this.viewOffsetY = y;
        pcs.firePropertyChange(PROP_VIEW, null, null);
    }

    public void zoomBy(double factor) {
        setZoom(this.zoom * factor);
    }

    public void setZoom(double zoom) {
        this.zoom = Math.max(0.25, Math.min(3.0, zoom));
        pcs.firePropertyChange(PROP_VIEW, null, null);
    }

    public GridConfig grid() {
        return project == null ? new GridConfig(64, 40, 30) : project.getGrid();
    }

    public void clearSelection() {
        setSelectedPlacementId(null);
        setSelectedMicroLocationId(null);
        setSelectedWallPathId(null);
        setSelectedWallVertexIndex(-1);
    }

    public void selectPlacement(String placementId) {
        setSelectedPlacementId(placementId);
        setSelectedWallPathId(null);
    }

    public void selectAsset(String assetId) {
        setSelectedAssetId(assetId);
        this.selectedTokenAssetId = null;
        this.selectedObjectAssetId = null;
    }

    public void selectTokenAsset(String assetId) {
        setSelectedTokenAssetId(assetId);
    }

    public void selectObjectAsset(String assetId) {
        setSelectedObjectAssetId(assetId);
    }

    public void selectLayer(String layerId) {
        setSelectedLayerId(layerId);
    }

    public String getSelectedWallPathId() {
        return selectedWallPathId;
    }

    public int getSelectedWallVertexIndex() {
        return selectedWallVertexIndex;
    }

    public void setSelectedWallPathId(String selectedWallPathId) {
        String old = this.selectedWallPathId;
        this.selectedWallPathId = selectedWallPathId;
        if (selectedWallPathId == null) {
            this.selectedWallVertexIndex = -1;
        }
        pcs.firePropertyChange(PROP_SELECTED_WALL, old, selectedWallPathId);
    }

    public void setSelectedWallVertexIndex(int selectedWallVertexIndex) {
        int old = this.selectedWallVertexIndex;
        this.selectedWallVertexIndex = selectedWallVertexIndex;
        pcs.firePropertyChange(PROP_SELECTED_WALL_VERTEX, old, selectedWallVertexIndex);
    }

    public void selectWallPath(String wallPathId) {
        setSelectedPlacementId(null);
        setSelectedWallPathId(wallPathId);
        setSelectedWallVertexIndex(-1);
    }

    public void selectWallVertex(String wallPathId, int vertexIndex) {
        setSelectedPlacementId(null);
        setSelectedWallPathId(wallPathId);
        setSelectedWallVertexIndex(vertexIndex);
    }

    public WallPath selectedWallPath() {
        if (project == null || selectedWallPathId == null) return null;
        return project.getWallLayer() == null ? null : project.getWallLayer().findPathById(selectedWallPathId);
    }


    public void setWallSnapIndicator(Double x, Double y) {
        Double oldX = this.wallSnapX;
        Double oldY = this.wallSnapY;
        this.wallSnapX = x;
        this.wallSnapY = y;
        pcs.firePropertyChange(PROP_UI, null, null);
    }

    public void clearWallSnapIndicator() {
        if (this.wallSnapX != null || this.wallSnapY != null) {
            this.wallSnapX = null;
            this.wallSnapY = null;
            pcs.firePropertyChange(PROP_UI, null, null);
        }
    }

    public boolean hasWallSnapIndicator() {
        return wallSnapX != null && wallSnapY != null;
    }

    public Double getWallSnapX() {
        return wallSnapX;
    }

    public Double getWallSnapY() {
        return wallSnapY;
    }
    public AssetDefinition selectedAsset() {
        if (assetCatalog == null || selectedAssetId == null) return null;
        return assetCatalog.findById(selectedAssetId).orElse(null);
    }

    public AssetDefinition selectedTokenAsset() {
        if (assetCatalog == null || selectedTokenAssetId == null) return null;
        return assetCatalog.findById(selectedTokenAssetId).orElse(null);
    }

    public AssetDefinition selectedObjectAsset() {
        if (assetCatalog == null || selectedObjectAssetId == null) return null;
        return assetCatalog.findById(selectedObjectAssetId).orElse(null);
    }

    public MapPlacement selectedPlacement() {
        if (project == null || selectedPlacementId == null) return null;
        return project.findPlacement(selectedPlacementId).orElse(null);
    }

    public MapLayer selectedLayer() {
        if (project == null || selectedLayerId == null) return null;
        return project.findLayer(selectedLayerId).orElse(null);
    }

    public void ensureProject() {
        if (project == null) {
            setProject(MapProject.createBlank(null, "Untitled Map"));
        }
    }

    public boolean isLayerLocked(String layerId) {
        return project != null && project.findLayer(layerId).map(MapLayer::isLocked).orElse(false);
    }

    public boolean isLayerVisible(String layerId) {
        return project != null && project.findLayer(layerId).map(MapLayer::isVisible).orElse(true);
    }
}
