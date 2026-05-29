package com.avalon.dnd.shared;

import com.avalon.dnd.shared.PlacementSizingRules;

import java.util.Objects;

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
    private String microLocationId;
    private boolean blocksMovement;
    private boolean blocksSight;


    public MapObjectDto() {}

    public MapObjectDto(String id, String type, int col, int row, int width, int height) {
        this(id, type, col, row, width, height, 1, null, false, false);
    }

    public MapObjectDto(String id, String type, int col, int row,
                        int width, int height,int gridSize, String imageUrl) {
        this(id, type, col, row, width, height, gridSize, imageUrl, false, false);
    }

    public MapObjectDto(String id, String type, int col, int row,
                        int width, int height, int gridSize, String imageUrl,
                        boolean blocksMovement, boolean blocksSight) {
        this.id = id;
        this.type = type;
        this.col = col;
        this.row = row;
        this.width = width;
        this.height = height;
        this.gridSize = PlacementSizingRules.clampObjectGridSize(gridSize);
        this.imageUrl = imageUrl;
        this.blocksMovement = blocksMovement;
        this.blocksSight = blocksSight;
    }

    public String getId() { return id; }
    public String getType() { return type; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getGridSize() { return gridSize; }
    public String getImageUrl() { return imageUrl; }
    public String getMicroLocationId() { return microLocationId; }
    public boolean isBlocksMovement() { return blocksMovement; }
    public boolean isBlocksSight() { return blocksSight; }
    public void setGridSize(int gridSize) { this.gridSize = PlacementSizingRules.clampObjectGridSize(gridSize); }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setMicroLocationId(String microLocationId) { this.microLocationId = microLocationId; }
    public void setBlocksMovement(boolean blocksMovement) { this.blocksMovement = blocksMovement; }
    public void setBlocksSight(boolean blocksSight) { this.blocksSight = blocksSight; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MapObjectDto that)) return false;
        return col == that.col
                && row == that.row
                && width == that.width
                && height == that.height
                && gridSize == that.gridSize
                && blocksMovement == that.blocksMovement
                && blocksSight == that.blocksSight
                && Objects.equals(id, that.id)
                && Objects.equals(type, that.type)
                && Objects.equals(imageUrl, that.imageUrl)
                && Objects.equals(microLocationId, that.microLocationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, col, row, width, height, gridSize, imageUrl, microLocationId, blocksMovement, blocksSight);
    }
}
