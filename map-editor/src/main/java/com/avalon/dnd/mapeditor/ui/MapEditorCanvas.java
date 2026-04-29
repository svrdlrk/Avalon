package com.avalon.dnd.mapeditor.ui;

import com.avalon.dnd.mapeditor.model.BackgroundLayer;
import com.avalon.dnd.mapeditor.model.BackgroundMode;
import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.FogSettings;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.shared.MicroLocationDto;
import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.model.PlacementKind;
import com.avalon.dnd.mapeditor.model.TerrainCell;
import com.avalon.dnd.mapeditor.model.TerrainLayer;
import com.avalon.dnd.mapeditor.model.WallLayer;
import com.avalon.dnd.mapeditor.model.WallPath;
import com.avalon.dnd.mapeditor.model.WallPoint;
import com.avalon.dnd.mapeditor.service.FogCalculator;
import javafx.application.Platform;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MapEditorCanvas extends Canvas {

    private final EditorState state;
    private final Map<String, Image> imageCache = new HashMap<>();

    private MapPlacement hoverPlacement;
    private Integer hoverCol;
    private Integer hoverRow;

    private boolean resizePending;

    public enum MicroLocationHandle {
        NONE, MOVE, NW, N, NE, E, SE, S, SW, W
    }

    public static final class MicroLocationHit {
        public final MicroLocationDto zone;
        public final MicroLocationHandle handle;

        public MicroLocationHit(MicroLocationDto zone, MicroLocationHandle handle) {
            this.zone = zone;
            this.handle = handle;
        }
    }

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
            if (state.getActiveTool() != null) {
                state.getActiveTool().onScroll(e, this, state);
            }
            if (!e.isConsumed()) {
                double zoom = state.getZoom();
                if (e.getDeltaY() > 0) {
                    state.setZoom(zoom * 1.08);
                } else {
                    state.setZoom(zoom / 1.08);
                }
                requestRender();
            }
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
        drawTerrainLayer(gc);
        drawReferenceOverlay(gc);
        drawGrid(gc);
        drawMicroLocations(gc);
        drawWallLayer(gc);
        drawPlacements(gc);
        drawFogPreview(gc);
        drawWallSnapIndicator(gc);

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

    private void drawReferenceOverlay(GraphicsContext gc) {
        MapProject project = state.getProject();
        if (project == null || project.getReferenceOverlay() == null) {
            return;
        }

        var reference = project.getReferenceOverlay();
        if (!reference.isVisible()) {
            return;
        }

        String url = reference.getImageUrl();
        if (url == null || url.isBlank()) {
            return;
        }

        String cacheKey = "ref:" + url;
        Image image = imageCache.get(cacheKey);
        if (image == null) {
            image = loadImage(url);
            if (image != null) {
                imageCache.put(cacheKey, image);
            } else {
                return;
            }
        }

        if (image.isError()) {
            return;
        }

        gc.save();
        gc.setGlobalAlpha(reference.getOpacity());
        double x = reference.getOffsetX();
        double y = reference.getOffsetY();
        double scale = reference.getScale();
        double drawW = image.getWidth() * scale;
        double drawH = image.getHeight() * scale;
        gc.translate(x + drawW / 2.0, y + drawH / 2.0);
        gc.rotate(reference.getRotation());
        gc.drawImage(image, -drawW / 2.0, -drawH / 2.0, drawW, drawH);
        gc.restore();
    }

    private void drawTerrainLayer(GraphicsContext gc) {
        MapProject project = state.getProject();
        if (project == null) {
            return;
        }
        TerrainLayer terrain = project.getTerrainLayer();
        if (terrain == null || !terrain.isVisible()) {
            return;
        }

        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        gc.save();
        gc.setGlobalAlpha(terrain.getOpacity());
        for (TerrainCell terrainCell : terrain.getCells()) {
            if (terrainCell == null) {
                continue;
            }
            double x = ox + terrainCell.getCol() * cell;
            double y = oy + terrainCell.getRow() * cell;
            double w = terrainCell.getWidth() * cell;
            double h = terrainCell.getHeight() * cell;
            gc.setFill(colorForTerrain(terrainCell.getTerrainType()));
            gc.fillRect(x, y, w, h);
        }
        gc.restore();
    }

    private void drawWallLayer(GraphicsContext gc) {
        MapProject project = state.getProject();
        if (project == null) {
            return;
        }
        WallLayer wallLayer = project.getWallLayer();
        if (wallLayer == null || !wallLayer.isVisible()) {
            return;
        }

        WallPath selectedWall = state.selectedWallPath();

        gc.save();
        gc.setGlobalAlpha(wallLayer.getOpacity());
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        for (WallPath path : wallLayer.getPaths()) {
            if (path == null || path.getPoints().isEmpty()) {
                continue;
            }
            gc.setLineWidth(path.getThickness() / state.getZoom());
            gc.setStroke(path.isBlocksSight() ? Color.web("#2f3b52") : Color.web("#7f8c8d"));
            if (path.getPoints().size() == 1) {
                WallPoint point = path.getPoints().get(0);
                double radius = Math.max(2.5, path.getThickness()) / state.getZoom();
                gc.setFill(path.isBlocksSight() ? Color.web("#2f3b52") : Color.web("#7f8c8d"));
                gc.fillOval(point.getX() - radius, point.getY() - radius, radius * 2.0, radius * 2.0);
            } else {
                WallPoint prev = null;
                for (WallPoint point : path.getPoints()) {
                    if (prev != null) {
                        gc.strokeLine(prev.getX(), prev.getY(), point.getX(), point.getY());
                    }
                    prev = point;
                }
            }
        }

        if (selectedWall != null && selectedWall.isVisible()) {
            drawSelectedWall(gc, selectedWall);
            WallMergeCandidate mergeCandidate = findMergeCandidate(selectedWall, wallLayer);
            if (mergeCandidate != null) {
                WallPath candidatePath = wallLayer.findPathById(mergeCandidate.otherPathId());
                if (candidatePath != null && candidatePath.isVisible()) {
                    drawMergeCandidateWall(gc, candidatePath);
                }
            }
        }
        gc.restore();
    }

    private void drawSelectedWall(GraphicsContext gc, WallPath path) {
        if (path == null || path.getPoints().isEmpty()) {
            return;
        }

        gc.save();
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setStroke(Color.web("#ffd54a"));
        gc.setLineWidth(Math.max(2.0 / state.getZoom(), path.getThickness() / state.getZoom() + 1.5));
        if (path.getPoints().size() == 1) {
            WallPoint point = path.getPoints().get(0);
            double radius = Math.max(3.5, path.getThickness()) / state.getZoom();
            gc.setFill(Color.web("#ffd54a"));
            gc.fillOval(point.getX() - radius, point.getY() - radius, radius * 2.0, radius * 2.0);
        } else {
            WallPoint prev = null;
            for (WallPoint point : path.getPoints()) {
                if (prev != null) {
                    gc.strokeLine(prev.getX(), prev.getY(), point.getX(), point.getY());
                }
                prev = point;
            }
        }

        int selectedIndex = state.getSelectedWallVertexIndex();
        for (int i = 0; i < path.getPoints().size(); i++) {
            WallPoint point = path.getPoints().get(i);
            double radius = (i == selectedIndex ? 6.0 : 4.0) / state.getZoom();
            gc.setFill(i == selectedIndex ? Color.web("#ff8c00") : Color.web("#ffd54a"));
            gc.fillOval(point.getX() - radius, point.getY() - radius, radius * 2.0, radius * 2.0);
            gc.setStroke(Color.web("#111111"));
            gc.setLineWidth(1.2 / state.getZoom());
            gc.strokeOval(point.getX() - radius, point.getY() - radius, radius * 2.0, radius * 2.0);
        }
        gc.restore();
    }

    private void drawMergeCandidateWall(GraphicsContext gc, WallPath path) {
        if (path == null || path.getPoints().isEmpty()) {
            return;
        }

        gc.save();
        gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        gc.setLineDashes(10.0, 8.0);
        gc.setStroke(Color.web("#ff9f1c"));
        gc.setLineWidth(Math.max(2.0 / state.getZoom(), path.getThickness() / state.getZoom() + 1.0));
        if (path.getPoints().size() == 1) {
            WallPoint point = path.getPoints().get(0);
            double radius = Math.max(3.0, path.getThickness()) / state.getZoom();
            gc.setFill(Color.web("#ff9f1c"));
            gc.fillOval(point.getX() - radius, point.getY() - radius, radius * 2.0, radius * 2.0);
        } else {
            WallPoint prev = null;
            for (WallPoint point : path.getPoints()) {
                if (prev != null) {
                    gc.strokeLine(prev.getX(), prev.getY(), point.getX(), point.getY());
                }
                prev = point;
            }
        }
        gc.restore();
    }

    private WallMergeCandidate findMergeCandidate(WallPath selected, WallLayer layer) {
        if (selected == null || layer == null || selected.getPoints().isEmpty()) {
            return null;
        }
        double tolerance = 0.01;
        WallPoint start = selected.getFirstPoint();
        WallPoint end = selected.getLastPoint();
        if (start == null || end == null) {
            return null;
        }
        for (WallPath other : layer.getPaths()) {
            if (other == null || other == selected || other.getPoints().isEmpty()) {
                continue;
            }
            if (pointsNear(end, other.getFirstPoint(), tolerance)) {
                return new WallMergeCandidate(other.getId(), true, false);
            }
            if (pointsNear(end, other.getLastPoint(), tolerance)) {
                return new WallMergeCandidate(other.getId(), true, true);
            }
            if (pointsNear(start, other.getLastPoint(), tolerance)) {
                return new WallMergeCandidate(other.getId(), false, false);
            }
            if (pointsNear(start, other.getFirstPoint(), tolerance)) {
                return new WallMergeCandidate(other.getId(), false, true);
            }
        }
        return null;
    }

    private boolean pointsNear(WallPoint a, WallPoint b, double tolerance) {
        if (a == null || b == null) {
            return false;
        }
        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        return dx * dx + dy * dy <= tolerance * tolerance;
    }

    private record WallMergeCandidate(String otherPathId, boolean appendToEnd, boolean reverseOther) {}

    private void drawWallSnapIndicator(GraphicsContext gc) {
        if (!state.hasWallSnapIndicator()) {
            return;
        }
        Double x = state.getWallSnapX();
        Double y = state.getWallSnapY();
        if (x == null || y == null) {
            return;
        }
        gc.save();
        gc.setStroke(Color.web("#00e5ff"));
        gc.setFill(Color.web("#00e5ff", 0.25));
        double radius = 7.0 / state.getZoom();
        gc.setLineWidth(2.0 / state.getZoom());
        gc.strokeOval(x - radius, y - radius, radius * 2.0, radius * 2.0);
        gc.fillOval(x - radius * 0.55, y - radius * 0.55, radius * 1.1, radius * 1.1);
        gc.strokeLine(x - radius * 1.5, y, x + radius * 1.5, y);
        gc.strokeLine(x, y - radius * 1.5, x, y + radius * 1.5);
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

    private void drawMicroLocations(GraphicsContext gc) {
        MapProject project = state.getProject();
        if (project == null || project.getMicroLocations().isEmpty()) return;

        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        gc.save();
        gc.setLineDashes(10.0, 8.0);
        for (MicroLocationDto zone : project.getMicroLocations()) {
            if (zone == null) continue;
            double x = ox + zone.getCol() * cell;
            double y = oy + zone.getRow() * cell;
            double w = Math.max(1, zone.getWidth()) * cell;
            double h = Math.max(1, zone.getHeight()) * cell;
            boolean selected = zone.getId() != null && zone.getId().equals(selectedMicroLocationId());
            gc.setStroke(selected ? Color.web("#f97316") : zone.isLocked() ? Color.web("#94a3b8") : Color.web("#38bdf8"));
            gc.setFill(selected ? Color.web("#f97316", 0.12) : Color.web("#38bdf8", 0.08));
            gc.setLineWidth((selected ? 2.4 : 1.5) / state.getZoom());
            gc.fillRect(x, y, w, h);
            gc.strokeRect(x, y, w, h);

            gc.setFill(Color.web("#ffffff"));
            gc.setFont(Font.font(11 / state.getZoom()));
            gc.fillText(displayZoneLabel(zone), x + 4, y + 14 / state.getZoom());

            if (selected) {
                drawMicroLocationHandles(gc, x, y, w, h);
            }
        }
        gc.restore();
    }

    private String selectedMicroLocationId() {
        return state.getSelectedMicroLocationId();
    }

    private String displayZoneLabel(MicroLocationDto zone) {
        if (zone == null) return "";
        String name = zone.getName() == null || zone.getName().isBlank() ? zone.getId() : zone.getName();
        return name == null ? "zone" : name;
    }

    private void drawMicroLocationHandles(GraphicsContext gc, double x, double y, double w, double h) {
        double handleSize = 7.0 / state.getZoom();
        double half = handleSize / 2.0;
        gc.save();
        gc.setFill(Color.web("#ffffff"));
        gc.setStroke(Color.web("#111827"));
        gc.setLineWidth(1.0 / state.getZoom());
        double[][] points = {
                {x, y},
                {x + w / 2.0, y},
                {x + w, y},
                {x + w, y + h / 2.0},
                {x + w, y + h},
                {x + w / 2.0, y + h},
                {x, y + h},
                {x, y + h / 2.0}
        };
        for (double[] point : points) {
            gc.fillRect(point[0] - half, point[1] - half, handleSize, handleSize);
            gc.strokeRect(point[0] - half, point[1] - half, handleSize, handleSize);
        }
        gc.restore();
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
                double rotation = placement.getRotation();

                Image image = imageCache.computeIfAbsent("obj:" + placement.getImageUrl(), key -> loadImage(placement.getImageUrl()));

                gc.save();
                gc.translate(x + w / 2.0, y + h / 2.0);
                gc.rotate(rotation);
                gc.beginPath();
                gc.rect(-w / 2.0, -h / 2.0, w, h);
                gc.clip();

                if (image != null && !image.isError()) {
                    gc.drawImage(image, -w / 2.0, -h / 2.0, w, h);
                } else {
                    gc.setFill(colorFor(placement));
                    gc.fillRoundRect(-w / 2.0 + 1, -h / 2.0 + 1, Math.max(1, w - 2), Math.max(1, h - 2), 4, 4);
                }

                if (placement.isLocked()) {
                    gc.setStroke(Color.web("#9ca3af"));
                    gc.setLineWidth(1.5 / state.getZoom());
                    gc.strokeRect(-w / 2.0 + 2, -h / 2.0 + 2, Math.max(1, w - 4), Math.max(1, h - 4));
                }

                if (placement.isSelected()) {
                    gc.setStroke(Color.web("#f1c40f"));
                    gc.setLineWidth(2 / state.getZoom());
                    gc.strokeRect(-w / 2.0 + 1, -h / 2.0 + 1, Math.max(1, w - 2), Math.max(1, h - 2));
                }

                gc.restore();
            }

            gc.restore();
        }
    }

    private void drawFogPreview(GraphicsContext gc) {
        if (!state.isFogPreviewEnabled() || state.getProject() == null) {
            return;
        }

        FogSettings settings = state.getProject().getFogSettings();
        if (settings == null || !settings.isEnabled()) {
            return;
        }

        boolean[][] visible = FogCalculator.computeVisibleCells(state.getProject());
        if (visible.length == 0 || visible[0].length == 0) {
            return;
        }

        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();

        gc.save();
        gc.setFill(Color.color(0.0, 0.0, 0.0, settings.getOpacity()));
        for (int row = 0; row < visible.length; row++) {
            for (int col = 0; col < visible[row].length; col++) {
                if (visible[row][col]) {
                    continue;
                }
                gc.fillRect(ox + col * cell, oy + row * cell, cell, cell);
            }
        }
        gc.restore();
    }

    private Color colorFor(MapPlacement placement) {
        if (placement.getKind() == PlacementKind.TOKEN) return Color.web("#4a90d9");
        if (placement.getKind() == PlacementKind.DOOR) return Color.web("#8e5a2b");
        if (placement.getKind() == PlacementKind.WALL) return Color.web("#666666");
        return Color.web("#6e4d2b");
    }

    private Color colorForTerrain(String terrainType) {
        if (terrainType == null) {
            return Color.web("#4d7c3a");
        }
        return switch (terrainType.toLowerCase()) {
            case "stone", "rocks" -> Color.web("#8a8f98");
            case "dirt", "mud" -> Color.web("#7b5e3b");
            case "sand" -> Color.web("#cbb27b");
            case "water" -> Color.web("#397bbf");
            default -> Color.web("#4d7c3a");
        };
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

    public MicroLocationHit findMicroLocationHitAt(double screenX, double screenY) {
        MapProject project = state.getProject();
        if (project == null) return null;
        MicroLocationDto hitZone = null;
        for (int i = project.getMicroLocations().size() - 1; i >= 0; i--) {
            MicroLocationDto zone = project.getMicroLocations().get(i);
            if (zone == null) continue;
            if (containsMicroLocation(zone, screenX, screenY)) {
                hitZone = zone;
                break;
            }
        }
        if (hitZone == null) return null;
        return new MicroLocationHit(hitZone, findMicroLocationHandleAt(hitZone, screenX, screenY));
    }

    public MicroLocationDto findMicroLocationAt(double screenX, double screenY) {
        MicroLocationHit hit = findMicroLocationHitAt(screenX, screenY);
        return hit == null ? null : hit.zone;
    }

    public MicroLocationHandle findMicroLocationHandleAt(MicroLocationDto zone, double screenX, double screenY) {
        if (zone == null) return MicroLocationHandle.NONE;
        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        double x = ox + zone.getCol() * cell;
        double y = oy + zone.getRow() * cell;
        double w = Math.max(1, zone.getWidth()) * cell;
        double h = Math.max(1, zone.getHeight()) * cell;
        double threshold = 10.0 / state.getZoom();
        double worldX = screenToWorldX(screenX);
        double worldY = screenToWorldY(screenY);

        boolean left = Math.abs(worldX - x) <= threshold;
        boolean right = Math.abs(worldX - (x + w)) <= threshold;
        boolean top = Math.abs(worldY - y) <= threshold;
        boolean bottom = Math.abs(worldY - (y + h)) <= threshold;

        if (left && top) return MicroLocationHandle.NW;
        if (right && top) return MicroLocationHandle.NE;
        if (right && bottom) return MicroLocationHandle.SE;
        if (left && bottom) return MicroLocationHandle.SW;
        if (top && worldX >= x && worldX <= x + w) return MicroLocationHandle.N;
        if (bottom && worldX >= x && worldX <= x + w) return MicroLocationHandle.S;
        if (left && worldY >= y && worldY <= y + h) return MicroLocationHandle.W;
        if (right && worldY >= y && worldY <= y + h) return MicroLocationHandle.E;
        return MicroLocationHandle.MOVE;
    }

    private boolean containsMicroLocation(MicroLocationDto zone, double screenX, double screenY) {
        var grid = state.grid();
        int cell = grid.getCellSize();
        int ox = grid.getOffsetX();
        int oy = grid.getOffsetY();
        double worldX = screenToWorldX(screenX);
        double worldY = screenToWorldY(screenY);
        double x = ox + zone.getCol() * cell;
        double y = oy + zone.getRow() * cell;
        double w = Math.max(1, zone.getWidth()) * cell;
        double h = Math.max(1, zone.getHeight()) * cell;
        return worldX >= x && worldX <= x + w && worldY >= y && worldY <= y + h;
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

    public int getGridCellSize() {
        return state.grid().getCellSize();
    }

    public int getGridOffsetX() {
        return state.grid().getOffsetX();
    }

    public int getGridOffsetY() {
        return state.grid().getOffsetY();
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

    public double screenToWorldX(double screenX) {
        return (screenX - state.getViewOffsetX()) / state.getZoom();
    }

    public double screenToWorldY(double screenY) {
        return (screenY - state.getViewOffsetY()) / state.getZoom();
    }

    public WallPath findWallPathAt(double screenX, double screenY) {
        MapProject project = state.getProject();
        if (project == null || project.getWallLayer() == null || !project.getWallLayer().isVisible()) {
            return null;
        }

        double worldX = screenToWorldX(screenX);
        double worldY = screenToWorldY(screenY);
        double maxDistance = 8.0 / state.getZoom();

        for (int i = project.getWallLayer().getPaths().size() - 1; i >= 0; i--) {
            WallPath path = project.getWallLayer().getPaths().get(i);
            if (path == null || !path.isVisible() || path.getPoints().isEmpty()) {
                continue;
            }

            if (path.findNearestVertexIndex(worldX, worldY, maxDistance) >= 0) {
                return path;
            }
            if (path.getPoints().size() >= 2 && path.findNearestSegmentInsertIndex(worldX, worldY, maxDistance) >= 0) {
                return path;
            }
        }
        return null;
    }

    public int findWallVertexAt(WallPath path, double screenX, double screenY) {
        if (path == null) {
            return -1;
        }
        double worldX = screenToWorldX(screenX);
        double worldY = screenToWorldY(screenY);
        return path.findNearestVertexIndex(worldX, worldY, 8.0 / state.getZoom());
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
        String resolved = resolveImageSource(url.trim());
        if (resolved == null || resolved.isBlank()) return null;
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

    private String resolveImageSource(String url) {
        if (url == null || url.isBlank()) return null;
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("jar:") || url.startsWith("file:") || url.startsWith("data:")) {
            return encodeUrl(url);
        }

        String cleaned = url.replace('\\', '/');
        if (cleaned.startsWith("/uploads/") || cleaned.startsWith("uploads/")) {
            Path local = resolveProjectPath(cleaned.startsWith("/") ? cleaned.substring(1) : cleaned);
            if (local != null) {
                return local.toUri().toString();
            }
            return cleaned.startsWith("/") ? cleaned : "/" + cleaned;
        }

        Path local = resolveProjectPath(cleaned.startsWith("/") ? cleaned.substring(1) : cleaned);
        if (local != null) {
            return local.toUri().toString();
        }

        return encodeUrl(cleaned);
    }

    private Path resolveProjectPath(String relative) {
        if (relative == null || relative.isBlank()) return null;
        String cleaned = relative.startsWith("/") ? relative.substring(1) : relative;

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path current = cwd;
        for (int i = 0; i < 6 && current != null; i++, current = current.getParent()) {
            Path candidate = current.resolve(cleaned).normalize();
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        Path direct = Path.of(cleaned);
        if (Files.exists(direct)) {
            return direct.toAbsolutePath().normalize();
        }
        return null;
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
