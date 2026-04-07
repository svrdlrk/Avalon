package com.avalon.dnd.shared;

public class TokenDto {

    private String id;
    private String name;
    private int col;
    private int row;
    private String ownerId;
    private int hp;
    private int maxHp;
    /** Размер в клетках (1..4). Токен занимает gridSize×gridSize клеток. */
    private int gridSize = 1;
    /** URL изображения токена (относительный от корня сервера или null). */
    private String imageUrl;

    public TokenDto() {}

    public TokenDto(String id, String name, int col, int row, String ownerId) {
        this(id, name, col, row, ownerId, 10, 10, 1, null);
    }

    public TokenDto(String id, String name, int col, int row, String ownerId, int hp, int maxHp) {
        this(id, name, col, row, ownerId, hp, maxHp, 1, null);
    }

    public TokenDto(String id, String name, int col, int row, String ownerId,
                    int hp, int maxHp, int gridSize, String imageUrl) {
        this.id = id;
        this.name = name;
        this.col = col;
        this.row = row;
        this.ownerId = ownerId;
        this.hp = hp;
        this.maxHp = maxHp;
        this.gridSize = Math.max(1, Math.min(4, gridSize));
        this.imageUrl = imageUrl;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public String getOwnerId() { return ownerId; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getGridSize() { return gridSize; }
    public String getImageUrl() { return imageUrl; }

    public void setHp(int hp) { this.hp = hp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public void setGridSize(int gridSize) { this.gridSize = Math.max(1, Math.min(4, gridSize)); }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}