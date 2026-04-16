package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.model.PlacementKind;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.MapObjectDto;
import com.avalon.dnd.shared.TokenDto;

import java.util.ArrayList;
import java.util.List;

public final class SharedProjectMapper {

    private SharedProjectMapper() {}

    public static MapLayoutUpdateDto toLayoutDto(MapProject project) {
        List<TokenDto> tokens = new ArrayList<>();
        List<MapObjectDto> objects = new ArrayList<>();

        for (MapPlacement placement : project.getPlacements()) {
            if (placement.getKind() == PlacementKind.TOKEN || placement.getKind() == PlacementKind.SPAWN) {
                tokens.add(new TokenDto(
                        placement.getId(),
                        placement.getName() == null ? placement.getAssetId() : placement.getName(),
                        placement.getCol(),
                        placement.getRow(),
                        null,
                        10,
                        10,
                        placement.getGridSize(),
                        placement.getImageUrl()
                ));
            } else {
                objects.add(new MapObjectDto(
                        placement.getId(),
                        placement.getName() == null ? placement.getAssetId() : placement.getName(),
                        placement.getCol(),
                        placement.getRow(),
                        placement.effectiveWidth(),
                        placement.effectiveHeight(),
                        placement.getGridSize(),
                        placement.getImageUrl(),
                        placement.isBlocksMovement(),
                        placement.isBlocksSight()
                ));
            }
        }

        GridConfig grid = project.getGrid();
        return new MapLayoutUpdateDto(
                grid,
                tokens,
                objects,
                project.getBackgroundUrl(),
                project.getReferenceOverlayLayer(),
                project.getTerrainLayer(),
                project.getWallLayer(),
                project.getFogSettings(),
                project.getAssetPackIds()
        );
    }

    public static MapProject fromLayoutDto(String id, String name, MapLayoutUpdateDto dto) {
        MapProject project = MapProject.createBlank(id, name);
        project.setGrid(dto.getGrid() == null ? new GridConfig(64, 40, 30) : dto.getGrid());
        project.setBackgroundUrl(dto.getBackgroundUrl());
        project.setReferenceOverlayLayer(dto.getReferenceOverlayLayer() instanceof com.avalon.dnd.mapeditor.model.ReferenceOverlayLayer rol ? rol : null);
        project.setTerrainLayer(dto.getTerrainLayer() instanceof com.avalon.dnd.mapeditor.model.TerrainLayer tl ? tl : null);
        project.setWallLayer(dto.getWallLayer() instanceof com.avalon.dnd.mapeditor.model.WallLayer wl ? wl : null);
        project.setFogSettings(dto.getFogSettings() instanceof com.avalon.dnd.mapeditor.model.FogSettings fs ? fs : null);
        project.setAssetPackIds(dto.getAssetPackIds());

        if (dto.getObjects() != null) {
            for (MapObjectDto objectDto : dto.getObjects()) {
                MapPlacement placement = new MapPlacement();
                placement.setId(objectDto.getId());
                placement.setKind(PlacementKind.OBJECT);
                placement.setAssetId(objectDto.getType());
                placement.setName(objectDto.getType());
                placement.setCol(objectDto.getCol());
                placement.setRow(objectDto.getRow());
                placement.setWidth(objectDto.getWidth());
                placement.setHeight(objectDto.getHeight());
                placement.setGridSize(objectDto.getGridSize());
                placement.setImageUrl(objectDto.getImageUrl());
                placement.setBlocksMovement(objectDto.isBlocksMovement());
                placement.setBlocksSight(objectDto.isBlocksSight());
                project.addPlacement(placement);
            }
        }

        if (dto.getTokens() != null) {
            for (TokenDto tokenDto : dto.getTokens()) {
                MapPlacement placement = new MapPlacement();
                placement.setId(tokenDto.getId());
                placement.setKind(PlacementKind.TOKEN);
                placement.setAssetId(tokenDto.getName());
                placement.setName(tokenDto.getName());
                placement.setCol(tokenDto.getCol());
                placement.setRow(tokenDto.getRow());
                placement.setGridSize(tokenDto.getGridSize());
                placement.setWidth(tokenDto.getGridSize());
                placement.setHeight(tokenDto.getGridSize());
                placement.setImageUrl(tokenDto.getImageUrl());
                project.addPlacement(placement);
            }
        }

        return project;
    }
}
