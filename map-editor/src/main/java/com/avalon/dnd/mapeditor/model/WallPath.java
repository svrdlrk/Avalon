package com.avalon.dnd.mapeditor.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class WallPath {

    private String id = UUID.randomUUID().toString();
    private String name;
    private boolean visible = true;
    private boolean locked = false;
    private double opacity = 1.0;
    private double thickness = 2.5;
    private boolean blocksMovement = true;
    private boolean blocksSight = true;
    private final List<WallPoint> points = new ArrayList<>();

    public WallPath copy() {
        WallPath copy = new WallPath();
        copy.id = this.id;
        copy.name = this.name;
        copy.visible = this.visible;
        copy.locked = this.locked;
        copy.opacity = this.opacity;
        copy.thickness = this.thickness;
        copy.blocksMovement = this.blocksMovement;
        copy.blocksSight = this.blocksSight;
        for (WallPoint point : this.points) {
            if (point != null) {
                copy.points.add(point.copy());
            }
        }
        return copy;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isVisible() { return visible; }
    public boolean isLocked() { return locked; }
    public double getOpacity() { return opacity; }
    public double getThickness() { return thickness; }
    public boolean isBlocksMovement() { return blocksMovement; }
    public boolean isBlocksSight() { return blocksSight; }
    public List<WallPoint> getPoints() { return Collections.unmodifiableList(points); }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setOpacity(double opacity) { this.opacity = Math.max(0.0, Math.min(1.0, opacity)); }
    public void setThickness(double thickness) { this.thickness = Math.max(0.5, thickness); }
    public void setBlocksMovement(boolean blocksMovement) { this.blocksMovement = blocksMovement; }
    public void setBlocksSight(boolean blocksSight) { this.blocksSight = blocksSight; }
    public void setPoints(List<WallPoint> points) {
        this.points.clear();
        if (points != null) {
            for (WallPoint point : points) {
                if (point != null) {
                    this.points.add(point);
                }
            }
        }
    }

    public void addPoint(double x, double y) {
        points.add(new WallPoint(x, y));
    }

    public WallPoint getPoint(int index) {
        if (index < 0 || index >= points.size()) {
            return null;
        }
        return points.get(index);
    }

    public void movePoint(int index, double x, double y) {
        WallPoint point = getPoint(index);
        if (point != null) {
            point.setX(x);
            point.setY(y);
        }
    }

    public void insertPoint(int index, double x, double y) {
        int safeIndex = Math.max(0, Math.min(index, points.size()));
        points.add(safeIndex, new WallPoint(x, y));
    }

    public boolean removePoint(int index) {
        if (index < 0 || index >= points.size()) {
            return false;
        }
        points.remove(index);
        return true;
    }

    public boolean isEndpointIndex(int index) {
        return index <= 0 || index >= points.size() - 1;
    }

    public WallPoint getFirstPoint() {
        return points.isEmpty() ? null : points.get(0);
    }

    public WallPoint getLastPoint() {
        return points.isEmpty() ? null : points.get(points.size() - 1);
    }

    public boolean startsNear(double x, double y, double tolerance) {
        WallPoint first = getFirstPoint();
        return first != null && distanceSq(first.getX(), first.getY(), x, y) <= tolerance * tolerance;
    }

    public boolean endsNear(double x, double y, double tolerance) {
        WallPoint last = getLastPoint();
        return last != null && distanceSq(last.getX(), last.getY(), x, y) <= tolerance * tolerance;
    }

    public void reverse() {
        Collections.reverse(points);
    }

    public boolean mergeWith(WallPath other, boolean appendToEnd, boolean reverseOther) {
        if (other == null || other.points.isEmpty()) {
            return false;
        }

        List<WallPoint> incoming = new ArrayList<>();
        for (WallPoint point : other.points) {
            if (point != null) {
                incoming.add(point.copy());
            }
        }
        if (incoming.isEmpty()) {
            return false;
        }
        if (reverseOther) {
            Collections.reverse(incoming);
        }

        if (points.isEmpty()) {
            points.addAll(incoming);
            return true;
        }

        if (appendToEnd) {
            for (int i = 1; i < incoming.size(); i++) {
                points.add(incoming.get(i));
            }
        } else {
            List<WallPoint> merged = new ArrayList<>(incoming.size() + points.size());
            for (int i = 0; i < incoming.size() - 1; i++) {
                merged.add(incoming.get(i));
            }
            merged.addAll(points);
            points.clear();
            points.addAll(merged);
        }
        return true;
    }

    public WallPath splitAtVertex(int vertexIndex) {
        if (vertexIndex <= 0 || vertexIndex >= points.size() - 1) {
            return null;
        }

        WallPath tail = new WallPath();
        tail.name = this.name == null || this.name.isBlank() ? null : this.name + " (part 2)";
        tail.visible = this.visible;
        tail.locked = this.locked;
        tail.opacity = this.opacity;
        tail.thickness = this.thickness;
        tail.blocksMovement = this.blocksMovement;
        tail.blocksSight = this.blocksSight;

        for (int i = vertexIndex; i < points.size(); i++) {
            WallPoint point = points.get(i);
            if (point != null) {
                tail.points.add(point.copy());
            }
        }

        List<WallPoint> head = new ArrayList<>();
        for (int i = 0; i <= vertexIndex; i++) {
            WallPoint point = points.get(i);
            if (point != null) {
                head.add(point.copy());
            }
        }
        points.clear();
        points.addAll(head);
        return tail;
    }

    public int findNearestVertexIndex(double x, double y, double maxDistance) {
        double maxDistanceSq = maxDistance * maxDistance;
        int nearestIndex = -1;
        double nearest = maxDistanceSq;
        for (int i = 0; i < points.size(); i++) {
            WallPoint point = points.get(i);
            double dx = point.getX() - x;
            double dy = point.getY() - y;
            double distanceSq = dx * dx + dy * dy;
            if (distanceSq <= nearest) {
                nearest = distanceSq;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    public int findNearestSegmentInsertIndex(double x, double y, double maxDistance) {
        if (points.size() < 2) {
            return -1;
        }
        double maxDistanceSq = maxDistance * maxDistance;
        int insertIndex = -1;
        double bestDistance = maxDistanceSq;
        for (int i = 0; i < points.size() - 1; i++) {
            WallPoint a = points.get(i);
            WallPoint b = points.get(i + 1);
            double distanceSq = distanceToSegmentSquared(x, y, a.getX(), a.getY(), b.getX(), b.getY());
            if (distanceSq <= bestDistance) {
                bestDistance = distanceSq;
                insertIndex = i + 1;
            }
        }
        return insertIndex;
    }

    private double distanceToSegmentSquared(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            double ddx = px - x1;
            double ddy = py - y1;
            return ddx * ddx + ddy * ddy;
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = x1 + t * dx;
        double cy = y1 + t * dy;
        double ddx = px - cx;
        double ddy = py - cy;
        return ddx * ddx + ddy * ddy;
    }

    public void translate(double dx, double dy) {
        for (WallPoint point : points) {
            point.setX(point.getX() + dx);
            point.setY(point.getY() + dy);
        }
    }

    public boolean hasEnoughPoints() {
        return points.size() >= 2;
    }

    private static double distanceSq(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        return dx * dx + dy * dy;
    }
}
