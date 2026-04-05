package com.avalon.dnd.shared;

public class TokenCreateRequest {

    private String name;
    private int col;
    private int row;
    private String ownerId; // может быть null (NPC)

    public TokenCreateRequest() {}

    public TokenCreateRequest(String name, int col, int row, String ownerId) {
        this.name = name;
        this.col = col;
        this.row = row;
        this.ownerId = ownerId;
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

    public void setName(String name) {
        this.name = name;
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
}