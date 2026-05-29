package com.avalon.dnd.mapeditor.ui;

import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapProject;

public final class MapEditorTextSupport {

    private MapEditorTextSupport() {
    }

    public static String safeLayerName(MapProject project, String layerId) {
        if (layerId == null || project == null) {
            return "-";
        }
        for (MapLayer layer : project.getLayers()) {
            if (layer != null && layerId.equals(layer.getId())) {
                String name = layer.getName();
                return name == null || name.isBlank() ? layerId : name;
            }
        }
        return layerId;
    }

    public static String displayName(String primary, String fallback) {
        return primary == null ? fallback : primary;
    }
}
