package com.avalon.dnd.shared;

import com.avalon.dnd.shared.PlacementSizingRules;

/**
 * Fog-memory snapshot for a token.
 * Separate from TokenDto so authoritative state and render-only memory do not share the same type.
 */
public class TokenVisibilitySnapshotDto {

    private String id;
    private String name;
    private int col;
    private int row;
    private String ownerId;
    private int hp;
    private int maxHp;
    private int gridSize = 1;
    private String imageUrl;
    private int dayVision;
    private int nightVision;
    private int facingAngleDeg;

    public TokenVisibilitySnapshotDto() {}

    public TokenVisibilitySnapshotDto(String id, String name, int col, int row, String ownerId,
                                      int hp, int maxHp, int gridSize, String imageUrl,
                                      int dayVision, int nightVision, int facingAngleDeg) {
        this.id = id;
        this.name = name;
        this.col = col;
        this.row = row;
        this.ownerId = ownerId;
        this.hp = hp;
        this.maxHp = maxHp;
        this.gridSize = PlacementSizingRules.clampTokenGridSize(gridSize);
        this.imageUrl = imageUrl;
        this.dayVision = Math.max(0, dayVision);
        this.nightVision = Math.max(0, nightVision);
        this.facingAngleDeg = facingAngleDeg;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public String getOwnerId() { return ownerId; }
    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getGridSize() { return gridSize; }
    public String getImageUrl() { return imageUrl; }
    public int getDayVision() { return dayVision; }
    public int getNightVision() { return nightVision; }
    public int getFacingAngleDeg() { return facingAngleDeg; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public void setHp(int hp) { this.hp = hp; }
    public void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    public void setGridSize(int gridSize) { this.gridSize = PlacementSizingRules.clampTokenGridSize(gridSize); }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setDayVision(int dayVision) { this.dayVision = Math.max(0, dayVision); }
    public void setNightVision(int nightVision) { this.nightVision = Math.max(0, nightVision); }
    public void setFacingAngleDeg(int facingAngleDeg) { this.facingAngleDeg = facingAngleDeg; }
}
