package com.avalon.dnd.mapeditor.model;

public class AssetDefinition {

    private String id;
    private String name;
    private String category;
    private String imageUrl;
    private int width = 1;
    private int height = 1;
    private boolean blocksMovement;
    private boolean blocksSight;
    private PlacementKind kind = PlacementKind.OBJECT;

    public AssetDefinition() {}

    public AssetDefinition(String id, String name, String category, String imageUrl,
                           int width, int height,
                           boolean blocksMovement, boolean blocksSight,
                           PlacementKind kind) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.imageUrl = imageUrl;
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.blocksMovement = blocksMovement;
        this.blocksSight = blocksSight;
        this.kind = kind == null ? PlacementKind.OBJECT : kind;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getImageUrl() { return imageUrl; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isBlocksMovement() { return blocksMovement; }
    public boolean isBlocksSight() { return blocksSight; }
    public PlacementKind getKind() { return kind; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCategory(String category) { this.category = category; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setWidth(int width) { this.width = Math.max(1, width); }
    public void setHeight(int height) { this.height = Math.max(1, height); }
    public void setBlocksMovement(boolean blocksMovement) { this.blocksMovement = blocksMovement; }
    public void setBlocksSight(boolean blocksSight) { this.blocksSight = blocksSight; }
    public void setKind(PlacementKind kind) { this.kind = kind == null ? PlacementKind.OBJECT : kind; }

    @Override
    public String toString() {
        return name + " [" + kind + "]";
    }
}
