package com.avalon.dnd.dm.canvas;

import com.avalon.dnd.dm.config.RuntimeConfig;
import com.avalon.dnd.dm.model.ClientState;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.TokenDto;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleMapCanvas extends Canvas {

    private String serverBaseUrl = RuntimeConfig.defaultServerUrl();

    private TokenDto draggingToken = null;
    private double dragOffsetX, dragOffsetY;
    private Image backgroundImage;
    private String currentBackgroundUrl = null;
    private double zoom = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;
    private double lastCanvasWidth = -1.0;
    private double lastCanvasHeight = -1.0;

    private final Map<String, Image> imageCache = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > 100;
        }
    };
    private TokenDto hoveredToken = null;
    private final Runnable stateChangeListener = this::renderAndResize;
    private boolean disposed = false;

    private boolean panning = false;
    private boolean panArmed = false;
    private double panStartX;
    private double panStartY;
    private double panOriginOffsetX;
    private double panOriginOffsetY;
    private static final double PAN_THRESHOLD = 5.0;

    // FIX: guard flag — prevents renderAndResize from running while a previous
    // resize is still being processed by the JavaFX render thread.
    // Without this, rapidly resizing the canvas (e.g. large grid) causes the
    // prism RTTexture NPE / ClassCastException seen in the error log.
    private boolean resizePending = false;
    private boolean renderPending = false;
    private boolean renderDirty = false;
    private boolean safeRenderingMode = false;

    public BattleMapCanvas() {
        ClientState.getInstance().addChangeListener(stateChangeListener);

        setOnMousePressed(e -> {
            onMousePressed(e);
            if (draggingToken != null) {
                e.consume();
                return;
            }
            if (e.getButton() == MouseButton.MIDDLE || e.isSecondaryButtonDown()) {
                beginPan(e);
                e.consume();
                return;
            }
            if (e.getButton() == MouseButton.PRIMARY) {
                armPan(e);
            }
        });
        setOnMouseDragged(e -> {
            if (handlePanDrag(e)) {
                e.consume();
                return;
            }
            onMouseDragged(e);
            e.consume();
        });
        setOnMouseReleased(e -> {
            if (finishPan(e)) {
                e.consume();
                return;
            }
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

    // FIX: coalesce rapid resize calls so we never resize a canvas that is
    // still being rendered — avoids the RTTexture NPE on large grids.
    private void renderAndResize() {
        if (disposed || resizePending) return;
        resizePending = true;
        javafx.application.Platform.runLater(() -> {
            resizePending = false;
            if (disposed) {
                return;
            }
            GridConfig g = grid();
            double width  = g.getOffsetX() + (double) g.getCols() * g.getCellSize();
            double height = g.getOffsetY() + (double) g.getRows() * g.getCellSize();

            // Safety cap: JavaFX hardware renderer cannot handle textures larger
            // than ~8192 px in either dimension on most GPUs.
            double safeW = Math.min(Math.max(width,  1), 8192);
            double safeH = Math.min(Math.max(height, 1), 8192);

            boolean resized = false;
            if (Math.abs(lastCanvasWidth - safeW) > 0.5) {
                setWidth(safeW);
                lastCanvasWidth = safeW;
                resized = true;
            }
            if (Math.abs(lastCanvasHeight - safeH) > 0.5) {
                setHeight(safeH);
                lastCanvasHeight = safeH;
                resized = true;
            }
            if (resized) {
                requestRender();
            } else {
                requestRender();
            }
        });
    }

    private void requestRender() {
        if (disposed) {
            return;
        }
        renderDirty = true;
        if (renderPending) {
            return;
        }
        renderPending = true;
        javafx.application.Platform.runLater(() -> {
            renderPending = false;
            if (disposed || !renderDirty) {
                return;
            }
            renderDirty = false;
            render();
            if (renderDirty && !renderPending) {
                requestRender();
            }
        });
    }

    public void render() {
        if (disposed) {
            return;
        }
        GraphicsContext gc = getGraphicsContext2D();
        GridConfig grid = grid();

        try {
            gc.save();
            gc.clearRect(0, 0, getWidth(), getHeight());
            gc.translate(offsetX, offsetY);
            gc.scale(zoom, zoom);

            if (backgroundImage != null && !backgroundImage.isError()
                    && backgroundImage.getWidth() > 0) {
                gc.drawImage(backgroundImage, 0, 0, getWidth(), getHeight());
            } else {
                gc.setFill(Color.web("#2b2b2b"));
                gc.fillRect(0, 0, getWidth(), getHeight());
            }

            drawReferenceOverlay(gc);
            drawTerrainLayer(gc);
            drawWallLayer(gc);
            drawGrid(gc, grid);
            drawObjects(gc, grid);
            drawTokens(gc, grid);
            highlightPendingCell(gc, grid);
            if (hoveredToken != null) drawTooltip(gc, grid, hoveredToken);
            gc.restore();
        } catch (Throwable t) {
            if (!safeRenderingMode) {
                safeRenderingMode = true;
                System.err.println("[dm-client] switching to safe rendering mode after canvas failure: " + t);
            }
            try {
                gc.restore();
            } catch (Exception ignored) {
            }
            gc.clearRect(0, 0, getWidth(), getHeight());
            gc.translate(offsetX, offsetY);
            gc.scale(zoom, zoom);
            if (backgroundImage != null && !backgroundImage.isError()
                    && backgroundImage.getWidth() > 0) {
                gc.drawImage(backgroundImage, 0, 0, getWidth(), getHeight());
            } else {
                gc.setFill(Color.web("#2b2b2b"));
                gc.fillRect(0, 0, getWidth(), getHeight());
            }
            drawReferenceOverlay(gc);
            drawTerrainLayer(gc);
            drawWallLayer(gc);
            drawGrid(gc, grid);
            drawObjects(gc, grid);
            drawTokens(gc, grid);
            highlightPendingCell(gc, grid);
            if (hoveredToken != null) drawTooltip(gc, grid, hoveredToken);
            gc.restore();
        }
    }

    public void panBy(double dx, double dy) {
        if (disposed) {
            return;
        }
        offsetX += dx;
        offsetY += dy;
        requestRender();
    }

    public void setPan(double x, double y) {
        if (disposed) {
            return;
        }
        offsetX = x;
        offsetY = y;
        requestRender();
    }

    public boolean isPanning() {
        return panning;
    }

    private double visibleLeft() {
        return (-offsetX) / zoom;
    }

    private double visibleTop() {
        return (-offsetY) / zoom;
    }

    private double visibleRight() {
        return visibleLeft() + getWidth() / zoom;
    }

    private double visibleBottom() {
        return visibleTop() + getHeight() / zoom;
    }

    private boolean isVisible(double x, double y, double width, double height) {
        double left = visibleLeft();
        double top = visibleTop();
        double right = visibleRight();
        double bottom = visibleBottom();
        return x < right && x + width > left && y < bottom && y + height > top;
    }

    public TokenDto getTokenAt(double screenX, double screenY) {
        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        double worldX = (screenX - offsetX) / zoom;
        double worldY = (screenY - offsetY) / zoom;
        int col = (int) ((worldX - ox) / cell);
        int row = (int) ((worldY - oy) / cell);
        for (TokenDto t : ClientState.getInstance().getTokens().values()) {
            int gs = Math.max(1, t.getGridSize());
            if (col >= t.getCol() && col < t.getCol() + gs
                    && row >= t.getRow() && row < t.getRow() + gs) {
                return t;
            }
        }
        return null;
    }

    private void armPan(MouseEvent e) {
        panArmed = true;
        panning = false;
        panStartX = e.getX();
        panStartY = e.getY();
        panOriginOffsetX = offsetX;
        panOriginOffsetY = offsetY;
    }

    private void beginPan(MouseEvent e) {
        panArmed = false;
        panning = true;
        panStartX = e.getX();
        panStartY = e.getY();
        panOriginOffsetX = offsetX;
        panOriginOffsetY = offsetY;
        setCursor(javafx.scene.Cursor.MOVE);
    }

    private boolean handlePanDrag(MouseEvent e) {
        if (draggingToken != null) {
            return false;
        }
        if (!panArmed && !panning) {
            return false;
        }
        double dx = e.getX() - panStartX;
        double dy = e.getY() - panStartY;
        if (!panning && Math.hypot(dx, dy) >= PAN_THRESHOLD) {
            panning = true;
            setCursor(javafx.scene.Cursor.MOVE);
        }
        if (!panning) {
            return false;
        }
        offsetX = panOriginOffsetX + dx;
        offsetY = panOriginOffsetY + dy;
        requestRender();
        return true;
    }

    private boolean finishPan(MouseEvent e) {
        if (panning || panArmed) {
            if (panning) {
                setCursor(javafx.scene.Cursor.DEFAULT);
            }
            boolean wasPanning = panning;
            panArmed = false;
            panning = false;
            return wasPanning;
        }
        return false;
    }

    // ---- grid ----

    private void drawGrid(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        gc.setStroke(Color.web("#444444"));
        gc.setLineWidth(0.5);

        double left = visibleLeft();
        double top = visibleTop();
        double right = visibleRight();
        double bottom = visibleBottom();

        int startCol = Math.max(0, (int) Math.floor((left - ox) / cell) - 1);
        int endCol = Math.min(grid.getCols(), (int) Math.ceil((right - ox) / cell) + 1);
        int startRow = Math.max(0, (int) Math.floor((top - oy) / cell) - 1);
        int endRow = Math.min(grid.getRows(), (int) Math.ceil((bottom - oy) / cell) + 1);

        for (int c = startCol; c <= endCol; c++) {
            double x = ox + c * cell;
            gc.strokeLine(x, oy + startRow * cell, x, oy + endRow * cell);
        }
        for (int r = startRow; r <= endRow; r++) {
            double y = oy + r * cell;
            gc.strokeLine(ox + startCol * cell, y, ox + endCol * cell, y);
        }
    }

    // ---- tokens ----

    private void drawTokens(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        String myId = ClientState.getInstance().getPlayerId();

        for (TokenDto token : ClientState.getInstance().getTokens().values()) {
            if (token == draggingToken) continue;
            int gs = Math.max(1, token.getGridSize());
            double x = ox + token.getCol() * cell;
            double y = oy + token.getRow() * cell;
            double size = gs * cell;
            if (!isVisible(x, y, size, size)) continue;
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
        double facingAngle = token.getFacingAngleDeg();

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
            gc.translate(x + w / 2, y + h / 2);
            gc.rotate(facingAngle);
            gc.drawImage(img, -w / 2 + 3, -h / 2 + 3, w - 6, h - 6);
            gc.restore();
        } else {
            Color fill = mine ? Color.web("#c9a227")
                    : isNpc ? Color.web("#c0392b")
                    : Color.web("#4a90d9");
            gc.setFill(fill.deriveColor(0, 1, 1, 0.85));
            gc.fillOval(x + 3, y + 3, w - 6, h - 6);
        }

        gc.setGlobalAlpha(alpha);
        gc.setFill(Color.WHITE);
        double fontSize = Math.max(9, Math.min(14, cell * gs / 6.0));
        gc.setFont(Font.font("Arial", javafx.scene.text.FontWeight.BOLD, fontSize));
        gc.setTextAlign(TextAlignment.CENTER);
        String label = token.getName().length() > 8
                ? token.getName().substring(0, 7) + "…" : token.getName();
        gc.fillText(label, x + w / 2, y + h / 2 + fontSize / 3);

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
            if (!isVisible(x, y, w, h)) continue;

            Image img = getObjectImage(obj.getImageUrl());
            if (img != null && !img.isError()) {
                double scaleX = w / img.getWidth();
                double scaleY = h / img.getHeight();
                double scale  = Math.max(scaleX, scaleY);
                double drawW  = img.getWidth()  * scale;
                double drawH  = img.getHeight() * scale;
                double drawX  = x + (w - drawW) / 2;
                double drawY  = y + (h - drawH) / 2;
                gc.save();
                gc.drawImage(img, drawX, drawY, drawW, drawH);
                gc.restore();
            } else {
                gc.setFill(Color.web("#8B4513"));
                gc.fillRect(x, y, w, h);
                gc.setStroke(Color.web("#5a2d0c"));
                gc.setLineWidth(1);
                gc.strokeRect(x, y, w, h);
            }
        }
    }

    // ---- pending highlight ----

    private void highlightPendingCell(GraphicsContext gc, GridConfig grid) {
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        int col = ClientState.getInstance().getPendingPlaceCol();
        int row = ClientState.getInstance().getPendingPlaceRow();
        double x = ox + col * cell;
        double y = oy + row * cell;
        if (!isVisible(x, y, cell, cell)) {
            return;
        }
        gc.setStroke(Color.web("#f1c40f"));
        gc.setLineWidth(2);
        gc.strokeRect(x + 1, y + 1, cell - 2, cell - 2);
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
        double worldX = (e.getX() - offsetX) / zoom;
        double worldY = (e.getY() - offsetY) / zoom;
        int col = (int) ((worldX - ox) / cell);
        int row = (int) ((worldY - oy) / cell);

        TokenDto found = null;
        for (TokenDto t : ClientState.getInstance().getTokens().values()) {
            int gs = Math.max(1, t.getGridSize());
            if (col >= t.getCol() && col < t.getCol() + gs
                    && row >= t.getRow() && row < t.getRow() + gs) {
                found = t; break;
            }
        }
        if (found != hoveredToken) { hoveredToken = found; requestRender(); }
    }

    private void onMousePressed(MouseEvent e) {
        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        double worldX = (e.getX() - offsetX) / zoom;
        double worldY = (e.getY() - offsetY) / zoom;
        int col = (int) ((worldX - ox) / cell);
        int row = (int) ((worldY - oy) / cell);

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
            dragOffsetX = worldX - (ox + draggingToken.getCol() * cell);
            dragOffsetY = worldY - (oy + draggingToken.getRow() * cell);
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (draggingToken == null) return;
        requestRender();
        GraphicsContext gc = getGraphicsContext2D();
        int cell = grid().getCellSize();
        int gs   = Math.max(1, draggingToken.getGridSize());
        double worldX = (e.getX() - offsetX) / zoom - dragOffsetX;
        double worldY = (e.getY() - offsetY) / zoom - dragOffsetY;
        double screenX = worldX * zoom + offsetX;
        double screenY = worldY * zoom + offsetY;
        double drawW = gs * cell * zoom;

        gc.setGlobalAlpha(0.7);
        Image img = getTokenImage(draggingToken);
        if (img != null && !img.isError()) {
            gc.save();
            gc.drawImage(img, screenX + 3, screenY + 3, drawW - 6, drawW - 6);
            gc.restore();
        } else {
            gc.setFill(Color.web("#4a90d9", 0.6));
            gc.fillOval(screenX + 3, screenY + 3, drawW - 6, drawW - 6);
        }
        gc.setStroke(Color.web("#4a90d9"));
        gc.setLineWidth(2);
        gc.strokeOval(screenX + 3, screenY + 3, drawW - 6, drawW - 6);
        gc.setGlobalAlpha(1.0);
    }

    private void onMouseReleased(MouseEvent e) {
        if (draggingToken == null) return;

        GridConfig grid = grid();
        int cell = grid.getCellSize();
        int ox   = grid.getOffsetX();
        int oy   = grid.getOffsetY();
        int gs   = Math.max(1, draggingToken.getGridSize());

        double tokenX = (e.getX() - offsetX) / zoom - dragOffsetX;
        double tokenY = (e.getY() - offsetY) / zoom - dragOffsetY;

        int newCol = (int) Math.round((tokenX - ox) / cell);
        int newRow = (int) Math.round((tokenY - oy) / cell);
        newCol = Math.max(0, Math.min(newCol, grid.getCols() - gs));
        newRow = Math.max(0, Math.min(newRow, grid.getRows() - gs));

        com.avalon.dnd.shared.TokenMoveEvent ev =
                new com.avalon.dnd.shared.TokenMoveEvent(draggingToken.getId(), newCol, newRow);
        com.avalon.dnd.dm.net.ServerConnection.getInstance().send("/token.move", ev);

        draggingToken = null;
        requestRender();
    }

    private void drawReferenceOverlay(GraphicsContext gc) {
        Map<String, Object> map = ClientState.getInstance().getReferenceOverlayLayerMap();
        if (map == null) {
            return;
        }

        boolean visible = getBoolean(map.get("visible"), true);
        if (!visible) {
            return;
        }

        String imageUrl = getString(map.get("imageUrl"));
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        Image image = loadImage(resolveServerUrl(imageUrl));
        if (image == null || image.isError()) {
            return;
        }

        double opacity = clamp(getDouble(map.get("opacity"), 0.65), 0.0, 1.0);
        double scale = Math.max(0.1, getDouble(map.get("scale"), 1.0));
        double offsetX = getDouble(map.get("offsetX"), 0.0);
        double offsetY = getDouble(map.get("offsetY"), 0.0);

        gc.save();
        gc.setGlobalAlpha(opacity);
        gc.drawImage(image, offsetX, offsetY, image.getWidth() * scale, image.getHeight() * scale);
        gc.restore();
    }


    private void drawTerrainLayer(GraphicsContext gc) {
        Map<String, Object> map = ClientState.getInstance().getTerrainLayerMap();
        if (map == null) {
            return;
        }
        Object cellsObj = map.get("cells");
        if (!(cellsObj instanceof List<?> cells)) {
            return;
        }
        int cellSize = grid().getCellSize();
        int ox = grid().getOffsetX();
        int oy = grid().getOffsetY();
        for (Object cellObj : cells) {
            if (!(cellObj instanceof java.util.Map<?, ?> cell)) continue;
            int col = (int) getDouble(cell.get("col"), 0.0);
            int row = (int) getDouble(cell.get("row"), 0.0);
            int width = Math.max(1, (int) getDouble(cell.get("width"), 1.0));
            int height = Math.max(1, (int) getDouble(cell.get("height"), 1.0));
            if (!getBoolean(cell.get("visible"), true)) continue;
            double x = ox + col * cellSize;
            double y = oy + row * cellSize;
            double w = width * cellSize;
            double h = height * cellSize;
            if (!isVisible(x, y, w, h)) continue;
            String type = getString(cell.get("terrainType"));
            Color fill = terrainColor(type, getBoolean(cell.get("blocksMovement"), false), getBoolean(cell.get("blocksSight"), false));
            gc.setFill(fill);
            gc.fillRect(x, y, w, h);
        }
    }

    private void drawWallLayer(GraphicsContext gc) {
        Map<String, Object> map = ClientState.getInstance().getWallLayerMap();
        if (map == null) {
            return;
        }
        List<?> paths = firstList(map, "paths", "walls", "segments", "polylines", "lines");
        if (paths == null) {
            return;
        }
        for (Object pathObj : paths) {
            if (!(pathObj instanceof java.util.Map<?, ?> path)) continue;
            List<?> points = firstList(path, "points", "vertices", "coords", "pts");
            if (points == null || points.size() < 2) continue;
            double[] xs = new double[points.size()];
            double[] ys = new double[points.size()];
            int i = 0;
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (Object ptObj : points) {
                if (!(ptObj instanceof java.util.Map<?, ?> pt)) continue;
                double x = getDouble(pt.get("x"), 0.0);
                double y = getDouble(pt.get("y"), 0.0);
                xs[i] = x;
                ys[i] = y;
                i++;
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
            if (i < 2) continue;
            double thickness = Math.max(1.5, getDouble(path.get("thickness"), 2.5));
            if (!isVisible(minX - thickness, minY - thickness, (maxX - minX) + thickness * 2, (maxY - minY) + thickness * 2)) {
                continue;
            }
            gc.setStroke(getBoolean(path.get("blocksSight"), true) ? Color.web("#ecf0f1", 0.9) : Color.web("#95a5a6", 0.75));
            gc.setLineWidth(thickness);
            gc.strokePolyline(xs, ys, i);
        }
    }

    private Color terrainColor(String type, boolean blocksMovement, boolean blocksSight) {
        if (type == null) type = "grass";
        String t = type.toLowerCase();
        if (t.contains("water")) return Color.web("#3498db", 0.28);
        if (t.contains("sand")) return Color.web("#f1c40f", 0.18);
        if (t.contains("stone") || t.contains("rock")) return Color.web("#95a5a6", 0.20);
        if (t.contains("dirt") || t.contains("mud")) return Color.web("#8b4513", 0.18);
        if (blocksMovement || blocksSight) return Color.web("#2c3e50", 0.16);
        return Color.web("#2ecc71", 0.12);
    }

    private Image loadImage(String fullUrl) {
        if (fullUrl == null || fullUrl.isBlank()) return null;
        return imageCache.computeIfAbsent("ref:" + fullUrl, u -> {
            try {
                String encoded = encodeUrl(fullUrl);
                Image img = new Image(encoded, true);
                img.progressProperty().addListener((obs, old, p) -> {
                    if (disposed) return;
                    if (p.doubleValue() >= 1.0) javafx.application.Platform.runLater(() -> {
                        if (!disposed) requestRender();
                    });
                });
                if (img.getProgress() >= 1.0) {
                    javafx.application.Platform.runLater(() -> {
                        if (!disposed) requestRender();
                    });
                }
                return img;
            } catch (Exception ex) {
                System.err.println("[canvas] Failed to load image: " + fullUrl + " -> " + ex.getMessage());
                return null;
            }
        });
    }

    private static String getString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<?> firstList(java.util.Map<?, ?> map, String... keys) {
        if (map == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = map.get(key);
            if (value instanceof List<?> list) {
                return list;
            }
        }
        return null;
    }

    private static boolean getBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static double getDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(value)); } catch (Exception e) { return defaultValue; }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public void setZoom(double zoom) {
        this.zoom = Math.max(0.2, Math.min(4.0, zoom));
        requestRender();
    }

    public double getZoom() {
        return zoom;
    }

    public void zoomBy(double factor) {
        setZoom(this.zoom * factor);
    }

    public void resetView() {
        this.zoom = 1.0;
        this.offsetX = 0.0;
        this.offsetY = 0.0;
        requestRender();
    }

    public void centerView() {
        GridConfig grid = grid();
        double contentW = grid.getOffsetX() + (double) grid.getCols() * grid.getCellSize();
        double contentH = grid.getOffsetY() + (double) grid.getRows() * grid.getCellSize();
        this.offsetX = (getWidth() - contentW * zoom) / 2.0;
        this.offsetY = (getHeight() - contentH * zoom) / 2.0;
        requestRender();
    }

    public void fitView(double viewportW, double viewportH) {
        GridConfig grid = grid();
        double contentW = Math.max(1, grid.getOffsetX() + (double) grid.getCols() * grid.getCellSize());
        double contentH = Math.max(1, grid.getOffsetY() + (double) grid.getRows() * grid.getCellSize());
        double target = Math.min(viewportW / contentW, viewportH / contentH);
        this.zoom = Math.max(0.35, Math.min(1.0, target));
        this.offsetX = (viewportW - contentW * zoom) / 2.0;
        this.offsetY = (viewportH - contentH * zoom) / 2.0;
        requestRender();
    }

    // ---- images ----

    private Image getTokenImage(TokenDto token) {
        String url = token.getImageUrl();
        if (url == null || url.isBlank()) return null;
        String fullUrl = resolveServerUrl(url);
        if (fullUrl == null || fullUrl.isBlank()) return null;
        return imageCache.computeIfAbsent(fullUrl, u -> {
            try {
                String encoded = encodeUrl(u);
                Image img = new Image(encoded, true);
                img.progressProperty().addListener((obs, old, p) -> {
                    if (disposed) return;
                    if (p.doubleValue() >= 1.0) javafx.application.Platform.runLater(() -> {
                        if (!disposed) requestRender();
                    });
                });
                img.errorProperty().addListener((obs, old, err) -> {
                    if (err) System.err.println("[canvas] Failed to load image: " + encoded);
                });
                if (img.getProgress() >= 1.0) {
                    javafx.application.Platform.runLater(() -> {
                        if (!disposed) requestRender();
                    });
                }
                return img;
            } catch (Exception ex) {
                System.err.println("[canvas] Failed to load token image: " + u + " -> " + ex.getMessage());
                return null;
            }
        });
    }

    private Image getObjectImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String fullUrl = resolveServerUrl(imageUrl);
        if (fullUrl == null || fullUrl.isBlank()) return null;
        return imageCache.computeIfAbsent("obj:" + fullUrl, u -> {
            try {
                String encoded = encodeUrl(fullUrl);
                Image img = new Image(encoded, true);
                img.progressProperty().addListener((obs, old, p) -> {
                    if (disposed) return;
                    if (p.doubleValue() >= 1.0) javafx.application.Platform.runLater(() -> {
                        if (!disposed) requestRender();
                    });
                });
                if (img.getProgress() >= 1.0) {
                    javafx.application.Platform.runLater(() -> {
                        if (!disposed) requestRender();
                    });
                }
                return img;
            } catch (Exception ex) {
                System.err.println("[canvas] Failed to load object image: " + u + " -> " + ex.getMessage());
                return null;
            }
        });
    }

    private String resolveServerUrl(String path) {
        return BattleMapCanvasUrlSupport.resolveServerUrl(serverBaseUrl, path);
    }

    /**
     * Sets background image from a full HTTP URL.
     * Non-ASCII characters (e.g. Cyrillic) in the filename are percent-encoded
     * so that JavaFX Image can load the URL correctly.
     */
    public void setBackground(String fullUrl) {
        if (disposed) {
            return;
        }
        String resolved = resolveServerUrl(fullUrl);
        if (resolved == null || resolved.equals(currentBackgroundUrl)) {
            return;   // <-- ИСПРАВЛЕНИЕ: не перезагружаем фон при каждом notifyMapChanged()
        }
        currentBackgroundUrl = resolved;
        System.out.println("[canvas] Loading background: " + resolved);

        String encoded = BattleMapCanvasUrlSupport.encodeUrl(resolved);
        try {
            backgroundImage = new Image(encoded, true);
        } catch (Exception ex) {
            System.err.println("[canvas] Failed to load background: " + encoded + " -> " + ex.getMessage());
            backgroundImage = null;
            javafx.application.Platform.runLater(() -> requestRender());
            return;
        }
        backgroundImage.progressProperty().addListener((obs, old, p) -> {
            if (p.doubleValue() >= 1.0) {
                if (backgroundImage.isError()) {
                    System.err.println("[canvas] Background load error: " + encoded);
                }
                javafx.application.Platform.runLater(() -> requestRender());
            }
        });
        javafx.application.Platform.runLater(() -> requestRender());
    }

    public void dispose() {
        disposed = true;
        ClientState.getInstance().removeChangeListener(stateChangeListener);
        setOnMousePressed(null);
        setOnMouseDragged(null);
        setOnMouseReleased(null);
        setOnMouseMoved(null);
        setOnScroll(null);
        draggingToken = null;
        hoveredToken = null;
        panning = false;
        panArmed = false;
        setCursor(javafx.scene.Cursor.DEFAULT);
        imageCache.clear();
    }

    private static String encodeUrl(String url) {
        return BattleMapCanvasUrlSupport.encodeUrl(url);
    }

    /** Clears image cache (call when server URL changes). */
    public void clearCache() {
        imageCache.clear();
        backgroundImage = null;
        currentBackgroundUrl = null;   // <-- ИСПРАВЛЕНИЕ
    }
}
