package com.avalon.dnd.shared;

public class TokenDto {

    private String id;
    private String name;
    private int col;
    private int row;
    private String ownerId;
    private int hp;
    private int maxHp;

    public TokenDto() {}

    public TokenDto(String id, String name, int col, int row, String ownerId) {
        this(id, name, col, row, ownerId, 10, 10);
    }

    public TokenDto(String id, String name, int col, int row, String ownerId, int hp, int maxHp) {
        this.id = id;
        this.name = name;
        this.col = col;
        this.row = row;
        this.ownerId = ownerId;
        this.hp = hp;
        this.maxHp = maxHp;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public String getOwnerId() { return ownerId; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }

    public void setHp(int hp) { this.hp = hp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
}
