package com.avalon.dnd.server.service;

import java.util.Locale;

public final class AssetUrlNormalizer {

    private AssetUrlNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) return null;

        String value = raw.trim().replace('\\', '/');
        if (value.isBlank()) return null;

        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:")) {
            return value;
        }

        // For file:/ URIs and local absolute paths, try to extract a web-friendly
        // uploads/assets path so browser clients can load the image from the server.
        String extracted = extractKnownWebPath(value);
        if (extracted != null) {
            return extracted;
        }

        if (value.startsWith("file:")) {
            return value;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.startsWith("/maps/") || lower.startsWith("maps/")) {
            String noSlash = value.replaceFirst("^/+", "");
            return "/uploads/" + noSlash;
        }

        if (value.startsWith("/")) {
            return value;
        }

        return "/uploads/assets/" + value.replaceFirst("^/+", "");
    }

    private static String extractKnownWebPath(String value) {
        String lower = value.toLowerCase(Locale.ROOT);

        for (String marker : new String[]{"/uploads/", "uploads/", "/assets/", "assets/"}) {
            int idx = lower.indexOf(marker);
            if (idx >= 0) {
                String slice = value.substring(idx).replaceFirst("^/+", "");
                return "/" + slice;
            }
        }
        return null;
    }
}
