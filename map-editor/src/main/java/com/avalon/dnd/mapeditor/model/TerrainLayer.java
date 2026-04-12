package com.avalon.dnd.mapeditor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TerrainLayer {

    private boolean visible = true;
    private boolean locked = false;
    private double opacity = 1.0;
    private String paintType = "grass";
    private final List<TerrainCell> cells = new ArrayList<>();

    public TerrainLayer copy() {
        TerrainLayer copy = new TerrainLayer();
        copy.visible = this.visible;
        copy.locked = this.locked;
        copy.opacity = this.opacity;
        copy.paintType = this.paintType;
        for (TerrainCell cell : this.cells) {
            if (cell != null) {
                copy.cells.add(cell.copy());
            }
        }
        return copy;
    }

    public boolean isVisible() { return visible; }
    public boolean isLocked() { return locked; }
    public double getOpacity() { return opacity; }
    public String getPaintType() { return paintType; }
    public List<TerrainCell> getCells() { return Collections.unmodifiableList(cells); }

    public void setVisible(boolean visible) { this.visible = visible; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setOpacity(double opacity) { this.opacity = Math.max(0.0, Math.min(1.0, opacity)); }
    public void setPaintType(String paintType) { this.paintType = paintType == null || paintType.isBlank() ? "grass" : paintType; }
    public void setCells(List<TerrainCell> cells) {
        this.cells.clear();
        if (cells != null) {
            for (TerrainCell cell : cells) {
                if (cell != null) {
                    this.cells.add(cell);
                }
            }
        }
    }

    public TerrainCell findCellAt(int col, int row) {
        for (TerrainCell cell : cells) {
            if (cell.getCol() == col && cell.getRow() == row) {
                return cell;
            }
        }
        return null;
    }

    public TerrainCell findCellById(String id) {
        if (id == null) {
            return null;
        }
        for (TerrainCell cell : cells) {
            if (id.equals(cell.getId())) {
                return cell;
            }
        }
        return null;
    }

    public void upsertCell(TerrainCell cell) {
        if (cell == null) {
            return;
        }
        TerrainCell existing = findCellById(cell.getId());
        if (existing == null) {
            existing = findCellAt(cell.getCol(), cell.getRow());
        }
        if (existing == null) {
            cells.add(cell);
        } else {
            int index = cells.indexOf(existing);
            cells.set(index, cell);
        }
    }

    public boolean removeCellAt(int col, int row) {
        return cells.removeIf(cell -> cell.getCol() == col && cell.getRow() == row);
    }

    public boolean removeCellById(String id) {
        return id != null && cells.removeIf(cell -> id.equals(cell.getId()));
    }
}
