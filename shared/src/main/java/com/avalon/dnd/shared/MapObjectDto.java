package com.avalon.dnd.shared;

public class MapObjectDto {

    private String id;
    private String type;
    private int col;
    private int row;
    private int width;
    private int height;
    /** Размер объекта в клетках (1..4). */
    private int gridSize = 1;
    /** URL текстуры объекта (относительный от корня сервера или null). */
    private String imageUrl;


    public MapObjectDto() {}

    public MapObjectDto(String id, String type, int col, int row, int width, int height) {
        this(id, type, col, row, width, height, 1, null);
    }

    public MapObjectDto(String id, String type, int col, int row,
                        int width, int height,int gridSize, String imageUrl) {
        this.id = id;
        this.type = type;
        this.col = col;
        this.row = row;
        this.width = width;
        this.height = height;
        this.gridSize = Math.max(1, Math.min(4, gridSize));
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
    public void setGridSize(int gridSize) { this.gridSize = Math.max(1, Math.min(4, gridSize)); }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}