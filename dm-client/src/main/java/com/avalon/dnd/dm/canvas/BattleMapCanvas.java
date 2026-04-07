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
import javafx.scene.text.TextAlignment;

import java.util.HashMap;
import java.util.Map;

public class BattleMapCanvas extends Canvas {

    private TokenDto draggingToken = null;
    private double dragOffsetX, dragOffsetY;
    private Image backgroundImage;

    // Кеш изображений токенов и объектов
    private final Map<String, Image> imageCache = new HashMap<>();

    // Tooltip
    private TokenDto hoveredToken = null;

    public BattleMapCanvas() {
        ClientState.getInstance().addChangeListener(this::renderAndResize);
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        setOnMouseReleased(this::onMouseReleased);
        setOnMouseMoved(this::onMouseMoved);
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

        // Фон
        if (backgroundImage != null && backgroundImage.getWidth() > 0 && !backgroundImage.isError()) {
            gc.drawImage(backgroundImage, 0, 0, getWidth(), getHeight());
        } else {
            gc.setFill(Color.web("#2b2b2b"));
            gc.fillRect(0, 0, getWidth(), getHeight());
        }

        drawGrid(gc, grid);
        drawObjects(gc, grid);
        drawTokens(gc, grid);
        highlightPendingCell(gc, grid);
        if (hoveredToken != null) drawTooltip(gc, grid, hoveredToken);
    }

    // ---------------------------------------------------------------- grid

    private void drawGrid(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        gc.setStroke(Color.web("#444444"));
        gc.setLineWidth(0.5);

        for (int c = 0; c <= grid.getCols(); c++) {
            double x = ox + c * cell;
            gc.strokeLine(x, oy, x, oy + grid.getRows() * cell);
        }
        for (int r = 0; r <= grid.getRows(); r++) {
            double y = oy + r * cell;
            gc.strokeLine(ox, y, ox + grid.getCols() * cell, y);
        }
    }

    // ---------------------------------------------------------------- tokens

    private void drawTokens(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        String myId = ClientState.getInstance().getPlayerId();

        for (TokenDto token : ClientState.getInstance().getTokens().values()) {
            int gs = Math.max(1, token.getGridSize());
            double x = ox + token.getCol() * cell;
            double y = oy + token.getRow() * cell;
            double w = gs * cell;
            double h = gs * cell;

            boolean mine = myId != null && myId.equals(token.getOwnerId());
            boolean isNpc = token.getOwnerId() == null;

            // Тень / контур
            Color borderColor = mine ? Color.web("#c9a227")
                    : isNpc ? Color.web("#e74c3c")
                    : Color.web("#4a90d9");

            gc.setStroke(borderColor);
            gc.setLineWidth(gs > 1 ? 2.5 : 1.5);
            gc.strokeOval(x + 2, y + 2, w - 4, h - 4);

            // Изображение или заливка
            Image img = getTokenImage(token);
            if (img != null && !img.isError()) {
                gc.save();
                // Clip к кругу
                gc.beginPath();
                gc.arc(x + w / 2, y + h / 2, w / 2 - 3, h / 2 - 3, 0, 360);
                gc.clip();
                gc.drawImage(img, x + 3, y + 3, w - 6, h - 6);
                gc.restore();
            } else {
                // Цветная заливка-заглушка
                Color fillColor = mine ? Color.web("#c9a227")
                        : isNpc ? Color.web("#c0392b")
                        : Color.web("#4a90d9");
                gc.setFill(fillColor.deriveColor(0, 1, 1, 0.85));
                gc.fillOval(x + 3, y + 3, w - 6, h - 6);
            }

            // Имя
            gc.setFill(Color.WHITE);
            double fontSize = Math.max(9, Math.min(14, cell * gs / 6.0));
            gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, fontSize));
            gc.setTextAlign(TextAlignment.CENTER);
            String label = token.getName().length() > 8
                    ? token.getName().substring(0, 7) + "…"
                    : token.getName();
            gc.fillText(label, x + w / 2, y + h / 2 + fontSize / 3);

            // HP-бар
            if (token.getMaxHp() > 0) {
                double barW = w - 6;
                double barH = Math.max(4, cell / 12.0);
                double barX = x + 3;
                double barY = y + h - barH - 4;
                double ratio = Math.max(0, (double) token.getHp() / token.getMaxHp());

                gc.setFill(Color.web("#111111", 0.7));
                gc.fillRoundRect(barX, barY, barW, barH, 3, 3);

                Color hpColor = ratio > 0.5 ? Color.web("#2ecc71")
                        : ratio > 0.25 ? Color.web("#f39c12")
                        : Color.web("#e74c3c");
                gc.setFill(hpColor);
                gc.fillRoundRect(barX, barY, barW * ratio, barH, 3, 3);
            }

            // Размерная метка для больших токенов
            if (gs > 1) {
                gc.setFill(Color.web("#ecf0f1", 0.8));
                gc.setFont(Font.font("Arial", 9));
                gc.fillText(gs + "×" + gs, x + w - 14, y + 12);
            }
        }
    }

    // ---------------------------------------------------------------- objects

    private void drawObjects(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        for (var obj : ClientState.getInstance().getObjects().values()) {
            double x = ox + obj.getCol() * cell;
            double y = oy + obj.getRow() * cell;
            double w = obj.getWidth() * cell;
            double h = obj.getHeight() * cell;

            Image img = getObjectImage(obj.getImageUrl());
            if (img != null && !img.isError()) {
                gc.drawImage(img, x, y, w, h);
                // Лёгкое затемнение поверх
                gc.setFill(Color.web("#000000", 0.15));
                gc.fillRect(x, y, w, h);
            } else {
                gc.setFill(Color.web("#8B4513"));
                gc.fillRect(x, y, w, h);
            }
            gc.setStroke(Color.web("#5a2d0c"));
            gc.setLineWidth(1);
            gc.strokeRect(x, y, w, h);
        }
    }

    // ---------------------------------------------------------------- highlight

    private void highlightPendingCell(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        int col = ClientState.getInstance().getPendingPlaceCol();
        int row = ClientState.getInstance().getPendingPlaceRow();

        gc.setStroke(Color.web("#f1c40f"));
        gc.setLineWidth(2);
        gc.strokeRect(ox + col * cell + 1, oy + row * cell + 1, cell - 2, cell - 2);
    }

    // ---------------------------------------------------------------- tooltip

    private void drawTooltip(GraphicsContext gc, GridConfig grid, TokenDto t) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        int gs = Math.max(1, t.getGridSize());

        double tx = ox + t.getCol() * cell + gs * cell + 4;
        double ty = oy + t.getRow() * cell;

        String[] lines = {
                t.getName(),
                "HP: " + t.getHp() + " / " + t.getMaxHp(),
                "Размер: " + gs + "×" + gs,
                t.getOwnerId() == null ? "NPC" : "Игрок"
        };

        double boxW = 130;
        double boxH = lines.length * 16 + 10;

        // Не выходим за правый край
        if (tx + boxW > getWidth()) tx = ox + t.getCol() * cell - boxW - 4;
        if (ty + boxH > getHeight()) ty = getHeight() - boxH - 4;

        gc.setFill(Color.web("#1a1a2e", 0.92));
        gc.fillRoundRect(tx, ty, boxW, boxH, 6, 6);
        gc.setStroke(Color.web("#95a5a6"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(tx, ty, boxW, boxH, 6, 6);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 12));
        gc.setTextAlign(TextAlignment.LEFT);
        for (int i = 0; i < lines.length; i++) {
            gc.fillText(lines[i], tx + 6, ty + 16 + i * 16);
        }
    }

    // ---------------------------------------------------------------- mouse

    private void onMouseMoved(MouseEvent e) {
        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        int col = (int) ((e.getX() - ox) / cell);
        int row = (int) ((e.getY() - oy) / cell);

        TokenDto found = null;
        for (TokenDto t : ClientState.getInstance().getTokens().values()) {
            int gs = Math.max(1, t.getGridSize());
            if (col >= t.getCol() && col < t.getCol() + gs
                    && row >= t.getRow() && row < t.getRow() + gs) {
                found = t;
                break;
            }
        }

        if (found != hoveredToken) {
            hoveredToken = found;
            render();
        }
    }

    private void onMousePressed(MouseEvent e) {
        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        int col = (int) ((e.getX() - ox) / cell);
        int row = (int) ((e.getY() - oy) / cell);

        if (col < 0 || col >= grid.getCols() || row < 0 || row >= grid.getRows()) return;

        // Ищем токен в клетке с учётом gridSize
        draggingToken = ClientState.getInstance().getTokens().values().stream()
                .filter(t -> {
                    int gs = Math.max(1, t.getGridSize());
                    return col >= t.getCol() && col < t.getCol() + gs
                            && row >= t.getRow() && row < t.getRow() + gs;
                })
                .findFirst()
                .orElse(null);

        if (draggingToken == null) {
            ClientState.getInstance().setPendingPlaceCell(col, row);
        } else {
            dragOffsetX = e.getX() - (ox + draggingToken.getCol() * cell);
            dragOffsetY = e.getY() - (oy + draggingToken.getRow() * cell);
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (draggingToken != null) {
            render();
            GraphicsContext gc = getGraphicsContext2D();
            int cell = grid().getCellSize();
            int gs = Math.max(1, draggingToken.getGridSize());
            double x = e.getX() - dragOffsetX;
            double y = e.getY() - dragOffsetY;
            double w = gs * cell;

            gc.setFill(Color.web("#4a90d9", 0.6));
            gc.fillOval(x + 3, y + 3, w - 6, w - 6);
            gc.setStroke(Color.web("#4a90d9"));
            gc.setLineWidth(2);
            gc.strokeOval(x + 3, y + 3, w - 6, w - 6);
        }
    }

    private void onMouseReleased(MouseEvent e) {
        if (draggingToken == null) return;

        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        int gs = Math.max(1, draggingToken.getGridSize());

        int newCol = (int) ((e.getX() - ox) / cell);
        int newRow = (int) ((e.getY() - oy) / cell);
        newCol = Math.max(0, Math.min(newCol, grid.getCols() - gs));
        newRow = Math.max(0, Math.min(newRow, grid.getRows() - gs));

        com.avalon.dnd.shared.TokenMoveEvent event =
                new com.avalon.dnd.shared.TokenMoveEvent(draggingToken.getId(), newCol, newRow);
        com.avalon.dnd.dm.net.ServerConnection.getInstance().send("/token.move", event);

        draggingToken = null;
        render();
    }

    // ---------------------------------------------------------------- images

    private Image getTokenImage(TokenDto token) {
        String url = token.getImageUrl();
        if (url == null || url.isBlank()) return null;
        return imageCache.computeIfAbsent(url, u -> {
            // Если путь относительный — добавляем serverUrl. Здесь используем
            // простую эвристику: если не начинается с http — это ассет из jar/resources.
            String fullUrl = u.startsWith("http") ? u
                    : "file:src/main/resources" + u; // для dev-режима
            Image img = new Image(fullUrl, true);
            img.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() >= 1.0) javafx.application.Platform.runLater(this::render);
            });
            return img;
        });
    }

    private Image getObjectImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        return imageCache.computeIfAbsent("obj:" + imageUrl, u -> {
            String actual = imageUrl.startsWith("http") ? imageUrl
                    : "file:src/main/resources" + imageUrl;
            Image img = new Image(actual, true);
            img.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() >= 1.0) javafx.application.Platform.runLater(this::render);
            });
            return img;
        });
    }

    public void setBackground(String url) {
        if (url != null && !url.isEmpty()) {
            backgroundImage = new Image(url, true);
            backgroundImage.progressProperty().addListener((obs, old, progress) -> {
                if (progress.doubleValue() >= 1.0 && !backgroundImage.isError()) {
                    javafx.application.Platform.runLater(this::render);
                }
            });
        }
    }
}