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
    /** Радиус обзора днём. */
    private int dayVision;
    /** Радиус обзора ночью. */
    private int nightVision;

    public TokenDto() {}

    public TokenDto(String id, String name, int col, int row, String ownerId) {
        this(id, name, col, row, ownerId, 10, 10, 1, null, 0, 0);
    }

    public TokenDto(String id, String name, int col, int row, String ownerId, int hp, int maxHp) {
        this(id, name, col, row, ownerId, hp, maxHp, 1, null, 0, 0);
    }

    public TokenDto(String id, String name, int col, int row, String ownerId,
                    int hp, int maxHp, int gridSize, String imageUrl) {
        this(id, name, col, row, ownerId, hp, maxHp, gridSize, imageUrl, 0, 0);
    }

    public TokenDto(String id, String name, int col, int row, String ownerId,
                    int hp, int maxHp, int gridSize, String imageUrl,
                    int dayVision, int nightVision) {
        this.id = id;
        this.name = name;
        this.col = col;
        this.row = row;
        this.ownerId = ownerId;
        this.hp = hp;
        this.maxHp = maxHp;
        this.gridSize = Math.max(1, Math.min(4, gridSize));
        this.imageUrl = imageUrl;
        this.dayVision = Math.max(0, dayVision);
        this.nightVision = Math.max(0, nightVision);
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
    public int getDayVision() { return dayVision; }
    public int getNightVision() { return nightVision; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setHp(int hp) { this.hp = hp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public void setGridSize(int gridSize) { this.gridSize = Math.max(1, Math.min(4, gridSize)); }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setDayVision(int dayVision) { this.dayVision = Math.max(0, dayVision); }
    public void setNightVision(int nightVision) { this.nightVision = Math.max(0, nightVision); }
}
