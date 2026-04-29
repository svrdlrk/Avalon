package com.avalon.dnd.shared;

public class MicroLocationDto {

    private String id;
    private String name;
    private int col;
    private int row;
    private int width = 1;
    private int height = 1;
    private boolean locked;
    private String hint;
    private String interiorMapPath;

    public MicroLocationDto() {}

    public String getId() { return id; }
    public String getName() { return name; }
    public int getCol() { return col; }
    public int getRow() { return row; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public boolean isLocked() { return locked; }
    public String getHint() { return hint; }
    public String getInteriorMapPath() { return interiorMapPath; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setCol(int col) { this.col = col; }
    public void setRow(int row) { this.row = row; }
    public void setWidth(int width) { this.width = Math.max(1, width); }
    public void setHeight(int height) { this.height = Math.max(1, height); }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setHint(String hint) { this.hint = hint; }
    public void setInteriorMapPath(String interiorMapPath) { this.interiorMapPath = interiorMapPath; }

    public boolean containsCell(int testCol, int testRow) {
        return testCol >= col && testCol < col + width && testRow >= row && testRow < row + height;
    }
}
