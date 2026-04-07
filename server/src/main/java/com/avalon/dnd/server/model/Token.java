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
    /** Размер в клетках (1..4). */
    private int gridSize = 1;
    /** URL текстуры токена. */
    private String imageUrl;

    public Token() {}

    public Token(String id, String name, int col, int row, String ownerId, String sessionId) {
        this.id = id;
        this.name = name;
        this.col = col;
        this.row = row;
        this.ownerId = ownerId;
        this.sessionId = sessionId;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public String getOwnerId() { return ownerId; }
    public String getSessionId() { return sessionId; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getGridSize() { return gridSize; }
    public String getImageUrl() { return imageUrl; }

    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setHp(int hp) { this.hp = hp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public void setGridSize(int gridSize) { this.gridSize = Math.max(1, Math.min(4, gridSize)); }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}