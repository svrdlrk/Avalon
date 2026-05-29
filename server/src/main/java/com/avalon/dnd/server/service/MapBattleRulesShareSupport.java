package com.avalon.dnd.server.service;

import com.avalon.dnd.shared.VisibilityStateDto;

import java.util.List;
import java.util.Map;

/**
 * Share suggestion predicates extracted from the battle rules service.
 */
final class MapBattleRulesShareSupport {

    private MapBattleRulesShareSupport() {
    }

    static boolean shouldSuggestShare(VisibilityStateDto sharedState,
                                      VisibilityStateDto privateState,
                                      List<String> playerIds,
                                      int maxGroupSize) {
        if (playerIds == null || playerIds.size() < 2) {
            return false;
        }
        if (playerIds.size() > Math.max(0, maxGroupSize)) {
            return false;
        }
        if (privateState == null) {
            return false;
        }
        if (sharedState == null) {
            return true;
        }
        return !isStateCoveredBy(sharedState, privateState);
    }

    static boolean isStateCoveredBy(VisibilityStateDto base, VisibilityStateDto candidate) {
        if (candidate == null) return true;
        if (base == null) return false;
        if (!containsAllCells(base.getExploredCells(), candidate.getExploredCells())) return false;
        if (!baseContainsSnapshots(base.getTokenSnapshots(), candidate.getTokenSnapshots())) return false;
        if (!baseContainsSnapshots(base.getObjectSnapshots(), candidate.getObjectSnapshots())) return false;
        return true;
    }

    static boolean containsAllCells(List<String> base, List<String> candidate) {
        if (candidate == null || candidate.isEmpty()) return true;
        if (base == null || base.isEmpty()) return false;
        return base.containsAll(candidate);
    }

    static <T> boolean baseContainsSnapshots(Map<String, T> base, Map<String, T> candidate) {
        if (candidate == null || candidate.isEmpty()) return true;
        if (base == null || base.isEmpty()) return false;
        return base.keySet().containsAll(candidate.keySet());
    }
}
