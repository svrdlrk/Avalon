package com.avalon.dnd.shared;

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
}