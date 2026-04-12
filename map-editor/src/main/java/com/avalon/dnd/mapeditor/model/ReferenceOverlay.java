package com.avalon.dnd.mapeditor.model;

public class ReferenceOverlay {

    private String imageUrl;
    private boolean visible = true;
    private boolean locked = false;
    private double opacity = 0.65;
    private double scale = 1.0;
    private double rotation = 0.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;

    public ReferenceOverlay() {}

    public ReferenceOverlay copy() {
        ReferenceOverlay copy = new ReferenceOverlay();
        copy.imageUrl = this.imageUrl;
        copy.visible = this.visible;
        copy.locked = this.locked;
        copy.opacity = this.opacity;
        copy.scale = this.scale;
        copy.rotation = this.rotation;
        copy.offsetX = this.offsetX;
        copy.offsetY = this.offsetY;
        return copy;
    }

    public String getImageUrl() { return imageUrl; }
    public boolean isVisible() { return visible; }
    public boolean isLocked() { return locked; }
    public double getOpacity() { return opacity; }
    public double getScale() { return scale; }
    public double getRotation() { return rotation; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public void setOpacity(double opacity) { this.opacity = Math.max(0.0, Math.min(1.0, opacity)); }
    public void setScale(double scale) { this.scale = Math.max(0.1, Math.min(10.0, scale)); }
    public void setRotation(double rotation) { this.rotation = normalizeRotation(rotation); }
    public void setOffsetX(double offsetX) { this.offsetX = offsetX; }
    public void setOffsetY(double offsetY) { this.offsetY = offsetY; }

    private double normalizeRotation(double rotation) {
        double value = rotation % 360.0;
        if (value <= -180.0) {
            value += 360.0;
        } else if (value > 180.0) {
            value -= 360.0;
        }
        return value;
    }
}
