package com.avalon.dnd.shared;

import java.util.List;

/**
 * После смены сетки: новая конфигурация и полный список токенов (позиции могли быть поджаты).
 */
public class MapLayoutUpdateDto {

    private GridConfig grid;
    private List<TokenDto> tokens;
    private List<MapObjectDto> objects;
    private String backgroundUrl;

    public MapLayoutUpdateDto() {}

    public MapLayoutUpdateDto(GridConfig grid, List<TokenDto> tokens, List<MapObjectDto> objects, String backgroundUrl) {
        this.grid = grid;
        this.tokens = tokens;
        this.objects = objects;
        this.backgroundUrl = backgroundUrl;
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

    public String getBackgroundUrl() { return backgroundUrl; }

    public void setBackgroundUrl(String backgroundUrl) { this.backgroundUrl = backgroundUrl; }
}
