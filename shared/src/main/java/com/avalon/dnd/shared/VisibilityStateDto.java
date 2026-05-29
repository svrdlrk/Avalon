package com.avalon.dnd.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Server-computed fog/visibility snapshot for the current session.
 */
public class VisibilityStateDto {

    private boolean[][] visibleCells;
    private List<String> exploredCells = new ArrayList<>();
    private Map<String, TokenVisibilitySnapshotDto> tokenSnapshots = new LinkedHashMap<>();
    private Map<String, MapObjectDto> objectSnapshots = new LinkedHashMap<>();

    public VisibilityStateDto() {}

    public VisibilityStateDto(boolean[][] visibleCells,
                              List<String> exploredCells,
                              Map<String, TokenVisibilitySnapshotDto> tokenSnapshots,
                              Map<String, MapObjectDto> objectSnapshots) {
        this.visibleCells = visibleCells;
        setExploredCells(exploredCells);
        setTokenSnapshots(tokenSnapshots);
        setObjectSnapshots(objectSnapshots);
    }

    public boolean[][] getVisibleCells() { return visibleCells; }
    public void setVisibleCells(boolean[][] visibleCells) { this.visibleCells = visibleCells; }

    public List<String> getExploredCells() { return exploredCells; }
    public void setExploredCells(List<String> exploredCells) {
        this.exploredCells.clear();
        if (exploredCells != null) this.exploredCells.addAll(exploredCells);
    }

    public Map<String, TokenVisibilitySnapshotDto> getTokenSnapshots() { return tokenSnapshots; }
    public void setTokenSnapshots(Map<String, TokenVisibilitySnapshotDto> tokenSnapshots) {
        this.tokenSnapshots.clear();
        if (tokenSnapshots != null) this.tokenSnapshots.putAll(tokenSnapshots);
    }

    public Map<String, MapObjectDto> getObjectSnapshots() { return objectSnapshots; }
    public void setObjectSnapshots(Map<String, MapObjectDto> objectSnapshots) {
        this.objectSnapshots.clear();
        if (objectSnapshots != null) this.objectSnapshots.putAll(objectSnapshots);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VisibilityStateDto that)) return false;
        return Arrays.deepEquals(visibleCells, that.visibleCells)
                && Objects.equals(exploredCells, that.exploredCells)
                && Objects.equals(tokenSnapshots, that.tokenSnapshots)
                && Objects.equals(objectSnapshots, that.objectSnapshots);
    }

    @Override
    public int hashCode() {
        int result = Arrays.deepHashCode(visibleCells);
        result = 31 * result + Objects.hashCode(exploredCells);
        result = 31 * result + Objects.hashCode(tokenSnapshots);
        result = 31 * result + Objects.hashCode(objectSnapshots);
        return result;
    }
}
