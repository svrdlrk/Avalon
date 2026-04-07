package com.avalon.dnd.shared;

public class TokenCreateRequest {

    private String name;
    private int col;
    private int row;
    private String ownerId; // может быть null (NPC)
    private int hp;
    private int maxHp;

    public TokenCreateRequest() {}

    public TokenCreateRequest(String name, int col, int row, String ownerId, int hp, int maxHp) {
        this.name = name;
        this.col = col;
        this.row = row;
        this.ownerId = ownerId;
        this.hp = hp;
        this.maxHp = maxHp;
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

    public void setName(String name) {
        this.name = name;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public int getHp() { return hp; }

    public void setHp(int hp) { this.hp = hp; }

    public int getMaxHp() { return maxHp; }

    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
}