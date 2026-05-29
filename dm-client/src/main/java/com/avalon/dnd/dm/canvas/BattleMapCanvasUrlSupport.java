package com.avalon.dnd.dm.canvas;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class BattleMapCanvasUrlSupport {

    private BattleMapCanvasUrlSupport() {
    }

    public static String resolveServerUrl(String serverBaseUrl, String path) {
        if (path == null || path.isBlank()) return null;

        String trimmed = path.trim();
        boolean hasUriScheme = trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") && !trimmed.matches("^[a-zA-Z]:[\\\\/].*");
        if (hasUriScheme) {
            String relative = extractAssetPath(trimmed);
            return relative != null ? joinServerUrl(serverBaseUrl, relative) : trimmed;
        }

        String cleaned = trimmed.replace('\\', '/');
        String lower = cleaned.toLowerCase(Locale.ROOT);
        String relative = extractAssetPath(cleaned);
        if (relative != null) {
            return joinServerUrl(serverBaseUrl, relative);
        }

        if (lower.startsWith("/maps/") || lower.startsWith("maps/")) {
            String noSlash = cleaned.replaceFirst("^/+", "");
            return joinServerUrl(serverBaseUrl, "/uploads/" + noSlash);
        }

        if (cleaned.startsWith("/")) {
            return joinServerUrl(serverBaseUrl, cleaned);
        }

        return joinServerUrl(serverBaseUrl, "/uploads/assets/" + cleaned.replaceFirst("^/+", ""));
    }

    public static String encodeUrl(String url) {
        if (url == null) return null;
        try {
            int schemeEnd = url.indexOf("://");
            if (schemeEnd < 0) return url;
            int pathStart = url.indexOf('/', schemeEnd + 3);
            if (pathStart < 0) return url;

            String base = url.substring(0, pathStart);
            String path = url.substring(pathStart);
            String[] segments = path.split("/", -1);
            StringBuilder sb = new StringBuilder(base);
            for (int i = 0; i < segments.length; i++) {
                if (i > 0) sb.append('/');
                sb.append(encodePathSegment(segments[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return url;
        }
    }

    private static String joinServerUrl(String serverBaseUrl, String path) {
        boolean baseEndsWithSlash = serverBaseUrl.endsWith("/");
        boolean pathStartsWithSlash = path.startsWith("/");

        if (baseEndsWithSlash && pathStartsWithSlash) {
            return serverBaseUrl.substring(0, serverBaseUrl.length() - 1) + path;
        }
        if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return serverBaseUrl + "/" + path;
        }
        return serverBaseUrl + path;
    }

    private static String extractAssetPath(String raw) {
        String normalized = raw.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        String[] markers = {"/uploads/", "uploads/", "/assets/", "assets/"};
        for (String marker : markers) {
            int idx = lower.indexOf(marker);
            if (idx >= 0) {
                String slice = normalized.substring(idx);
                return slice.startsWith("/") ? slice : "/" + slice;
            }
        }
        int bang = normalized.indexOf("!/");
        if (bang >= 0) {
            String tail = normalized.substring(bang + 2);
            String tailLower = tail.toLowerCase(Locale.ROOT);
            for (String marker : markers) {
                int idx = tailLower.indexOf(marker);
                if (idx >= 0) {
                    String slice = tail.substring(idx);
                    return slice.startsWith("/") ? slice : "/" + slice;
                }
            }
        }
        return null;
    }

    private static String encodePathSegment(String segment) {
        if (segment == null || segment.isEmpty()) return segment == null ? "" : segment;
        try {
            return new URI(null, null, "/" + segment, null).toASCIIString().substring(1);
        } catch (Exception e) {
            byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int v = b & 0xFF;
                if ((v >= 'A' && v <= 'Z') || (v >= 'a' && v <= 'z') ||
                        (v >= '0' && v <= '9') ||
                        v == '-' || v == '_' || v == '.' || v == '~' || v == '+') {
                    sb.append((char) v);
                } else {
                    sb.append(String.format("%%%02X", v));
                }
            }
            return sb.toString();
        }
    }
}
