package com.avalon.dnd.shared;

public final class PlacementSizingRules {

    public static final int MIN_GRID_SIZE = 1;
    public static final int MAX_TOKEN_GRID_SIZE = 4;
    public static final int MAX_OBJECT_GRID_SIZE = 4;

    private PlacementSizingRules() {
    }

    public static int clampTokenGridSize(int size) {
        return Math.max(MIN_GRID_SIZE, Math.min(MAX_TOKEN_GRID_SIZE, size));
    }

    public static int clampObjectGridSize(int size) {
        return Math.max(MIN_GRID_SIZE, Math.min(MAX_OBJECT_GRID_SIZE, size));
    }
}
