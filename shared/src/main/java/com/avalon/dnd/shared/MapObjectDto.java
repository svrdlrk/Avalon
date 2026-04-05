package com.avalon.dnd.shared;

public class MapObjectDto {

    private String id;
    private String type; // WALL, TREE, ROCK
    private int col;
    private int row;
    private int width;
    private int height;

    public MapObjectDto() {}

    public MapObjectDto(String id, String type, int col, int row, int width, int height) {
        this.id = id;
        this.type = type;
        this.col = col;
        this.row = row;
        this.width = width;
        this.height = height;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}