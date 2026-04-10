package com.avalon.dnd.mapeditor.ui;

import com.avalon.dnd.mapeditor.model.BackgroundLayer;
import com.avalon.dnd.mapeditor.model.BackgroundMode;
import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.model.PlacementKind;
import javafx.application.Platform;
import javafx.geometry.VPos;
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

public class MapEditorCanvas extends Canvas {

    private final EditorState state;
    private final Map<String, Image> imageCache = new HashMap<>();

    private MapPlacement hoverPlacement;
    private Integer hoverCol;
    private Integer hoverRow;

    private boolean resizePending;

    public MapEditorCanvas(EditorState state) {
        this.state = state;

        setFocusTraversable(true);

        state.addListener(evt -> requestRender());

        setOnMousePressed(e -> {
            requestFocus();
            if (state.getActiveTool() != null) {
                state.getActiveTool().onMousePressed(e, this, state);
            }
        });
        setOnMouseDragged(e -> {
            if (state.getActiveTool() != null) {
                state.getActiveTool().onMouseDragged(e, this, state);
            }
        });
        setOnMouseReleased(e -> {
            if (state.getActiveTool() != null) {
                state.getActiveTool().onMouseReleased(e, this, state);
            }
        });
        setOnMouseMoved(e -> {
            updateHover(e.getX(), e.getY());
            if (state.getActiveTool() != null) {
                state.getActiveTool().onMouseMoved(e, this, state);
            }
        });

        setOnScroll(e -> {
            double zoom = state.getZoom();
            if (e.getDeltaY() > 0) {
                state.setZoom(zoom * 1.08);
            } else {
                state.setZoom(zoom / 1.08);
            }
            requestRender();
        });

        requestRender();
    }

    public void requestRender() {
        if (resizePending) return;
        resizePending = true;
        Platform.runLater(() -> {
            resizePending = false;
            renderAndFit();
        });
    }

    private void renderAndFit() {
        MapProject project = state.getProject();
        if (project == null) return;

        double width = Math.max(1200, state.grid().getOffsetX() + (double) state.grid().getCols() * state.grid().getCellSize());
        double height = Math.max(800, state.grid().getOffsetY() + (double) state.grid().getRows() * state.grid().getCellSize());

        setWidth(width);
        setHeight(height);
        render();
    }

    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        MapProject project = state.getProject();
        if (project == null) {
            gc.clearRect(0, 0, getWidth(), getHeight());
            return;
        }

        gc.setFill(Color.web("#1f1f1f"));
        gc.fillRect(0, 0, getWidth(), getHeight());

        gc.save();
        gc.translate(state.getViewOffsetX(), state.getViewOffsetY());
        gc.scale(state.getZoom(), state.getZoom());

        drawBackground(gc);
        drawGrid(gc);
        drawPlacements(gc);
        drawFogPreview(gc);

        if (hoverCol != null && hoverRow != null) {
            drawHoverCell(gc);
        }

        MapPlacement selected = state.selectedPlacement();
        if (selected != null) {
            drawSelection(gc, selected);
        }

        gc.restore();

        if (hoverPlacement != null) {
            drawPlacementTooltip(gc, hoverPlacement);
        }

        drawStatus(gc);
    }

    private void drawBackground(GraphicsContext gc) {
        MapProject project = state.getProject();
        if (project == null) {
            return;
        }

        BackgroundLayer background = project.getBackgroundLayer();
        if (background == null) {
            return;
        }

        String backgroundUrl = background.getImageUrl();
        if (backgroundUrl == null || backgroundUrl.isBlank()) {
            return;
        }

        String cacheKey = "bg:" + backgroundUrl;
        Image image = imageCache.get(cacheKey);

        if (image == null) {
            image = loadImage(backgroundUrl);
            if (image != null) {
                imageCache.put(cacheKey, image);
            } else {
                return;
            }
        }

        if (image.isError()) {
            return;
        }

        double canvasWidth = getWidth();
        double canvasHeight = getHeight();

        double scale = background.getScale() > 0 ? background.getScale() : 1.0;
        double offsetX = background.getOffsetX();
        double offsetY = background.getOffsetY();
        double opacity = background.getOpacity();

        BackgroundMode mode = background.getMode();
        if (mode == null) {
            mode = BackgroundMode.FREE;
        }

        gc.save();
        gc.setGlobalAlpha(opacity);

        switch (mode) {
            case STRETCH -> {
                gc.drawImage(image, 0, 0, canvasWidth, canvasHeight);
            }
            case FIT -> {
                double imgW = image.getWidth();
                double imgH = image.getHeight();

                if (imgW <= 0 || imgH <= 0) {
                    gc.restore();
                    return;
                }

                double scaleX = canvasWidth / imgW;
                double scaleY = canvasHeight / imgH;
                double fitScale = Math.min(scaleX, scaleY) * scale;

                double drawW = imgW * fitScale;
                double drawH = imgH * fitScale;
                double x = (canvasWidth - drawW) / 2.0 + offsetX;
                double y = (canvasHeight - drawH) / 2.0 + offsetY;

                gc.drawImage(image, x, y, drawW, drawH);
            }
            case TILE -> {
                double tileW = image.getWidth() * scale;
                double tileH = image.getHeight() * scale;

                if (tileW <= 0 || tileH <= 0) {
                    gc.restore();
                    return;
                }

                double startX = offsetX % tileW;
                double startY = offsetY % tileH;

                for (double x = startX - tileW; x < canvasWidth; x += tileW) {
                    for (double y = startY - tileH; y < canvasHeight; y += tileH) {
                        gc.drawImage(image, x, y, tileW, tileH);
                    }
                }
            }
            case FREE -> {
                double drawW = image.getWidth() * scale;
                double drawH = image.getHeight() * scale;
                gc.drawImage(image, offsetX, offsetY, drawW, drawH);
            }
        }

        gc.restore();
    }

    private void drawGrid(GraphicsContext gc) {
        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        gc.setStroke(Color.web("#3b3b3b"));
        gc.setLineWidth(0.6 / state.getZoom());

        for (int c = 0; c <= grid.getCols(); c++) {
            double x = ox + c * cell;
            gc.strokeLine(x, oy, x, oy + grid.getRows() * cell);
        }
        for (int r = 0; r <= grid.getRows(); r++) {
            double y = oy + r * cell;
            gc.strokeLine(ox, y, ox + grid.getCols() * cell, y);
        }
    }

    private void drawPlacements(GraphicsContext gc) {
        MapProject project = state.getProject();
        if (project == null) return;

        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        for (MapLayer layer : project.getLayers()) {
            if (!layer.isVisible()) continue;

            gc.save();
            gc.setGlobalAlpha(layer.getOpacity());

            for (MapPlacement placement : project.getPlacements()) {
                if (!layer.getId().equals(placement.getLayerId())) continue;

                double x = ox + placement.getCol() * cell;
                double y = oy + placement.getRow() * cell;
                double w = placement.effectiveWidth() * cell;
                double h = placement.effectiveHeight() * cell;

                Image image = imageCache.computeIfAbsent("obj:" + placement.getImageUrl(), key -> loadImage(placement.getImageUrl()));
                if (image != null && !image.isError()) {
                    gc.save();
                    gc.beginPath();
                    gc.rect(x, y, w, h);
                    gc.clip();
                    gc.drawImage(image, x, y, w, h);
                    gc.restore();
                } else {
                    gc.setFill(colorFor(placement));
                    gc.fillRoundRect(x + 1, y + 1, Math.max(1, w - 2), Math.max(1, h - 2), 4, 4);
                }

                if (placement.isLocked()) {
                    gc.setStroke(Color.web("#9ca3af"));
                    gc.setLineWidth(1.5 / state.getZoom());
                    gc.strokeRect(x + 2, y + 2, w - 4, h - 4);
                }

                if (placement.isSelected()) {
                    gc.setStroke(Color.web("#f1c40f"));
                    gc.setLineWidth(2 / state.getZoom());
                    gc.strokeRect(x + 1, y + 1, w - 2, h - 2);
                }
            }

            gc.restore();
        }
    }

    private void drawFogPreview(GraphicsContext gc) {
        if (!state.isFogPreviewEnabled() || state.getProject() == null) {
            return;
        }

        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        gc.save();
        gc.setFill(Color.color(0.0, 0.0, 0.0, 0.12));
        for (MapPlacement placement : state.getProject().getPlacements()) {
            if (!placement.isBlocksSight()) continue;
            double x = ox + placement.getCol() * cell;
            double y = oy + placement.getRow() * cell;
            double w = placement.effectiveWidth() * cell;
            double h = placement.effectiveHeight() * cell;
            gc.fillRect(x, y, w, h);
        }
        gc.restore();
    }

    private Color colorFor(MapPlacement placement) {
        if (placement.getKind() == PlacementKind.TOKEN) return Color.web("#4a90d9");
        if (placement.getKind() == PlacementKind.DOOR) return Color.web("#8e5a2b");
        if (placement.getKind() == PlacementKind.WALL) return Color.web("#666666");
        return Color.web("#6e4d2b");
    }

    private void drawSelection(GraphicsContext gc, MapPlacement selected) {
        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        double x = ox + selected.getCol() * cell;
        double y = oy + selected.getRow() * cell;
        double w = selected.effectiveWidth() * cell;
        double h = selected.effectiveHeight() * cell;

        gc.setStroke(Color.web("#ffd54a"));
        gc.setLineWidth(2.5 / state.getZoom());
        gc.strokeRect(x - 1, y - 1, w + 2, h + 2);
    }

    private void drawHoverCell(GraphicsContext gc) {
        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        gc.setStroke(Color.web("#76b5ff"));
        gc.setLineWidth(2 / state.getZoom());
        gc.strokeRect(ox + hoverCol * cell + 1, oy + hoverRow * cell + 1, cell - 2, cell - 2);
    }

    private void drawPlacementTooltip(GraphicsContext gc, MapPlacement placement) {
        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        double worldX = ox + placement.getCol() * cell + placement.effectiveWidth() * cell + 10;
        double worldY = oy + placement.getRow() * cell;

        double x = worldToScreenX(worldX);
        double y = worldToScreenY(worldY);

        String[] lines = {
                placement.getName() == null ? placement.getAssetId() : placement.getName(),
                placement.getKind().name(),
                "Layer: " + (placement.getLayerId() == null ? "-" : placement.getLayerId()),
                "Blocks sight: " + placement.isBlocksSight(),
                "Blocks move: " + placement.isBlocksMovement()
        };

        double boxW = 210;
        double boxH = lines.length * 18 + 12;

        if (x + boxW > getWidth()) x = Math.max(8, worldToScreenX(ox + placement.getCol() * cell) - boxW - 10);
        if (y + boxH > getHeight()) y = getHeight() - boxH - 8;

        gc.setFill(Color.web("#111827", 0.94));
        gc.fillRoundRect(x, y, boxW, boxH, 8, 8);
        gc.setStroke(Color.web("#64748b"));
        gc.setLineWidth(1);
        gc.strokeRoundRect(x, y, boxW, boxH, 8, 8);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 12));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setTextBaseline(VPos.TOP);
        for (int i = 0; i < lines.length; i++) {
            gc.fillText(lines[i], x + 8, y + 8 + i * 18);
        }
    }

    private void drawStatus(GraphicsContext gc) {
        String status = "Tool: " + (state.getActiveTool() == null ? "-" : state.getActiveTool().getDisplayName())
                + " | Zoom: " + String.format("%.2f", state.getZoom())
                + " | Offset: " + String.format("%.0f, %.0f", state.getViewOffsetX(), state.getViewOffsetY())
                + " | Snap: " + state.isSnapToGrid()
                + " | Fog preview: " + state.isFogPreviewEnabled();
        gc.setFill(Color.web("#000000", 0.55));
        gc.fillRoundRect(10, 10, 520, 28, 8, 8);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Arial", 13));
        gc.setTextBaseline(VPos.CENTER);
        gc.fillText(status, 18, 24);
    }

    public MapPlacement findPlacementAt(double screenX, double screenY) {
        int[] cell = screenToCell(screenX, screenY);
        if (cell == null) return null;

        int col = cell[0];
        int row = cell[1];

        MapProject project = state.getProject();
        if (project == null) return null;

        for (int layerIndex = project.getLayers().size() - 1; layerIndex >= 0; layerIndex--) {
            MapLayer layer = project.getLayers().get(layerIndex);
            if (!layer.isVisible()) continue;
            for (int i = project.getPlacements().size() - 1; i >= 0; i--) {
                MapPlacement placement = project.getPlacements().get(i);
                if (!layer.getId().equals(placement.getLayerId())) continue;
                if (col >= placement.getCol() && col < placement.getCol() + placement.effectiveWidth()
                        && row >= placement.getRow() && row < placement.getRow() + placement.effectiveHeight()) {
                    return placement;
                }
            }
        }
        return null;
    }

    public int[] screenToCell(double screenX, double screenY) {
        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        double worldX = (screenX - state.getViewOffsetX()) / state.getZoom();
        double worldY = (screenY - state.getViewOffsetY()) / state.getZoom();

        int col = (int) Math.floor((worldX - ox) / cell);
        int row = (int) Math.floor((worldY - oy) / cell);

        if (col < 0 || row < 0 || col >= grid.getCols() || row >= grid.getRows()) {
            return null;
        }
        return new int[] { col, row };
    }

    private void updateHover(double x, double y) {
        int[] cell = screenToCell(x, y);
        if (cell == null) {
            hoverCol = null;
            hoverRow = null;
            hoverPlacement = null;
            requestRender();
            return;
        }

        hoverCol = cell[0];
        hoverRow = cell[1];
        MapPlacement hit = findPlacementAt(x, y);

        boolean changed = hit != hoverPlacement;
        hoverPlacement = hit;
        if (changed) {
            requestRender();
        }
    }

    private Image loadImage(String url) {
        if (url == null || url.isBlank()) return null;
        String resolved = url.startsWith("jar:") ? url : encodeUrl(url);
        Image image = new Image(resolved, true);
        image.progressProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue.doubleValue() >= 1.0) {
                Platform.runLater(this::requestRender);
            }
        });
        image.errorProperty().addListener((obs, oldValue, isError) -> {
            if (Boolean.TRUE.equals(isError)) {
                Platform.runLater(this::requestRender);
            }
        });
        return image;
    }

    private double worldToScreenX(double worldX) {
        return worldX * state.getZoom() + state.getViewOffsetX();
    }

    private double worldToScreenY(double worldY) {
        return worldY * state.getZoom() + state.getViewOffsetY();
    }

    private static String encodeUrl(String url) {
        if (url == null) return null;
        try {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd < 0) return url;
            int pathStart = url.indexOf('/', schemeEnd + 3);
            if (pathStart < 0) return url;

            String base = url.substring(0, pathStart);
            String path = url.substring(pathStart);

            String[] segments = path.split("/", -1);
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) sb.append('/');
                sb.append(encodePathSegment(segments[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return url;
        }
    }

    private static String encodePathSegment(String segment) {
        if (segment == null || segment.isEmpty()) return segment == null ? "" : segment;
        try {
            return new URI(null, null, "/" + segment, null).toASCIIString().substring(1);
        } catch (Exception e) {
            byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int v = b & 0xFF;
                if ((v >= 'A' && v <= 'Z') || (v >= 'a' && v <= 'z') ||
                        (v >= '0' && v <= '9') || v == '-' || v == '_' || v == '.' || v == '~' || v == '+') {
                    sb.append((char) v);
                } else {
                    sb.append(String.format("%%%02X", v));
                }
            }
            return sb.toString();
        }
    }
}
