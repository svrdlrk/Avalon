package com.avalon.dnd.mapeditor.service;
import com.avalon.dnd.mapeditor.model.AssetCatalog;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.model.WallPath;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProjectValidator {

    private ProjectValidator() {
    }

    public static List<String> validate(MapProject project, AssetCatalog catalog) {
        List<String> issues = new ArrayList<>();
        if (project == null) {
            issues.add("Project is empty.");
            return issues;
        }

        if (project.getName() == null || project.getName().isBlank()) {
            issues.add("Project name is empty.");
        }

        Set<String> layerIds = new LinkedHashSet<>();
        for (MapLayer layer : project.getLayers()) {
            if (layer == null) {
                issues.add("Project contains a null layer.");
                continue;
            }
            if (layer.getId() == null || layer.getId().isBlank()) {
                issues.add("Layer has no id: " + safeName(layer.getName()));
            } else if (!layerIds.add(layer.getId())) {
                issues.add("Duplicate layer id: " + layer.getId());
            }
        }

        for (MapPlacement placement : project.getPlacements()) {
            if (placement == null) {
                issues.add("Project contains a null placement.");
                continue;
            }
            if (placement.getLayerId() == null || placement.getLayerId().isBlank()) {
                issues.add("Placement " + safeName(placement.getName()) + " has no layer.");
            } else if (project.findLayer(placement.getLayerId()).isEmpty()) {
                issues.add("Placement " + safeName(placement.getName()) + " references missing layer " + placement.getLayerId());
            }
            if (placement.getAssetId() != null && !placement.getAssetId().isBlank() && catalog != null && catalog.findById(placement.getAssetId()).isEmpty()) {
                issues.add("Placement " + safeName(placement.getName()) + " references missing asset " + placement.getAssetId());
            }
            if (placement.effectiveWidth() <= 0 || placement.effectiveHeight() <= 0) {
                issues.add("Placement " + safeName(placement.getName()) + " has invalid size.");
            }
        }

        if (project.getWallLayer() != null) {
            int index = 1;
            for (WallPath path : project.getWallLayer().getPaths()) {
                if (path == null) {
                    issues.add("Wall layer contains a null path.");
                    index++;
                    continue;
                }
                if (path.getPoints().size() < 2) {
                    issues.add("Wall path " + safeName(path.getName()) + " has less than 2 points.");
                }
                index++;
            }
        }

        return issues;
    }

    private static String safeName(String value) {
        return value == null || value.isBlank() ? "<unnamed>" : value;
    }
}
