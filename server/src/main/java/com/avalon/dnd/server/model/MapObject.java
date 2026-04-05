package com.avalon.dnd.server.model;

public class MapObject {

    private String id;
    private String type;
    private int col;
    private int row;
    private int width;
    private int height;
    private String sessionId;

    public MapObject(String id, String type, int col, int row,
                     int width, int height, String sessionId) {
        this.id = id;
        this.type = type;
        this.col = col;
        this.row = row;
        this.width = width;
        this.height = height;
        this.sessionId = sessionId;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
}