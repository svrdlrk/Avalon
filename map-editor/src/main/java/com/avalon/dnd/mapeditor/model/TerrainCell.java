package com.avalon.dnd.mapeditor.model;

import java.util.UUID;

public class TerrainCell {

    private String id = UUID.randomUUID().toString();
    private int col;
    private int row;
    private int width = 1;
    private int height = 1;
    private String terrainType = "grass";
    private boolean blocksMovement;
    private boolean blocksSight;

    public TerrainCell() {}

    public TerrainCell copy() {
        TerrainCell copy = new TerrainCell();
        copy.id = this.id;
        copy.col = this.col;
        copy.row = this.row;
        copy.width = this.width;
        copy.height = this.height;
        copy.terrainType = this.terrainType;
        copy.blocksMovement = this.blocksMovement;
        copy.blocksSight = this.blocksSight;
        return copy;
    }

    public String getId() { return id; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public String getTerrainType() { return terrainType; }
    public boolean isBlocksMovement() { return blocksMovement; }
    public boolean isBlocksSight() { return blocksSight; }

    public void setId(String id) { this.id = id; }
    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }
    public void setWidth(int width) { this.width = Math.max(1, width); }
    public void setHeight(int height) { this.height = Math.max(1, height); }
    public void setTerrainType(String terrainType) { this.terrainType = terrainType == null || terrainType.isBlank() ? "grass" : terrainType; }
    public void setBlocksMovement(boolean blocksMovement) { this.blocksMovement = blocksMovement; }
    public void setBlocksSight(boolean blocksSight) { this.blocksSight = blocksSight; }
}
