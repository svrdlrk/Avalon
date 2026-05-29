package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.MapObject;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.MapObjectDto;
import com.avalon.dnd.shared.TokenDto;
import com.avalon.dnd.shared.TokenVisibilitySnapshotDto;

/**
 * Pure DTO mapping helpers extracted from the battle rules service.
 */
public final class MapBattleRulesMappers {

    private MapBattleRulesMappers() {
    }

    public static TokenDto toTokenDto(Token token) {
        TokenDto dto = new TokenDto();
        dto.setId(token.getId());
        dto.setOwnerId(token.getOwnerId());
        dto.setName(token.getName());
        dto.setCol(token.getCol());
        dto.setRow(token.getRow());
        dto.setHp(token.getHp());
        dto.setMaxHp(token.getMaxHp());
        dto.setGridSize(token.getGridSize());
        dto.setImageUrl(token.getImageUrl());
        dto.setFacingAngleDeg(token.getFacingAngleDeg());
        return dto;
    }

    public static TokenVisibilitySnapshotDto toTokenSnapshotDto(Token token) {
        TokenVisibilitySnapshotDto dto = new TokenVisibilitySnapshotDto();
        dto.setId(token.getId());
        dto.setName(token.getName());
        dto.setOwnerId(token.getOwnerId());
        dto.setCol(token.getCol());
        dto.setRow(token.getRow());
        dto.setGridSize(token.getGridSize());
        dto.setImageUrl(token.getImageUrl());
        return dto;
    }

    public static MapObjectDto toObjectDto(MapObject object) {
        MapObjectDto dto = new MapObjectDto(
                object.getId(), object.getType(),
                object.getCol(), object.getRow(),
                object.getWidth(), object.getHeight(),
                object.getGridSize(), object.getImageUrl(),
                object.isBlocksMovement(), object.isBlocksSight());
        dto.setMicroLocationId(object.getMicroLocationId());
        return dto;
    }
}
