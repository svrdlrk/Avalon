package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.BackgroundLayer;
import com.avalon.dnd.mapeditor.model.FogSettings;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.model.ReferenceOverlayLayer;
import com.avalon.dnd.mapeditor.model.TerrainLayer;
import com.avalon.dnd.mapeditor.model.WallLayer;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MicroLocationDto;

import java.util.ArrayList;

public final class BattleProjectMapper {

    private BattleProjectMapper() {}

    public static BattleProjectDto toDto(MapProject project) {
        BattleProjectDto dto = new BattleProjectDto();
        if (project == null) {
            return dto;
        }

        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setGrid(copyGrid(project.getGrid()));
        dto.setBackgroundLayer(copyBackground(project.getBackgroundLayer()));
        dto.setReferenceOverlayLayer(copyReference(project.getReferenceOverlayLayer()));
        dto.setTerrainLayer(copyTerrain(project.getTerrainLayer()));
        dto.setWallLayer(copyWall(project.getWallLayer()));
        dto.setFogSettings(copyFog(project.getFogSettings()));
        dto.setMicroLocations(new ArrayList<>(project.getMicroLocations()));
        dto.setAssetPackIds(new ArrayList<>(project.getAssetPackIds()));

        ArrayList<MapLayer> layers = new ArrayList<>();
        for (MapLayer layer : project.getLayers()) {
            layers.add(layer == null ? null : layer.copy());
        }
        dto.setLayers(layers);

        ArrayList<MapPlacement> placements = new ArrayList<>();
        for (MapPlacement placement : project.getPlacements()) {
            placements.add(placement == null ? null : placement.copy());
        }
        dto.setPlacements(placements);
        return dto;
    }

    public static MapProject fromDto(BattleProjectDto dto) {
        if (dto == null) {
            return MapProject.createBlank(null, "Untitled Map");
        }

        MapProject project = MapProject.createBlank(dto.getId(), dto.getName());
        project.setDescription(dto.getDescription());
        project.setGrid(copyGrid(dto.getGrid()));
        project.setBackgroundLayer(copyBackground(dto.getBackgroundLayer()));
        project.setReferenceOverlayLayer(copyReference(dto.getReferenceOverlayLayer()));
        project.setTerrainLayer(copyTerrain(dto.getTerrainLayer()));
        project.setWallLayer(copyWall(dto.getWallLayer()));
        project.setFogSettings(copyFog(dto.getFogSettings()));
        project.setMicroLocations(dto.getMicroLocations());
        project.setAssetPackIds(dto.getAssetPackIds());

        project.mutableLayers().clear();
        if (dto.getLayers() != null) {
            for (MapLayer layer : dto.getLayers()) {
                if (layer != null) {
                    project.mutableLayers().add(layer.copy());
                }
            }
        }
        if (project.getLayers().isEmpty()) {
            project.ensureDefaultLayers();
        }

        project.mutablePlacements().clear();
        if (dto.getPlacements() != null) {
            for (MapPlacement placement : dto.getPlacements()) {
                if (placement != null) {
                    project.mutablePlacements().add(placement.copy());
                }
            }
        }

        return project;
    }

    private static GridConfig copyGrid(GridConfig source) {
        GridConfig grid = new GridConfig(64, 40, 30);
        if (source == null) {
            return grid;
        }
        grid.setCellSize(source.getCellSize());
        grid.setCols(source.getCols());
        grid.setRows(source.getRows());
        grid.setOffsetX(source.getOffsetX());
        grid.setOffsetY(source.getOffsetY());
        return grid;
    }

    private static BackgroundLayer copyBackground(BackgroundLayer source) {
        return source == null ? new BackgroundLayer() : source.copy();
    }

    private static ReferenceOverlayLayer copyReference(ReferenceOverlayLayer source) {
        return source == null ? new ReferenceOverlayLayer() : source.copy();
    }

    private static TerrainLayer copyTerrain(TerrainLayer source) {
        return source == null ? new TerrainLayer() : source.copy();
    }

    private static WallLayer copyWall(WallLayer source) {
        return source == null ? new WallLayer() : source.copy();
    }

    private static FogSettings copyFog(FogSettings source) {
        return source == null ? new FogSettings() : source.copy();
    }
}
