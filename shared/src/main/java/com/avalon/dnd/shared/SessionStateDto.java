package com.avalon.dnd.shared;

import java.util.List;

public class SessionStateDto {

    private String                myPlayerId;
    private GridConfig            grid;
    private List<TokenDto>        tokens;
    private List<PlayerDto>       players;
    private List<MapObjectDto>    objects;
    private String                backgroundUrl;
    /** Текущее состояние инициативы (null если не запущена). */
    private InitiativeStateDto    initiative;

    public SessionStateDto() {}

    public SessionStateDto(String myPlayerId,
                           GridConfig grid,
                           List<TokenDto> tokens,
                           List<PlayerDto> players,
                           List<MapObjectDto> objects,
                           String backgroundUrl) {
        this(myPlayerId, grid, tokens, players, objects, backgroundUrl, null);
    }

    public SessionStateDto(String myPlayerId,
                           GridConfig grid,
                           List<TokenDto> tokens,
                           List<PlayerDto> players,
                           List<MapObjectDto> objects,
                           String backgroundUrl,
                           InitiativeStateDto initiative) {
        this.myPlayerId   = myPlayerId;
        this.grid         = grid;
        this.tokens       = tokens;
        this.players      = players;
        this.objects      = objects;
        this.backgroundUrl = backgroundUrl;
        this.initiative   = initiative;
    }

    public String             getMyPlayerId()  { return myPlayerId; }
    public GridConfig         getGrid()        { return grid; }
    public List<TokenDto>     getTokens()      { return tokens; }
    public List<PlayerDto>    getPlayers()     { return players; }
    public List<MapObjectDto> getObjects()     { return objects; }
    public String             getBackgroundUrl() { return backgroundUrl; }
    public InitiativeStateDto getInitiative()  { return initiative; }

    public void setMyPlayerId(String v)            { this.myPlayerId   = v; }
    public void setGrid(GridConfig v)              { this.grid         = v; }
    public void setTokens(List<TokenDto> v)        { this.tokens       = v; }
    public void setPlayers(List<PlayerDto> v)      { this.players      = v; }
    public void setObjects(List<MapObjectDto> v)   { this.objects      = v; }
    public void setBackgroundUrl(String v)         { this.backgroundUrl = v; }
    public void setInitiative(InitiativeStateDto v){ this.initiative   = v; }
}