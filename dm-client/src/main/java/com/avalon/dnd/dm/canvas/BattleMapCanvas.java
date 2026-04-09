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

    // Server base URL for loading assets via HTTP (set from MainStage)
    private String serverBaseUrl = "http://localhost:8080";

    private TokenDto draggingToken = null;
    private double dragOffsetX, dragOffsetY;   // mouse offset from token top-left
    private Image backgroundImage;

    private final Map<String, Image> imageCache = new HashMap<>();
    private TokenDto hoveredToken = null;

    public BattleMapCanvas() {
        ClientState.getInstance().addChangeListener(this::renderAndResize);

        // FIX: consume mouse events during drag to prevent ScrollPane from panning
        setOnMousePressed(e -> {
            onMousePressed(e);
            if (draggingToken != null) e.consume();
        });
        setOnMouseDragged(e -> {
            onMouseDragged(e);
            e.consume();   // always consume drag — prevents ScrollPane panning
        });
        setOnMouseReleased(e -> {
            onMouseReleased(e);
            e.consume();
        });
        setOnMouseMoved(this::onMouseMoved);

        renderAndResize();
    }

    public void setServerBaseUrl(String url) {
        this.serverBaseUrl = url;
    }

    private GridConfig grid() {
        return ClientState.getInstance().getGrid();
    }

    private void renderAndResize() {
        GridConfig g = grid();
        double width  = g.getOffsetX() + (double) g.getCols() * g.getCellSize();
        double height = g.getOffsetY() + (double) g.getRows() * g.getCellSize();
        setWidth(Math.max(width, 1));
        setHeight(Math.max(height, 1));
        render();
    }

    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        GridConfig grid = grid();

        if (backgroundImage != null && !backgroundImage.isError()
                && backgroundImage.getWidth() > 0) {
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

    // ---- grid ----

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

    // ---- tokens ----

    private void drawTokens(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        String myId = ClientState.getInstance().getPlayerId();

        for (TokenDto token : ClientState.getInstance().getTokens().values()) {
            if (token == draggingToken) continue;   // drawn separately in onMouseDragged
            drawToken(gc, token, ox, oy, cell, myId, 1.0);
        }
    }

    private void drawToken(GraphicsContext gc, TokenDto token,
                           int ox, int oy, int cell, String myId, double alpha) {
        int gs = Math.max(1, token.getGridSize());
        double x = ox + token.getCol() * cell;
        double y = oy + token.getRow() * cell;
        double w = gs * cell;
        double h = gs * cell;

        boolean mine  = myId != null && myId.equals(token.getOwnerId());
        boolean isNpc = token.getOwnerId() == null;

        Color borderColor = mine  ? Color.web("#c9a227")
                : isNpc ? Color.web("#e74c3c")
                : Color.web("#4a90d9");

        gc.setGlobalAlpha(alpha);
        gc.setStroke(borderColor);
        gc.setLineWidth(gs > 1 ? 2.5 : 1.5);
        gc.strokeOval(x + 2, y + 2, w - 4, h - 4);

        Image img = getTokenImage(token);
        if (img != null && !img.isError()) {
            gc.save();
            gc.beginPath();
            gc.arc(x + w / 2, y + h / 2, w / 2 - 3, h / 2 - 3, 0, 360);
            gc.clip();
            gc.drawImage(img, x + 3, y + 3, w - 6, h - 6);
            gc.restore();
        } else {
            Color fill = mine ? Color.web("#c9a227")
                    : isNpc ? Color.web("#c0392b")
                    : Color.web("#4a90d9");
            gc.setFill(fill.deriveColor(0, 1, 1, 0.85));
            gc.fillOval(x + 3, y + 3, w - 6, h - 6);
        }

        // Name
        gc.setGlobalAlpha(alpha);
        gc.setFill(Color.WHITE);
        double fontSize = Math.max(9, Math.min(14, cell * gs / 6.0));
        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, fontSize));
        gc.setTextAlign(TextAlignment.CENTER);
        String label = token.getName().length() > 8
                ? token.getName().substring(0, 7) + "…" : token.getName();
        gc.fillText(label, x + w / 2, y + h / 2 + fontSize / 3);

        // HP bar — DM sees all
        if (token.getMaxHp() > 0) {
            double barW = w - 6, barH = Math.max(4, cell / 12.0);
            double barX = x + 3, barY = y + h - barH - 4;
            double ratio = Math.max(0, (double) token.getHp() / token.getMaxHp());
            gc.setFill(Color.web("#111111", 0.7));
            gc.fillRoundRect(barX, barY, barW, barH, 3, 3);
            Color hpColor = ratio > 0.5 ? Color.web("#2ecc71")
                    : ratio > 0.25 ? Color.web("#f39c12") : Color.web("#e74c3c");
            gc.setFill(hpColor);
            gc.fillRoundRect(barX, barY, barW * ratio, barH, 3, 3);
        }

        if (gs > 1) {
            gc.setFill(Color.web("#ecf0f1", 0.8));
            gc.setFont(Font.font("Arial", 9));
            gc.fillText(gs + "×" + gs, x + w - 14, y + 12);
        }

        gc.setGlobalAlpha(1.0);
    }

    // ---- objects ----

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
                // Fit image covering the cell area (maintain aspect ratio)
                double scaleX = w / img.getWidth();
                double scaleY = h / img.getHeight();
                double scale  = Math.max(scaleX, scaleY);
                double drawW  = img.getWidth()  * scale;
                double drawH  = img.getHeight() * scale;
                double drawX  = x + (w - drawW) / 2;
                double drawY  = y + (h - drawH) / 2;
                gc.save();
                gc.beginPath();
                gc.rect(x, y, w, h);
                gc.clip();
                gc.drawImage(img, drawX, drawY, drawW, drawH);
                gc.restore();
                gc.setFill(Color.web("#000000", 0.12));
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

    // ---- pending highlight ----

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

    // ---- tooltip ----

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

        double boxW = 130, boxH = lines.length * 16 + 10;
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
        for (int i = 0; i < lines.length; i++)
            gc.fillText(lines[i], tx + 6, ty + 16 + i * 16);
    }

    // ---- mouse ----

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
                found = t; break;
            }
        }
        if (found != hoveredToken) { hoveredToken = found; render(); }
    }

    private void onMousePressed(MouseEvent e) {
        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        int col = (int) ((e.getX() - ox) / cell);
        int row = (int) ((e.getY() - oy) / cell);

        if (col < 0 || col >= grid.getCols() || row < 0 || row >= grid.getRows()) return;

        draggingToken = ClientState.getInstance().getTokens().values().stream()
                .filter(t -> {
                    int gs = Math.max(1, t.getGridSize());
                    return col >= t.getCol() && col < t.getCol() + gs
                            && row >= t.getRow() && row < t.getRow() + gs;
                })
                .findFirst().orElse(null);

        if (draggingToken == null) {
            ClientState.getInstance().setPendingPlaceCell(col, row);
        } else {
            // Offset of mouse from token top-left pixel
            dragOffsetX = e.getX() - (ox + draggingToken.getCol() * cell);
            dragOffsetY = e.getY() - (oy + draggingToken.getRow() * cell);
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (draggingToken == null) return;
        render();   // redraw everything without the dragged token
        GraphicsContext gc = getGraphicsContext2D();
        int cell = grid().getCellSize();
        int gs   = Math.max(1, draggingToken.getGridSize());
        double x = e.getX() - dragOffsetX;
        double y = e.getY() - dragOffsetY;
        double w = gs * cell;

        // Draw ghost token at drag position
        String myId = ClientState.getInstance().getPlayerId();
        gc.setGlobalAlpha(0.7);
        Image img = getTokenImage(draggingToken);
        if (img != null && !img.isError()) {
            gc.save();
            gc.beginPath();
            gc.arc(x + w / 2, y + w / 2, w / 2 - 3, w / 2 - 3, 0, 360);
            gc.clip();
            gc.drawImage(img, x + 3, y + 3, w - 6, w - 6);
            gc.restore();
        } else {
            gc.setFill(Color.web("#4a90d9", 0.6));
            gc.fillOval(x + 3, y + 3, w - 6, w - 6);
        }
        gc.setStroke(Color.web("#4a90d9"));
        gc.setLineWidth(2);
        gc.strokeOval(x + 3, y + 3, w - 6, w - 6);
        gc.setGlobalAlpha(1.0);
    }

    private void onMouseReleased(MouseEvent e) {
        if (draggingToken == null) return;

        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox   = grid.getOffsetX();
        int oy   = grid.getOffsetY();
        int gs   = Math.max(1, draggingToken.getGridSize());

        // Token top-left in pixel space
        double tokenX = e.getX() - dragOffsetX;
        double tokenY = e.getY() - dragOffsetY;

        int newCol = (int) Math.round((tokenX - ox) / cell);
        int newRow = (int) Math.round((tokenY - oy) / cell);
        newCol = Math.max(0, Math.min(newCol, grid.getCols() - gs));
        newRow = Math.max(0, Math.min(newRow, grid.getRows() - gs));

        com.avalon.dnd.shared.TokenMoveEvent ev =
                new com.avalon.dnd.shared.TokenMoveEvent(draggingToken.getId(), newCol, newRow);
        com.avalon.dnd.dm.net.ServerConnection.getInstance().send("/token.move", ev);

        draggingToken = null;
        render();
    }

    // ---- images ----

    /**
     * FIX: load token images via HTTP from server, not from local filesystem.
     * This works regardless of the working directory.
     */
    private Image getTokenImage(TokenDto token) {
        String url = token.getImageUrl();
        if (url == null || url.isBlank()) return null;
        String fullUrl = url.startsWith("http") ? url : serverBaseUrl + url;
        return imageCache.computeIfAbsent(fullUrl, u -> {
            Image img = new Image(u, true);
            img.progressProperty().addListener((obs, old, p) -> {
                if (p.doubleValue() >= 1.0) javafx.application.Platform.runLater(this::render);
            });
            img.errorProperty().addListener((obs, old, err) -> {
                if (err) System.err.println("[canvas] Failed to load image: " + u);
            });
            return img;
        });
    }

    private Image getObjectImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String fullUrl = imageUrl.startsWith("http") ? imageUrl : serverBaseUrl + imageUrl;
        return imageCache.computeIfAbsent("obj:" + fullUrl, u -> {
            Image img = new Image(fullUrl, true);
            img.progressProperty().addListener((obs, old, p) -> {
                if (p.doubleValue() >= 1.0) javafx.application.Platform.runLater(this::render);
            });
            return img;
        });
    }

    /**
     * Sets background image from a full HTTP URL.
     */
    public void setBackground(String fullUrl) {
        if (fullUrl == null || fullUrl.isEmpty()) return;
        System.out.println("[canvas] Loading background: " + fullUrl);
        backgroundImage = new Image(fullUrl, true);
        backgroundImage.progressProperty().addListener((obs, old, p) -> {
            if (p.doubleValue() >= 1.0) {
                if (backgroundImage.isError()) {
                    System.err.println("[canvas] Background load error: " + fullUrl);
                } else {
                    javafx.application.Platform.runLater(this::render);
                }
            }
        });
    }

    /** Clears image cache (call when server URL changes). */
    public void clearCache() {
        imageCache.clear();
        backgroundImage = null;
    }
}