package com.avalon.dnd.mapeditor.model;

public class FogSettings {

    private boolean enabled = true;
    private boolean revealFromTokens = true;
    private boolean revealFromSelectedPlacement = true;
    private int revealRadius = 6;
    private double opacity = 0.72;
    private boolean retainExploredCells = true;
    private int sharedVisionDistance = 8;
    /**
     * Backward-compatible boolean flag. Prefer timeOfDay for new data.
     */
    private boolean nightMode;
    /**
     * Explicit time-of-day marker persisted in map state.
     * Expected values: "day" or "night".
     */
    private String timeOfDay = "day";

    public FogSettings() {}

    public FogSettings copy() {
        FogSettings copy = new FogSettings();
        copy.enabled = this.enabled;
        copy.revealFromTokens = this.revealFromTokens;
        copy.revealFromSelectedPlacement = this.revealFromSelectedPlacement;
        copy.revealRadius = this.revealRadius;
        copy.opacity = this.opacity;
        copy.retainExploredCells = this.retainExploredCells;
        copy.sharedVisionDistance = this.sharedVisionDistance;
        copy.nightMode = this.nightMode;
        copy.timeOfDay = this.timeOfDay;
        return copy;
    }

    public boolean isEnabled() { return enabled; }
    public boolean isRevealFromTokens() { return revealFromTokens; }
    public boolean isRevealFromSelectedPlacement() { return revealFromSelectedPlacement; }
    public int getRevealRadius() { return revealRadius; }
    public double getOpacity() { return opacity; }
    public boolean isRetainExploredCells() { return retainExploredCells; }
    public int getSharedVisionDistance() { return sharedVisionDistance; }
    public boolean isNightMode() { return nightMode || "night".equalsIgnoreCase(timeOfDay); }
    public String getTimeOfDay() { return timeOfDay; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setRevealFromTokens(boolean revealFromTokens) { this.revealFromTokens = revealFromTokens; }
    public void setRevealFromSelectedPlacement(boolean revealFromSelectedPlacement) { this.revealFromSelectedPlacement = revealFromSelectedPlacement; }
    public void setRevealRadius(int revealRadius) { this.revealRadius = Math.max(0, Math.min(64, revealRadius)); }
    public void setOpacity(double opacity) { this.opacity = Math.max(0.0, Math.min(1.0, opacity)); }
    public void setRetainExploredCells(boolean retainExploredCells) { this.retainExploredCells = retainExploredCells; }
    public void setSharedVisionDistance(int sharedVisionDistance) { this.sharedVisionDistance = Math.max(0, Math.min(64, sharedVisionDistance)); }
    public void setNightMode(boolean nightMode) {
        this.nightMode = nightMode;
        this.timeOfDay = nightMode ? "night" : "day";
    }

    public void setTimeOfDay(String timeOfDay) {
        if (timeOfDay == null) {
            this.timeOfDay = "day";
            this.nightMode = false;
            return;
        }
        String normalized = timeOfDay.trim().toLowerCase();
        if (normalized.isEmpty() || normalized.equals("day") || normalized.equals("light")) {
            this.timeOfDay = "day";
            this.nightMode = false;
            return;
        }
        if (normalized.equals("night") || normalized.equals("dark")) {
            this.timeOfDay = "night";
            this.nightMode = true;
            return;
        }
        this.timeOfDay = normalized;
        this.nightMode = normalized.contains("night") || normalized.contains("dark");
    }
}
