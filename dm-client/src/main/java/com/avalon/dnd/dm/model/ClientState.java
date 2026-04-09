package com.avalon.dnd.dm.model;

import com.avalon.dnd.shared.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientState {

    private static final ClientState INSTANCE = new ClientState();
    public static ClientState getInstance() { return INSTANCE; }

    private String sessionId;
    private String playerId;
    private GridConfig grid = new GridConfig(64, 20, 20);
    private String backgroundUrl;

    private final Map<String, TokenDto>     tokens  = new ConcurrentHashMap<>();
    private final Map<String, MapObjectDto> objects = new ConcurrentHashMap<>();
    private final Map<String, PlayerDto>    players = new ConcurrentHashMap<>();

    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    private int pendingPlaceCol;
    private int pendingPlaceRow;

    private ClientState() {}

    public void applyState(SessionStateDto state, String sessionId, String playerId) {
        this.sessionId     = sessionId;
        this.playerId      = playerId;
        this.grid          = state.getGrid();
        this.backgroundUrl = state.getBackgroundUrl();
        tokens.clear();  state.getTokens().forEach(t -> tokens.put(t.getId(), t));
        objects.clear(); state.getObjects().forEach(o -> objects.put(o.getId(), o));
        players.clear(); state.getPlayers().forEach(p -> players.put(p.getId(), p));
        notifyMapChanged();
    }

    public void applyMapLayoutUpdate(MapLayoutUpdateDto dto) {
        this.grid          = dto.getGrid();
        this.backgroundUrl = dto.getBackgroundUrl();
        tokens.clear();  dto.getTokens().forEach(t -> tokens.put(t.getId(), t));
        objects.clear();
        if (dto.getObjects() != null) dto.getObjects().forEach(o -> objects.put(o.getId(), o));
        pendingPlaceCol = Math.min(pendingPlaceCol, Math.max(0, grid.getCols() - 1));
        pendingPlaceRow = Math.min(pendingPlaceRow, Math.max(0, grid.getRows() - 1));
        notifyMapChanged();
    }

    public void moveToken(TokenDto token)   { tokens.put(token.getId(), token); notifyMapChanged(); }
    public void addToken(TokenDto token)    { tokens.put(token.getId(), token); notifyMapChanged(); }
    public void removeToken(String id)      { tokens.remove(id);  notifyMapChanged(); }
    public void addObject(MapObjectDto obj) { objects.put(obj.getId(), obj); notifyMapChanged(); }
    public void removeObject(String id)     { objects.remove(id); notifyMapChanged(); }

    /** FIX: добавляем игрока при PLAYER_JOINED. */
    public void addPlayer(PlayerDto player) {
        players.put(player.getId(), player);
        notifyMapChanged();
    }

    /** FIX: убираем игрока при PLAYER_LEFT. */
    public void removePlayer(String playerId) {
        players.remove(playerId);
        notifyMapChanged();
    }

    public void setPendingPlaceCell(int col, int row) {
        this.pendingPlaceCol = col;
        this.pendingPlaceRow = row;
        notifyMapChanged();
    }

    public int getPendingPlaceCol() { return pendingPlaceCol; }
    public int getPendingPlaceRow() { return pendingPlaceRow; }

    public void addChangeListener(Runnable r)    { changeListeners.add(r); }
    public void removeChangeListener(Runnable r) { changeListeners.remove(r); }

    public void notifyMapChanged() {
        for (Runnable r : changeListeners)
            javafx.application.Platform.runLater(r);
    }

    public String getSessionId()                 { return sessionId; }
    public String getPlayerId()                  { return playerId; }
    public GridConfig getGrid()                  { return grid; }
    public Map<String, TokenDto>     getTokens() { return tokens; }
    public Map<String, MapObjectDto> getObjects(){ return objects; }
    public Map<String, PlayerDto>    getPlayers(){ return players; }
    public String getBackgroundUrl()             { return backgroundUrl; }

    public void setBackgroundUrl(String url) {
        this.backgroundUrl = url;
        notifyMapChanged();
    }
}