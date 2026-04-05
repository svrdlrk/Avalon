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

    private final Map<String, TokenDto> tokens = new ConcurrentHashMap<>();
    private final Map<String, MapObjectDto> objects = new ConcurrentHashMap<>();
    private final Map<String, PlayerDto> players = new ConcurrentHashMap<>();

    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    /** Клетка для стены: выбирается кликом по пустому месту на карте. */
    private int pendingPlaceCol;
    private int pendingPlaceRow;

    private ClientState() {}

    public void applyState(SessionStateDto state, String sessionId, String playerId) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.grid = state.getGrid();

        tokens.clear();
        state.getTokens().forEach(t -> tokens.put(t.getId(), t));

        objects.clear();
        state.getObjects().forEach(o -> objects.put(o.getId(), o));

        players.clear();
        state.getPlayers().forEach(p -> players.put(p.getId(), p));
        notifyMapChanged();
    }

    public void applyMapLayoutUpdate(MapLayoutUpdateDto dto) {
        this.grid = dto.getGrid();
        tokens.clear();
        dto.getTokens().forEach(t -> tokens.put(t.getId(), t));
        objects.clear();
        if (dto.getObjects() != null) {
            dto.getObjects().forEach(o -> objects.put(o.getId(), o));
        }
        pendingPlaceCol = Math.min(pendingPlaceCol, Math.max(0, grid.getCols() - 1));
        pendingPlaceRow = Math.min(pendingPlaceRow, Math.max(0, grid.getRows() - 1));
        notifyMapChanged();
    }

    public void setPendingPlaceCell(int col, int row) {
        this.pendingPlaceCol = col;
        this.pendingPlaceRow = row;
        notifyMapChanged();
    }

    public int getPendingPlaceCol() {
        return pendingPlaceCol;
    }

    public int getPendingPlaceRow() {
        return pendingPlaceRow;
    }

    /** Подписка на любые изменения токенов/объектов/сетки (UI). */
    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    public void removeChangeListener(Runnable listener) {
        changeListeners.remove(listener);
    }

    public void notifyMapChanged() {
        for (Runnable r : changeListeners) {
            javafx.application.Platform.runLater(r);
        }
    }

    public void moveToken(TokenDto token) {
        tokens.put(token.getId(), token);
        notifyMapChanged();
    }

    public void addToken(TokenDto token) {
        tokens.put(token.getId(), token);
        notifyMapChanged();
    }

    public void removeToken(String tokenId) {
        tokens.remove(tokenId);
        notifyMapChanged();
    }

    public void addObject(MapObjectDto obj) {
        objects.put(obj.getId(), obj);
        notifyMapChanged();
    }

    public void removeObject(String objectId) {
        objects.remove(objectId);
        notifyMapChanged();
    }

    public String getSessionId() { return sessionId; }
    public String getPlayerId() { return playerId; }
    public GridConfig getGrid() { return grid; }
    public Map<String, TokenDto> getTokens() { return tokens; }
    public Map<String, MapObjectDto> getObjects() { return objects; }
    public Map<String, PlayerDto> getPlayers() { return players; }
}