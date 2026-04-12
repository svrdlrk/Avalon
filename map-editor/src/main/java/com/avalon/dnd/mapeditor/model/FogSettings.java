package com.avalon.dnd.mapeditor.model;

public class FogSettings {

    private boolean enabled = true;
    private boolean revealFromTokens = true;
    private boolean revealFromSelectedPlacement = true;
    private int revealRadius = 6;
    private double opacity = 0.72;

    public FogSettings() {}

    public FogSettings copy() {
        FogSettings copy = new FogSettings();
        copy.enabled = this.enabled;
        copy.revealFromTokens = this.revealFromTokens;
        copy.revealFromSelectedPlacement = this.revealFromSelectedPlacement;
        copy.revealRadius = this.revealRadius;
        copy.opacity = this.opacity;
        return copy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isRevealFromTokens() {
        return revealFromTokens;
    }

    public boolean isRevealFromSelectedPlacement() {
        return revealFromSelectedPlacement;
    }

    public int getRevealRadius() {
        return revealRadius;
    }

    public double getOpacity() {
        return opacity;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setRevealFromTokens(boolean revealFromTokens) {
        this.revealFromTokens = revealFromTokens;
    }

    public void setRevealFromSelectedPlacement(boolean revealFromSelectedPlacement) {
        this.revealFromSelectedPlacement = revealFromSelectedPlacement;
    }

    public void setRevealRadius(int revealRadius) {
        this.revealRadius = Math.max(0, Math.min(64, revealRadius));
    }

    public void setOpacity(double opacity) {
        this.opacity = Math.max(0.0, Math.min(1.0, opacity));
    }
}
