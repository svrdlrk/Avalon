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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class BattleMapCanvas extends Canvas {

    private String serverBaseUrl = "http://localhost:8080";

    private TokenDto draggingToken = null;
    private double dragOffsetX, dragOffsetY;
    private Image backgroundImage;
    private String currentBackgroundUrl = null;

    private final Map<String, Image> imageCache = new HashMap<>();
    private TokenDto hoveredToken = null;

    // FIX: guard flag — prevents renderAndResize from running while a previous
    // resize is still being processed by the JavaFX render thread.
    // Without this, rapidly resizing the canvas (e.g. large grid) causes the
    // prism RTTexture NPE / ClassCastException seen in the error log.
    private boolean resizePending = false;

    public BattleMapCanvas() {
        ClientState.getInstance().addChangeListener(this::renderAndResize);

        setOnMousePressed(e -> {
            onMousePressed(e);
            if (draggingToken != null) e.consume();
        });
        setOnMouseDragged(e -> {
            onMouseDragged(e);
            e.consume();
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

    // FIX: coalesce rapid resize calls so we never resize a canvas that is
    // still being rendered — avoids the RTTexture NPE on large grids.
    private void renderAndResize() {
        if (resizePending) return;
        resizePending = true;
        javafx.application.Platform.runLater(() -> {
            resizePending = false;
            GridConfig g = grid();
            double width  = g.getOffsetX() + (double) g.getCols() * g.getCellSize();
            double height = g.getOffsetY() + (double) g.getRows() * g.getCellSize();

            // Safety cap: JavaFX hardware renderer cannot handle textures larger
            // than ~8192 px in either dimension on most GPUs.
            double safeW = Math.min(Math.max(width,  1), 16384);
            double safeH = Math.min(Math.max(height, 1), 16384);

            setWidth(safeW);
            setHeight(safeH);
            render();
        });
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
            if (token == draggingToken) continue;
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
                gc.beginPath();
                gc.rect(x, y, w, h);
                gc.clip();
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
            dragOffsetX = e.getX() - (ox + draggingToken.getCol() * cell);
            dragOffsetY = e.getY() - (oy + draggingToken.getRow() * cell);
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (draggingToken == null) return;
        render();
        GraphicsContext gc = getGraphicsContext2D();
        int cell = grid().getCellSize();
        int gs   = Math.max(1, draggingToken.getGridSize());
        double x = e.getX() - dragOffsetX;
        double y = e.getY() - dragOffsetY;
        double w = gs * cell;

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

    private Image getTokenImage(TokenDto token) {
        String url = token.getImageUrl();
        if (url == null || url.isBlank()) return null;
        String fullUrl = resolveServerUrl(url);
        return imageCache.computeIfAbsent(fullUrl, u -> {
            // FIX: encode before loading so Cyrillic filenames work
            String encoded = encodeUrl(u);
            Image img = new Image(encoded, true);
            img.progressProperty().addListener((obs, old, p) -> {
                if (p.doubleValue() >= 1.0) javafx.application.Platform.runLater(this::render);
            });
            img.errorProperty().addListener((obs, old, err) -> {
                if (err) System.err.println("[canvas] Failed to load image: " + encoded);
            });
            return img;
        });
    }

    private Image getObjectImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String fullUrl = resolveServerUrl(imageUrl);
        return imageCache.computeIfAbsent("obj:" + fullUrl, u -> {
            String encoded = encodeUrl(fullUrl);
            Image img = new Image(encoded, true);
            img.progressProperty().addListener((obs, old, p) -> {
                if (p.doubleValue() >= 1.0) javafx.application.Platform.runLater(this::render);
            });
            return img;
        });
    }

    private String resolveServerUrl(String path) {
        if (path == null || path.isBlank()) return null;
        if (path.startsWith("http://") || path.startsWith("https://")) return path;

        boolean baseEndsWithSlash = serverBaseUrl.endsWith("/");
        boolean pathStartsWithSlash = path.startsWith("/");

        if (baseEndsWithSlash && pathStartsWithSlash) {
            return serverBaseUrl.substring(0, serverBaseUrl.length() - 1) + path;
        }
        if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return serverBaseUrl + "/" + path;
        }
        return serverBaseUrl + path;
    }

    /**
     * Sets background image from a full HTTP URL.
     * Non-ASCII characters (e.g. Cyrillic) in the filename are percent-encoded
     * so that JavaFX Image can load the URL correctly.
     */
    public void setBackground(String fullUrl) {
        String resolved = resolveServerUrl(fullUrl);
        if (resolved == null || resolved.equals(currentBackgroundUrl)) {
            return;   // <-- ИСПРАВЛЕНИЕ: не перезагружаем фон при каждом notifyMapChanged()
        }
        currentBackgroundUrl = resolved;
        System.out.println("[canvas] Loading background: " + resolved);

        String encoded = encodeUrl(resolved);
        backgroundImage = new Image(encoded, true);
        backgroundImage.progressProperty().addListener((obs, old, p) -> {
            if (p.doubleValue() >= 1.0) {
                if (backgroundImage.isError()) {
                    System.err.println("[canvas] Background load error: " + encoded);
                } else {
                    javafx.application.Platform.runLater(this::render);
                }
            }
        });
    }

    /**
     * Percent-encodes non-ASCII and space characters in each path segment of a URL,
     * leaving the scheme, host, port, and '/' separators untouched.
     * Already-encoded sequences (%XX) are preserved.
     */
    private static String encodeUrl(String url) {
        if (url == null) return null;
        try {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd < 0) return url;
            int pathStart = url.indexOf('/', schemeEnd + 3);
            if (pathStart < 0) return url;

            String base = url.substring(0, pathStart);   // "http://localhost:8080"
            String path = url.substring(pathStart);      // "/uploads/maps/uuid_файл.jpg"

            String[] segments = path.split("/", -1);
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) sb.append('/');
                sb.append(encodePathSegment(segments[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return url; // safe fallback
        }
    }

    /** Percent-encodes a single path segment (no slashes). */
    private static String encodePathSegment(String segment) {
        if (segment == null || segment.isEmpty()) return segment == null ? "" : segment;
        try {
            // URI(null, null, rawPath, null) encodes path using RFC 3986 rules
            return new URI(null, null, "/" + segment, null)
                    .toASCIIString()
                    .substring(1); // drop the leading '/' we added
        } catch (Exception e) {
            // Manual fallback: UTF-8 percent-encode everything except unreserved chars
            byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int v = b & 0xFF;
                if ((v >= 'A' && v <= 'Z') || (v >= 'a' && v <= 'z') ||
                        (v >= '0' && v <= '9') ||
                        v == '-' || v == '_' || v == '.' || v == '~' || v == '+') {
                    sb.append((char) v);
                } else {
                    sb.append(String.format("%%%02X", v));
                }
            }
            return sb.toString();
        }
    }

    /** Clears image cache (call when server URL changes). */
    public void clearCache() {
        imageCache.clear();
        backgroundImage = null;
        currentBackgroundUrl = null;   // <-- ИСПРАВЛЕНИЕ
    }
}