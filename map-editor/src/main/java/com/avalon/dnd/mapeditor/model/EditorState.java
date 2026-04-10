package com.avalon.dnd.mapeditor.model;

import com.avalon.dnd.mapeditor.tool.Tool;
import com.avalon.dnd.shared.GridConfig;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class EditorState {

    public static final String PROP_PROJECT = "project";
    public static final String PROP_ACTIVE_TOOL = "activeTool";
    public static final String PROP_SELECTED_ASSET = "selectedAsset";
    public static final String PROP_SELECTED_PLACEMENT = "selectedPlacement";
    public static final String PROP_SELECTED_LAYER = "selectedLayer";
    public static final String PROP_VIEW = "view";
    public static final String PROP_UI = "ui";

    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final ProjectHistory history = new ProjectHistory();

    private MapProject project;
    private AssetCatalog assetCatalog;
    private Tool activeTool;

    private String selectedAssetId;
    private String selectedPlacementId;
    private String selectedLayerId;

    private double viewOffsetX = 0;
    private double viewOffsetY = 0;
    private double zoom = 1.0;
    private boolean snapToGrid = true;
    private boolean fogPreviewEnabled = true;

    public void addListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    public MapProject getProject() { return project; }

    public void setProject(MapProject project) {
        MapProject old = this.project;
        this.project = project;
        if (this.project != null) {
            this.project.ensureDefaultLayers();
            if (selectedLayerId == null || this.project.findLayer(selectedLayerId).isEmpty()) {
                selectedLayerId = this.project.getLayers().isEmpty() ? null : this.project.getLayers().get(0).getId();
            }
        }
        clearSelection();
        history.clear();
        pcs.firePropertyChange(PROP_PROJECT, old, project);
        pcs.firePropertyChange(PROP_SELECTED_LAYER, null, selectedLayerId);
    }

    public void recordHistory() {
        history.record(project);
    }

    public boolean undo() {
        if (!history.canUndo()) {
            return false;
        }
        this.project = history.undo(project);
        if (this.project != null) {
            this.project.ensureDefaultLayers();
            if (selectedLayerId == null || this.project.findLayer(selectedLayerId).isEmpty()) {
                selectedLayerId = this.project.getLayers().isEmpty() ? null : this.project.getLayers().get(0).getId();
            }
        }
        clearSelection();
        pcs.firePropertyChange(PROP_PROJECT, null, project);
        return true;
    }

    public boolean redo() {
        if (!history.canRedo()) {
            return false;
        }
        this.project = history.redo(project);
        if (this.project != null) {
            this.project.ensureDefaultLayers();
            if (selectedLayerId == null || this.project.findLayer(selectedLayerId).isEmpty()) {
                selectedLayerId = this.project.getLayers().isEmpty() ? null : this.project.getLayers().get(0).getId();
            }
        }
        clearSelection();
        pcs.firePropertyChange(PROP_PROJECT, null, project);
        return true;
    }

    public AssetCatalog getAssetCatalog() { return assetCatalog; }
    public void setAssetCatalog(AssetCatalog assetCatalog) { this.assetCatalog = assetCatalog; }

    public Tool getActiveTool() { return activeTool; }
    public void setActiveTool(Tool activeTool) {
        Tool old = this.activeTool;
        this.activeTool = activeTool;
        pcs.firePropertyChange(PROP_ACTIVE_TOOL, old, activeTool);
    }

    public String getSelectedAssetId() { return selectedAssetId; }
    public void setSelectedAssetId(String selectedAssetId) {
        String old = this.selectedAssetId;
        this.selectedAssetId = selectedAssetId;
        pcs.firePropertyChange(PROP_SELECTED_ASSET, old, selectedAssetId);
    }

    public String getSelectedPlacementId() { return selectedPlacementId; }
    public void setSelectedPlacementId(String selectedPlacementId) {
        String old = this.selectedPlacementId;
        this.selectedPlacementId = selectedPlacementId;
        pcs.firePropertyChange(PROP_SELECTED_PLACEMENT, old, selectedPlacementId);
    }

    public String getSelectedLayerId() { return selectedLayerId; }
    public void setSelectedLayerId(String selectedLayerId) {
        String old = this.selectedLayerId;
        this.selectedLayerId = selectedLayerId;
        pcs.firePropertyChange(PROP_SELECTED_LAYER, old, selectedLayerId);
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
    }

    public void selectPlacement(String placementId) {
        setSelectedPlacementId(placementId);
    }

    public void selectAsset(String assetId) {
        setSelectedAssetId(assetId);
    }

    public void selectLayer(String layerId) {
        setSelectedLayerId(layerId);
    }

    public AssetDefinition selectedAsset() {
        if (assetCatalog == null || selectedAssetId == null) return null;
        return assetCatalog.findById(selectedAssetId).orElse(null);
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
