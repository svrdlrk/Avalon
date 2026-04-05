package com.avalon.dnd.server.model;

public class Token {

    private String id;
    private String name;
    private int col;
    private int row;
    private String ownerId; // null = NPC
    private String sessionId;
    private int hp = 10;
    private int maxHp = 10;

    public Token() {}

    public Token(String id, String name, int col, int row, String ownerId, String sessionId) {
        this.id = id;
        this.name = name;
        this.col = col;
        this.row = row;
        this.ownerId = ownerId;
        this.sessionId = sessionId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getCol() {
        return col;
    }

    public int getRow() {
        return row;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }
}