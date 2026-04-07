package com.avalon.dnd.shared;

public class TokenCreateRequest {

    private String name;
    private int col;
    private int row;
    private String ownerId;
    private int hp;
    private int maxHp;
    /** Размер токена в клетках (1..4). */
    private int gridSize = 1;
    /** Относительный URL изображения или null. */
    private String imageUrl;

    public TokenCreateRequest() {}

    public TokenCreateRequest(String name, int col, int row, String ownerId,
                              int hp, int maxHp, int gridSize, String imageUrl) {
        this.name = name;
        this.col = col;
        this.row = row;
        this.ownerId = ownerId;
        this.hp = hp;
        this.maxHp = maxHp;
        this.gridSize = gridSize;
        this.imageUrl = imageUrl;
    }

    public String getName() { return name; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public String getOwnerId() { return ownerId; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getGridSize() { return gridSize; }
    public String getImageUrl() { return imageUrl; }

    public void setName(String name) { this.name = name; }
    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setHp(int hp) { this.hp = hp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public void setGridSize(int gridSize) { this.gridSize = gridSize; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}