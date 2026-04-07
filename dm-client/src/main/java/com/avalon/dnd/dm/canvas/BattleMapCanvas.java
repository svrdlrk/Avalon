package com.avalon.dnd.dm.canvas;

import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.TokenDto;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

public class BattleMapCanvas extends Canvas {

    private TokenDto draggingToken = null;
    private double dragOffsetX, dragOffsetY;
    private Image backgroundImage;

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

        // Рисуем фон: либо загруженную картинку, либо заливку
        // FIX: убрана двойная заливка поверх backgroundImage
        if (backgroundImage != null && backgroundImage.getWidth() > 0
                && !backgroundImage.isError()) {
            gc.drawImage(backgroundImage, 0, 0, getWidth(), getHeight());
        } else {
            gc.setFill(Color.web("#2b2b2b"));
            gc.fillRect(0, 0, getWidth(), getHeight());
        }

        drawGrid(gc, grid);
        drawObjects(gc, grid);
        drawTokens(gc, grid);
        highlightPendingCell(gc, grid);
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
            boolean isNpc = token.getOwnerId() == null;

            gc.setFill(mine ? Color.web("#c9a227") : isNpc ? Color.web("#c9a227").darker() : Color.web("#4a90d9"));
            gc.fillOval(x + 4, y + 4, cell - 8, cell - 8);

            // Имя
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font(Math.max(9, cell / 6.0)));
            String label = token.getName().length() > 6
                    ? token.getName().substring(0, 5) + "…"
                    : token.getName();
            gc.fillText(label, x + 6, y + cell / 2.0 + 4);

            // HP-бар
            if (token.getMaxHp() > 0) {
                double barW = cell - 8;
                double barH = 4;
                double barX = x + 4;
                double barY = y + cell - 10;
                double ratio = Math.max(0, (double) token.getHp() / token.getMaxHp());

                gc.setFill(Color.web("#333333"));
                gc.fillRect(barX, barY, barW, barH);

                Color hpColor = ratio > 0.5 ? Color.web("#4caf50")
                        : ratio > 0.25 ? Color.web("#ff9800")
                        : Color.web("#f44336");
                gc.setFill(hpColor);
                gc.fillRect(barX, barY, barW * ratio, barH);
            }
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
            gc.setLineWidth(1);
            gc.strokeRect(x, y, w, h);
        }
    }

    private void highlightPendingCell(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        int col = ClientState.getInstance().getPendingPlaceCol();
        int row = ClientState.getInstance().getPendingPlaceRow();

        double x = ox + col * cell;
        double y = oy + row * cell;

        gc.setStroke(Color.web("#ffcc00"));
        gc.setLineWidth(2);
        gc.strokeRect(x + 1, y + 1, cell - 2, cell - 2);
    }

    private void onMousePressed(MouseEvent e) {
        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        int col = (int) ((e.getX() - ox) / cell);
        int row = (int) ((e.getY() - oy) / cell);

        if (col < 0 || col >= grid.getCols() || row < 0 || row >= grid.getRows()) {
            return;
        }

        draggingToken = ClientState.getInstance().getTokens().values().stream()
                .filter(t -> t.getCol() == col && t.getRow() == row)
                .findFirst()
                .orElse(null);

        if (draggingToken == null) {
            ClientState.getInstance().setPendingPlaceCell(col, row);
        } else {
            dragOffsetX = e.getX() - (ox + col * cell);
            dragOffsetY = e.getY() - (oy + row * cell);
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (draggingToken != null) {
            render();
            GraphicsContext gc = getGraphicsContext2D();
            int cell = grid().getCellSize();
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
                new com.avalon.dnd.shared.TokenMoveEvent(draggingToken.getId(), newCol, newRow);
        com.avalon.dnd.dm.net.ServerConnection.getInstance().send("/token.move", event);

        draggingToken = null;
        render();
    }

    public void setBackground(String url) {
        if (url != null && !url.isEmpty()) {
            backgroundImage = new Image(url, true); // async load
            // Перерисовываем когда картинка загрузится
            backgroundImage.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() >= 1.0 && !backgroundImage.isError()) {
                    javafx.application.Platform.runLater(this::render);
                }
            });
        }
    }
}