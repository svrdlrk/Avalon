package com.avalon.dnd.shared;

public class MapObjectCreateRequest {

    private String type;
    private int col;
    private int row;
    private int width;
    private int height;
    /** Размер объекта в клетках (1..4). */
    private int gridSize = 1;
    /** URL текстуры объекта (относительный от корня сервера или null). */
    private String imageUrl;
    private boolean blocksMovement;
    private boolean blocksSight;

    public MapObjectCreateRequest() {}

    public MapObjectCreateRequest(String type, int col, int row, int width, int height) {
        this(type, col, row, width, height, 1, null);
    }

    public MapObjectCreateRequest(String type, int col, int row,
                                  int width, int height,int gridSize, String imageUrl) {
        this.type = type;
        this.col = col;
        this.row = row;
        this.width = width;
        this.height = height;
        this.gridSize = gridSize;
        this.imageUrl = imageUrl;
        this.blocksMovement = false;
        this.blocksSight = false;
    }

    public String getType() { return type; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getGridSize() { return gridSize; }
    public String getImageUrl() { return imageUrl; }
    public boolean isBlocksMovement() { return blocksMovement; }
    public boolean isBlocksSight() { return blocksSight; }

    public void setType(String type) { this.type = type; }
    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public void setGridSize(int gridSize) { this.gridSize = gridSize; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setBlocksMovement(boolean blocksMovement) { this.blocksMovement = blocksMovement; }
    public void setBlocksSight(boolean blocksSight) { this.blocksSight = blocksSight; }
}