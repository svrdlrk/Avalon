package com.avalon.dnd.shared;

import java.util.List;

public class SessionStateDto {

    private String myPlayerId;
    private GridConfig grid;
    private List<TokenDto> tokens;
    private List<PlayerDto> players;
    private List<MapObjectDto> objects;

    public SessionStateDto() {}

    public SessionStateDto(String myPlayerId,
                           GridConfig grid,
                           List<TokenDto> tokens,
                           List<PlayerDto> players,
                           List<MapObjectDto> objects) {
        this.myPlayerId = myPlayerId;
        this.grid = grid;
        this.tokens = tokens;
        this.players = players;
        this.objects = objects;
    }

    public String getMyPlayerId() { return myPlayerId; }

    public void setMyPlayerId(String myPlayerId) { this.myPlayerId = myPlayerId; }

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

    public List<PlayerDto> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerDto> players) {
        this.players = players;
    }

    public List<MapObjectDto> getObjects() { return objects; }

    public void setObjects(List<MapObjectDto> objects) { this.objects = objects; }
}