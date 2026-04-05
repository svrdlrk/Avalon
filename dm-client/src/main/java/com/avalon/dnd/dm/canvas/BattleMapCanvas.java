package com.avalon.dnd.dm.canvas;

import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.TokenDto;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;

public class BattleMapCanvas extends Canvas {

    private TokenDto draggingToken = null;
    private double dragOffsetX, dragOffsetY;

    public BattleMapCanvas() {
        ClientState.getInstance().addChangeListener(this::renderAndResize);
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        setOnMouseReleased(this::onMouseReleased);
        renderAndResize();
    }

    private GridConfig grid() {
        return ClientState.getInstance().getGrid();
    }

    private void renderAndResize() {
        GridConfig g = grid();
        double width = g.getOffsetX() + (double) g.getCols() * g.getCellSize();
        double height = g.getOffsetY() + (double) g.getRows() * g.getCellSize();
        setWidth(Math.max(width, 1));
        setHeight(Math.max(height, 1));
        render();
    }

    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        GridConfig grid = grid();

        gc.setFill(Color.web("#2b2b2b"));
        gc.fillRect(0, 0, getWidth(), getHeight());

        drawGrid(gc, grid);
        drawObjects(gc, grid);
        drawTokens(gc, grid);
    }

    private void drawGrid(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int cols = grid.getCols();
        int rows = grid.getRows();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        gc.setStroke(Color.web("#444444"));
        gc.setLineWidth(0.5);

        for (int c = 0; c <= cols; c++) {
            double x = ox + c * cell;
            gc.strokeLine(x, oy, x, oy + rows * cell);
        }

        for (int r = 0; r <= rows; r++) {
            double y = oy + r * cell;
            gc.strokeLine(ox, y, ox + cols * cell, y);
        }
    }

    private void drawTokens(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        String myId = ClientState.getInstance().getPlayerId();

        for (TokenDto token : ClientState.getInstance().getTokens().values()) {
            double x = ox + token.getCol() * cell;
            double y = oy + token.getRow() * cell;

            boolean mine = myId != null && myId.equals(token.getOwnerId());
            gc.setFill(mine ? Color.web("#c9a227") : Color.web("#4a90d9"));
            gc.fillOval(x + 4, y + 4, cell - 8, cell - 8);

            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font(10));
            gc.fillText(token.getName(), x + 6, y + cell / 2.0 + 4);
        }
    }

    private void drawObjects(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        for (var obj : ClientState.getInstance().getObjects().values()) {
            double x = ox + obj.getCol() * cell;
            double y = oy + obj.getRow() * cell;
            double w = obj.getWidth() * cell;
            double h = obj.getHeight() * cell;

            gc.setFill(Color.web("#8B4513"));
            gc.fillRect(x, y, w, h);

            gc.setStroke(Color.web("#5a2d0c"));
            gc.strokeRect(x, y, w, h);
        }
    }

    private void onMousePressed(MouseEvent e) {
        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        int col = (int) ((e.getX() - ox) / cell);
        int row = (int) ((e.getY() - oy) / cell);

        draggingToken = ClientState.getInstance().getTokens().values().stream()
                .filter(t -> t.getCol() == col && t.getRow() == row)
                .findFirst()
                .orElse(null);

        if (draggingToken == null
                && col >= 0 && col < grid.getCols()
                && row >= 0 && row < grid.getRows()) {
            ClientState.getInstance().setPendingPlaceCell(col, row);
        }

        if (draggingToken != null) {
            dragOffsetX = e.getX() - (ox + col * cell);
            dragOffsetY = e.getY() - (oy + row * cell);
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (draggingToken != null) {
            GridConfig grid = grid();
            render();
            GraphicsContext gc = getGraphicsContext2D();
            int cell = grid.getCellSize();
            double x = e.getX() - dragOffsetX;
            double y = e.getY() - dragOffsetY;

            gc.setFill(Color.web("#4a90d9").deriveColor(0, 1, 1, 0.7));
            gc.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        }
    }

    private void onMouseReleased(MouseEvent e) {
        if (draggingToken == null) return;

        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        int newCol = (int) ((e.getX() - ox) / cell);
        int newRow = (int) ((e.getY() - oy) / cell);

        newCol = Math.max(0, Math.min(newCol, grid.getCols() - 1));
        newRow = Math.max(0, Math.min(newRow, grid.getRows() - 1));

        com.avalon.dnd.shared.TokenMoveEvent event =
                new com.avalon.dnd.shared.TokenMoveEvent(
                        draggingToken.getId(), newCol, newRow
                );

        com.avalon.dnd.dm.net.ServerConnection.getInstance()
                .send("/token.move", event);

        draggingToken = null;
        render();
    }
}
