package com.avalon.dnd.server.model;

import com.avalon.dnd.shared.GridConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class GameSession {

    private String id;
    private GridConfig grid = new GridConfig(64,20,20);
    private final AtomicLong version = new AtomicLong(0);


    private Map<String, Player> players = new ConcurrentHashMap<>();
    private Map<String, Token> tokens = new ConcurrentHashMap<>();
    private Map<String, MapObject> objects = new ConcurrentHashMap<>();

    public GameSession(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public Map<String, Player> getPlayers() {
        return players;
    }

    public Map<String, Token> getTokens() {
        return tokens;
    }

    public Map<String, MapObject> getObjects() {
        return objects;
    }

    public GridConfig getGrid() {
        return grid;
    }

    public void setGrid(GridConfig grid) {
        this.grid = grid;
    }

    public long getVersion() { return version.get(); }
    public long incrementVersion() { return version.incrementAndGet(); }
}