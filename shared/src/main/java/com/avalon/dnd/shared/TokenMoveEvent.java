package com.avalon.dnd.shared;

public class TokenMoveEvent {

    private String tokenId;
    private int toCol;
    private int toRow;

    public TokenMoveEvent() {}

    public TokenMoveEvent(String tokenId, int toCol, int toRow) {
        this.tokenId = tokenId;
        this.toCol = toCol;
        this.toRow = toRow;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public int getToCol() {
        return toCol;
    }

    public void setToCol(int toCol) {
        this.toCol = toCol;
    }

    public int getToRow() {
        return toRow;
    }

    public void setToRow(int toRow) {
        this.toRow = toRow;
    }
}