package com.avalon.dnd.shared;

import java.util.Objects;

public class GridConfig {
    private int cellSize;   // размер клетки в пикселях
    private int cols;       // количество колонок
    private int rows;       // количество строк
    private int offsetX;    // сдвиг сетки по X (для выравнивания с картой)
    private int offsetY;    // сдвиг сетки по Y

    public GridConfig() {}

    public GridConfig(int cellSize, int cols, int rows) {
        this.cellSize = cellSize;
        this.cols = cols;
        this.rows = rows;
        this.offsetX = 0;
        this.offsetY = 0;
    }

    // getters + setters
    public int getCellSize() { return cellSize; }
    public void setCellSize(int cellSize) { this.cellSize = cellSize; }
    public int getCols() { return cols; }
    public void setCols(int cols) { this.cols = cols; }
    public int getRows() { return rows; }
    public void setRows(int rows) { this.rows = rows; }
    public int getOffsetX() { return offsetX; }
    public void setOffsetX(int offsetX) { this.offsetX = offsetX; }
    public int getOffsetY() { return offsetY; }
    public void setOffsetY(int offsetY) { this.offsetY = offsetY; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GridConfig that)) return false;
        return cellSize == that.cellSize
                && cols == that.cols
                && rows == that.rows
                && offsetX == that.offsetX
                && offsetY == that.offsetY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(cellSize, cols, rows, offsetX, offsetY);
    }
}
