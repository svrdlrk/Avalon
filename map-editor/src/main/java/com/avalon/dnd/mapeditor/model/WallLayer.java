package com.avalon.dnd.mapeditor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WallLayer {

    private boolean visible = true;
    private boolean locked = false;
    private double opacity = 1.0;
    private double defaultThickness = 2.5;
    private boolean defaultBlocksMovement = true;
    private boolean defaultBlocksSight = true;
    private final List<WallPath> paths = new ArrayList<>();

    public WallLayer copy() {
        WallLayer copy = new WallLayer();
        copy.visible = this.visible;
        copy.locked = this.locked;
        copy.opacity = this.opacity;
        copy.defaultThickness = this.defaultThickness;
        copy.defaultBlocksMovement = this.defaultBlocksMovement;
        copy.defaultBlocksSight = this.defaultBlocksSight;
        for (WallPath path : this.paths) {
            if (path != null) {
                copy.paths.add(path.copy());
            }
        }
        return copy;
    }

    public boolean isVisible() { return visible; }
    public boolean isLocked() { return locked; }
    public double getOpacity() { return opacity; }
    public double getDefaultThickness() { return defaultThickness; }
    public boolean isDefaultBlocksMovement() { return defaultBlocksMovement; }
    public boolean isDefaultBlocksSight() { return defaultBlocksSight; }
    public List<WallPath> getPaths() { return Collections.unmodifiableList(paths); }

    public void setVisible(boolean visible) { this.visible = visible; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setOpacity(double opacity) { this.opacity = Math.max(0.0, Math.min(1.0, opacity)); }
    public void setDefaultThickness(double defaultThickness) { this.defaultThickness = Math.max(0.5, defaultThickness); }
    public void setDefaultBlocksMovement(boolean defaultBlocksMovement) { this.defaultBlocksMovement = defaultBlocksMovement; }
    public void setDefaultBlocksSight(boolean defaultBlocksSight) { this.defaultBlocksSight = defaultBlocksSight; }
    public void setPaths(List<WallPath> paths) {
        this.paths.clear();
        if (paths != null) {
            for (WallPath path : paths) {
                if (path != null) {
                    this.paths.add(path);
                }
            }
        }
    }

    public void addPath(WallPath path) {
        if (path != null) {
            paths.add(path);
        }
    }

    public WallPath findPathById(String id) {
        if (id == null) {
            return null;
        }
        for (WallPath path : paths) {
            if (id.equals(path.getId())) {
                return path;
            }
        }
        return null;
    }

    public boolean removePathById(String id) {
        return id != null && paths.removeIf(path -> id.equals(path.getId()));
    }

    public EndpointSnap findNearestEndpoint(double x, double y, double maxDistance, String excludePathId) {
        double maxDistanceSq = maxDistance * maxDistance;
        EndpointSnap best = null;
        double bestDistance = maxDistanceSq;
        for (WallPath path : paths) {
            if (path == null || path.getPoints().isEmpty()) {
                continue;
            }
            if (excludePathId != null && excludePathId.equals(path.getId())) {
                continue;
            }
            WallPoint first = path.getFirstPoint();
            if (first != null) {
                double dx = first.getX() - x;
                double dy = first.getY() - y;
                double distanceSq = dx * dx + dy * dy;
                if (distanceSq <= bestDistance) {
                    bestDistance = distanceSq;
                    best = new EndpointSnap(path.getId(), 0, first.getX(), first.getY());
                }
            }
            WallPoint last = path.getLastPoint();
            if (last != null) {
                double dx = last.getX() - x;
                double dy = last.getY() - y;
                double distanceSq = dx * dx + dy * dy;
                if (distanceSq <= bestDistance) {
                    bestDistance = distanceSq;
                    best = new EndpointSnap(path.getId(), path.getPoints().size() - 1, last.getX(), last.getY());
                }
            }
        }
        return best;
    }

    public record EndpointSnap(String pathId, int pointIndex, double x, double y) {}
}
