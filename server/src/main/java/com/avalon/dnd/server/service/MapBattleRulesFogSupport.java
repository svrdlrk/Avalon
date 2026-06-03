package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.JsonPayloads;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Shared fog/vision parsing helpers extracted from the battle rules service.
 */
final class MapBattleRulesFogSupport {

    private MapBattleRulesFogSupport() {
    }

    static FogSnapshot snapshot(JsonNode fogSettings, int sharedVisionFallback) {
        Map<String, Object> fogMap = JsonPayloads.toMap(fogSettings);
        if (fogMap.isEmpty()) {
            return new FogSnapshot(true, true, false, false, Math.max(0, sharedVisionFallback));
        }
        boolean enabled = readBoolean(fogMap, "fogEnabled", "enabled", "isEnabled", "fog", "active", "visibilityEnabled");
        boolean revealFromTokens = readBoolean(fogMap, "revealFromTokens", "revealTokens", "tokensReveal", "showFromTokens");
        boolean retainExploredCells = readBoolean(fogMap, "retainExploredCells", "keepExplored", "persistExplored", "rememberExplored");
        boolean nightMode = resolveNightMode(fogMap);
        int sharedVisionDistance = resolveSharedVisionDistance(fogMap, sharedVisionFallback);
        return new FogSnapshot(enabled, revealFromTokens, retainExploredCells, nightMode, sharedVisionDistance);
    }

    static boolean isFogEnabled(JsonNode fogSettings) {
        return snapshot(fogSettings, 0).enabled();
    }

    static boolean isRevealFromTokensEnabled(JsonNode fogSettings) {
        return snapshot(fogSettings, 0).revealFromTokens();
    }

    static boolean isRetainExploredCellsEnabled(JsonNode fogSettings) {
        return snapshot(fogSettings, 0).retainExploredCells();
    }

    static boolean isNightMode(JsonNode fogSettings) {
        return snapshot(fogSettings, 0).nightMode();
    }

    static int resolveVisionRadius(Token token, boolean isNightMode, int fallback) {
        int preferred = isNightMode ? token.getNightVision() : token.getDayVision();
        int alternate = isNightMode ? token.getDayVision() : token.getNightVision();
        int radius = preferred > 0 ? preferred : (alternate > 0 ? alternate : fallback);
        return Math.max(0, radius);
    }

    static int resolveSharedVisionDistance(JsonNode fogSettings, int fallback) {
        return snapshot(fogSettings, fallback).sharedVisionDistance();
    }

    private static boolean readBoolean(Map<String, Object> fogMap, String... keys) {
        Object value = firstNonNull(fogMap, keys);
        return MapBattleRulesGeometrySupport.readBoolean(value, false);
    }

    private static Object firstNonNull(Map<String, Object> fogMap, String... keys) {
        if (fogMap == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key == null) continue;
            Object value = fogMap.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static boolean resolveNightMode(Map<String, Object> fogMap) {
        if (fogMap == null || fogMap.isEmpty()) {
            return false;
        }
        Object value = fogMap.get("timeOfDay");
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase();
            if (normalized.equals("night") || normalized.equals("dark")) return true;
            if (normalized.equals("day") || normalized.equals("light")) return false;
        }
        value = fogMap.get("dayNight");
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase();
            if (normalized.equals("night") || normalized.equals("dark")) return true;
            if (normalized.equals("day") || normalized.equals("light")) return false;
        }
        value = fogMap.get("mode");
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase();
            if (normalized.equals("night") || normalized.equals("dark")) return true;
            if (normalized.equals("day") || normalized.equals("light")) return false;
        }
        Object boolValue = firstNonNull(fogMap, "nightMode", "isNightMode", "night", "isNight", "darkness");
        return MapBattleRulesGeometrySupport.readBoolean(boolValue, false);
    }

    private static int resolveSharedVisionDistance(Map<String, Object> fogMap, int fallback) {
        if (fogMap == null || fogMap.isEmpty()) {
            return Math.max(0, fallback);
        }
        int distance = MapBattleRulesGeometrySupport.readInt(firstNonNull(
                fogMap, "sharedVisionDistance", "sharedSightDistance", "visionDistance", "groupDistance", "sharedDistance"
        ), fallback);
        return Math.max(0, distance);
    }

    record FogSnapshot(boolean enabled,
                       boolean revealFromTokens,
                       boolean retainExploredCells,
                       boolean nightMode,
                       int sharedVisionDistance) {}
}
