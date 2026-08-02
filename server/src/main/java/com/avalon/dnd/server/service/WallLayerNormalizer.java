package com.avalon.dnd.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class WallLayerNormalizer {

    private WallLayerNormalizer() {
    }

    public static JsonNode normalize(JsonNode wallLayer) {
        if (wallLayer == null || wallLayer.isNull() || wallLayer.isMissingNode() || !wallLayer.isObject()) {
            return wallLayer;
        }

        ObjectNode root = wallLayer.deepCopy();
        root.put("visible", true);

        boolean defaultBlocksMovement = root.path("defaultBlocksMovement").asBoolean(true);
        boolean defaultBlocksSight = root.path("defaultBlocksSight").asBoolean(true);

        JsonNode pathsNode = firstArray(
                root.get("paths"),
                root.get("walls"),
                root.get("segments"),
                root.get("polylines"),
                root.get("lines")
        );
        if (pathsNode != null && root.get("paths") != pathsNode) {
            root.set("paths", pathsNode);
        }

        JsonNode canonicalPaths = root.get("paths");
        if (canonicalPaths != null && canonicalPaths.isArray()) {
            for (JsonNode pathNode : canonicalPaths) {
                if (!(pathNode instanceof ObjectNode path)) {
                    continue;
                }
                if (path.get("visible") == null || path.get("visible").isNull()) {
                    path.put("visible", true);
                }
                if (path.get("blocksMovement") == null || path.get("blocksMovement").isNull()) {
                    path.put("blocksMovement", defaultBlocksMovement);
                }
                if (path.get("blocksSight") == null || path.get("blocksSight").isNull()) {
                    path.put("blocksSight", defaultBlocksSight);
                }

                JsonNode pointsNode = firstArray(
                        path.get("points"),
                        path.get("vertices"),
                        path.get("coords"),
                        path.get("pts")
                );
                if (pointsNode != null && path.get("points") != pointsNode) {
                    path.set("points", pointsNode);
                }
            }
        }

        return root;
    }

    private static JsonNode firstArray(JsonNode... candidates) {
        if (candidates == null) {
            return null;
        }
        for (JsonNode candidate : candidates) {
            if (candidate != null && candidate.isArray()) {
                return candidate;
            }
        }
        return null;
    }
}
