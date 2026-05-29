package com.avalon.dnd.server.service;

import com.avalon.dnd.shared.MapObjectDto;
import com.avalon.dnd.shared.TokenVisibilitySnapshotDto;
import com.avalon.dnd.shared.VisibilityStateDto;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

final class MapBattleRulesVisibilitySupport {

    private MapBattleRulesVisibilitySupport() {
    }

    static VisibilityStateDto mergeVisibilityStates(Collection<VisibilityStateDto> states) {
        if (states == null || states.isEmpty()) {
            return null;
        }
        VisibilityStateDto merged = null;
        for (VisibilityStateDto state : states) {
            merged = mergeVisibilityStates(merged, state);
        }
        return merged;
    }

    static VisibilityStateDto mergeVisibilityStates(VisibilityStateDto left, VisibilityStateDto right) {
        if (left == null) {
            return right == null ? null : copyVisibilityState(right);
        }
        if (right == null) {
            return copyVisibilityState(left);
        }

        boolean[][] visible = MapBattleRulesGeometrySupport.mergeVisibleCells(left.getVisibleCells(), right.getVisibleCells());
        LinkedHashSet<String> explored = new LinkedHashSet<>();
        if (left.getExploredCells() != null) explored.addAll(left.getExploredCells());
        if (right.getExploredCells() != null) explored.addAll(right.getExploredCells());
        LinkedHashMap<String, TokenVisibilitySnapshotDto> tokenSnapshots = new LinkedHashMap<>();
        if (left.getTokenSnapshots() != null) tokenSnapshots.putAll(left.getTokenSnapshots());
        if (right.getTokenSnapshots() != null) tokenSnapshots.putAll(right.getTokenSnapshots());
        LinkedHashMap<String, MapObjectDto> objectSnapshots = new LinkedHashMap<>();
        if (left.getObjectSnapshots() != null) objectSnapshots.putAll(left.getObjectSnapshots());
        if (right.getObjectSnapshots() != null) objectSnapshots.putAll(right.getObjectSnapshots());

        VisibilityStateDto merged = new VisibilityStateDto();
        merged.setVisibleCells(visible);
        merged.setExploredCells(new ArrayList<>(explored));
        merged.setTokenSnapshots(tokenSnapshots);
        merged.setObjectSnapshots(objectSnapshots);
        return merged;
    }

    static VisibilityStateDto copyVisibilityState(VisibilityStateDto source) {
        if (source == null) return null;
        VisibilityStateDto copy = new VisibilityStateDto();
        copy.setVisibleCells(MapBattleRulesGeometrySupport.copyVisibleCells(source.getVisibleCells()));
        copy.setExploredCells(source.getExploredCells() == null ? null : new ArrayList<>(source.getExploredCells()));
        copy.setTokenSnapshots(source.getTokenSnapshots() == null ? null : new LinkedHashMap<>(source.getTokenSnapshots()));
        copy.setObjectSnapshots(source.getObjectSnapshots() == null ? null : new LinkedHashMap<>(source.getObjectSnapshots()));
        return copy;
    }
}
