package com.avalon.dnd.shared;

public class MapObjectCreateRequest {

    private String type;
    private int col;
    private int row;
    private int width;
    private int height;

    public MapObjectCreateRequest() {}

    public MapObjectCreateRequest(String type, int col, int row, int width, int height) {
        this.type = type;
        this.col = col;
        this.row = row;
        this.width = width;
        this.height = height;
    }

    public String getType() { return type; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setType(String type) { this.type = type; }
    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
}