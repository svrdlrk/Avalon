package com.avalon.dnd.shared;

import java.util.List;

/**
 * После смены сетки: новая конфигурация и полный список токенов (позиции могли быть поджаты).
 */
public class MapLayoutUpdateDto {

    private GridConfig grid;
    private List<TokenDto> tokens;
    private List<MapObjectDto> objects;

    public MapLayoutUpdateDto() {}

    public MapLayoutUpdateDto(GridConfig grid, List<TokenDto> tokens, List<MapObjectDto> objects) {
        this.grid = grid;
        this.tokens = tokens;
        this.objects = objects;
    }

    public GridConfig getGrid() {
        return grid;
    }

    public void setGrid(GridConfig grid) {
        this.grid = grid;
    }

    public List<TokenDto> getTokens() {
        return tokens;
    }

    public void setTokens(List<TokenDto> tokens) {
        this.tokens = tokens;
    }

    public List<MapObjectDto> getObjects() {
        return objects;
    }

    public void setObjects(List<MapObjectDto> objects) {
        this.objects = objects;
    }
}
