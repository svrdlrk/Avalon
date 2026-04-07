package com.avalon.dnd.server.model;

public class MapObject {

    private String id;
    private String type;
    private int col;
    private int row;
    private int width;
    private int height;
    private String sessionId;
    /** Размер в клетках (1..4). */
    private int gridSize = 1;
    /** URL текстуры объекта. */
    private String imageUrl;

    public MapObject(String id, String type, int col, int row,
                     int width, int height, String sessionId) {
        this(id, type, col, row, width, height, sessionId,1, null);
    }

    public MapObject(String id, String type, int col, int row,
                     int width, int height, String sessionId,int gridSize, String imageUrl) {
        this.id = id;
        this.type = type;
        this.col = col;
        this.row = row;
        this.width = width;
        this.height = height;
        this.sessionId = sessionId;
        this.gridSize = gridSize;
        this.imageUrl = imageUrl;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getGridSize() { return gridSize; }
    public String getImageUrl() { return imageUrl; }

    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public void setGridSize(int gridSize) { this.gridSize = Math.max(1, Math.min(4, gridSize)); }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}