package com.avalon.dnd.mapeditor.ui;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapProject;

import java.util.Locale;

/**
 * Small status-formatting helpers extracted from MapEditorPane.
 */
public final class MapEditorPaneStatusSupport {

    private MapEditorPaneStatusSupport() {
    }

    public static String workspaceSummary(MapProject project) {
        if (project == null) {
            return "Untitled map • 0 layers • 0 placements";
        }
        int layers = project.getLayers().size();
        int placements = project.getPlacements().size();
        String projectName = project.getName();
        if (projectName == null || projectName.isBlank()) {
            projectName = "Untitled map";
        }
        return String.format(Locale.ROOT, "%s • %d layers • %d placements", projectName, layers, placements);
    }

    public static String toolStatus(EditorState state) {
        String toolName = state.getActiveTool() == null ? "Select" : state.getActiveTool().getDisplayName();
        return "Tool: " + toolName;
    }

    public static String viewStatus(EditorState state) {
        return String.format(Locale.ROOT,
                "Zoom: %.2fx • Pan: %.0f, %.0f", state.getZoom(), state.getViewOffsetX(), state.getViewOffsetY());
    }

    public static String gridStatus(EditorState state) {
        return "Snap: " + (state.isSnapToGrid() ? "on" : "off")
                + " • Fog: " + (state.isFogPreviewEnabled() ? "on" : "off");
    }

    public static int findLayerIndex(MapProject project, String selectedLayerId) {
        if (project == null || selectedLayerId == null) {
            return -1;
        }
        var layers = project.getLayers();
        for (int i = 0; i < layers.size(); i++) {
            MapLayer layer = layers.get(i);
            if (layer != null && selectedLayerId.equals(layer.getId())) {
                return i;
            }
        }
        return -1;
    }
}
