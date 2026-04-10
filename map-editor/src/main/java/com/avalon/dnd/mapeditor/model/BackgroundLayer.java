package com.avalon.dnd.mapeditor.model;

public class BackgroundLayer {

    private String imageUrl;
    private BackgroundMode mode = BackgroundMode.STRETCH;
    private boolean visible = true;
    private double opacity = 1.0;
    private double scale = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;

    public BackgroundLayer() {}

    public BackgroundLayer copy() {
        BackgroundLayer copy = new BackgroundLayer();
        copy.imageUrl = this.imageUrl;
        copy.mode = this.mode;
        copy.visible = this.visible;
        copy.opacity = this.opacity;
        copy.scale = this.scale;
        copy.offsetX = this.offsetX;
        copy.offsetY = this.offsetY;
        return copy;
    }

    public String getImageUrl() { return imageUrl; }
    public BackgroundMode getMode() { return mode; }
    public boolean isVisible() { return visible; }
    public double getOpacity() { return opacity; }
    public double getScale() { return scale; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }

    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setMode(BackgroundMode mode) { this.mode = mode == null ? BackgroundMode.STRETCH : mode; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public void setOpacity(double opacity) { this.opacity = Math.max(0.0, Math.min(1.0, opacity)); }
    public void setScale(double scale) { this.scale = Math.max(0.1, Math.min(10.0, scale)); }
    public void setOffsetX(double offsetX) { this.offsetX = offsetX; }
    public void setOffsetY(double offsetY) { this.offsetY = offsetY; }
}
