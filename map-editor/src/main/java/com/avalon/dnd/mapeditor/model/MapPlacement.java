package com.avalon.dnd.mapeditor.model;

public class MapPlacement {

    private String id;
    private PlacementKind kind = PlacementKind.OBJECT;
    private String assetId;
    private String name;
    private String layerId;

    private int col;
    private int row;
    private int width = 1;
    private int height = 1;

    private int gridSize = 1;
    private double rotation;
    private boolean blocksMovement;
    private boolean blocksSight;
    private String imageUrl;

    private boolean selected;
    private boolean locked;
    private String ownerId;
    private String faction;
    private int hp = 10;
    private int maxHp = 10;
    private boolean hidden;
    private int initiativeOrder;
    private boolean npc;

    public MapPlacement() {}

    public static MapPlacement fromAsset(String id, AssetDefinition asset, int col, int row) {
        return fromAsset(id, asset, col, row, null);
    }

    public static MapPlacement fromAsset(String id, AssetDefinition asset, int col, int row, String layerId) {
        MapPlacement p = new MapPlacement();
        p.setId(id);
        p.setKind(asset.getKind());
        p.setAssetId(asset.getId());
        p.setName(asset.getName());
        p.setLayerId(layerId);
        p.setCol(col);
        p.setRow(row);
        if (asset.getKind() == PlacementKind.TOKEN || asset.getKind() == PlacementKind.SPAWN) {
            int size = Math.max(asset.getWidth(), asset.getHeight());
            p.setGridSize(size);
        } else {
            p.setWidth(asset.getWidth());
            p.setHeight(asset.getHeight());
        }
        p.setBlocksMovement(asset.isBlocksMovement());
        p.setBlocksSight(asset.isBlocksSight());
        p.setImageUrl(asset.getImageUrl());
        return p;
    }

    public MapPlacement copy() {
        MapPlacement copy = new MapPlacement();
        copy.id = this.id;
        copy.kind = this.kind;
        copy.assetId = this.assetId;
        copy.name = this.name;
        copy.layerId = this.layerId;
        copy.col = this.col;
        copy.row = this.row;
        copy.width = this.width;
        copy.height = this.height;
        copy.gridSize = this.gridSize;
        copy.rotation = this.rotation;
        copy.blocksMovement = this.blocksMovement;
        copy.blocksSight = this.blocksSight;
        copy.imageUrl = this.imageUrl;
        copy.selected = this.selected;
        copy.locked = this.locked;
        copy.ownerId = this.ownerId;
        copy.faction = this.faction;
        copy.hp = this.hp;
        copy.maxHp = this.maxHp;
        copy.hidden = this.hidden;
        copy.initiativeOrder = this.initiativeOrder;
        copy.npc = this.npc;
        return copy;
    }

    public int effectiveWidth() {
        return Math.max(1, isTokenKind() ? gridSize : width);
    }

    public int effectiveHeight() {
        return Math.max(1, isTokenKind() ? gridSize : height);
    }

    public boolean containsCell(int testCol, int testRow) {
        return testCol >= col && testCol < col + effectiveWidth()
                && testRow >= row && testRow < row + effectiveHeight();
    }

    public String getId() { return id; }
    public PlacementKind getKind() { return kind; }
    public String getAssetId() { return assetId; }
    public String getName() { return name; }
    public String getLayerId() { return layerId; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getGridSize() { return gridSize; }
    public double getRotation() { return rotation; }
    public boolean isBlocksMovement() { return blocksMovement; }
    public boolean isBlocksSight() { return blocksSight; }
    public String getImageUrl() { return imageUrl; }
    public boolean isSelected() { return selected; }
    public boolean isLocked() { return locked; }
    public String getOwnerId() { return ownerId; }
    public String getFaction() { return faction; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public boolean isHidden() { return hidden; }
    public int getInitiativeOrder() { return initiativeOrder; }
    public boolean isNpc() { return npc; }

    public void setId(String id) { this.id = id; }

    public void setKind(PlacementKind kind) {
        this.kind = kind == null ? PlacementKind.OBJECT : kind;
        if (isTokenKind()) {
            syncTokenSize(Math.max(1, gridSize));
        }
    }

    public void setAssetId(String assetId) { this.assetId = assetId; }
    public void setName(String name) { this.name = name; }
    public void setLayerId(String layerId) { this.layerId = layerId; }
    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }

    public void setWidth(int width) {
        this.width = Math.max(1, width);
        if (isTokenKind()) {
            syncTokenSize(this.width);
        }
    }

    public void setHeight(int height) {
        this.height = Math.max(1, height);
        if (isTokenKind()) {
            syncTokenSize(this.height);
        }
    }

    public void setGridSize(int gridSize) {
        syncTokenSize(gridSize);
    }

    public void setRotation(double rotation) { this.rotation = rotation; }
    public void setBlocksMovement(boolean blocksMovement) { this.blocksMovement = blocksMovement; }
    public void setBlocksSight(boolean blocksSight) { this.blocksSight = blocksSight; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setFaction(String faction) { this.faction = faction; }
    public void setHp(int hp) { this.hp = Math.max(0, hp); }
    public void setMaxHp(int maxHp) { this.maxHp = Math.max(1, maxHp); }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public void setInitiativeOrder(int initiativeOrder) { this.initiativeOrder = initiativeOrder; }
    public void setNpc(boolean npc) { this.npc = npc; }

    private void syncTokenSize(int size) {
        int normalized = Math.max(1, Math.min(10, size));
        this.gridSize = normalized;
        if (isTokenKind()) {
            this.width = normalized;
            this.height = normalized;
        }
    }

    private boolean isTokenKind() {
        return kind == PlacementKind.TOKEN || kind == PlacementKind.SPAWN;
    }
}
