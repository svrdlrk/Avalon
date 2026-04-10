package com.avalon.dnd.mapeditor.model;

public class MapLayer {

    private String id;
    private String name;
    private LayerKind kind = LayerKind.OBJECTS;
    private boolean visible = true;
    private boolean locked = false;
    private double opacity = 1.0;

    public MapLayer() {}

    public MapLayer(String id, String name, LayerKind kind) {
        this.id = id;
        this.name = name;
        this.kind = kind == null ? LayerKind.OBJECTS : kind;
    }

    public MapLayer copy() {
        MapLayer copy = new MapLayer();
        copy.id = this.id;
        copy.name = this.name;
        copy.kind = this.kind;
        copy.visible = this.visible;
        copy.locked = this.locked;
        copy.opacity = this.opacity;
        return copy;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public LayerKind getKind() { return kind; }
    public boolean isVisible() { return visible; }
    public boolean isLocked() { return locked; }
    public double getOpacity() { return opacity; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setKind(LayerKind kind) { this.kind = kind == null ? LayerKind.OBJECTS : kind; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setOpacity(double opacity) { this.opacity = Math.max(0.0, Math.min(1.0, opacity)); }

    @Override
    public String toString() {
        return name + " [" + kind + "]";
    }
}
