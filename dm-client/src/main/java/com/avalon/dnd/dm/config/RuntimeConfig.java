package com.avalon.dnd.dm.config;

public final class RuntimeConfig {
    private RuntimeConfig() {}

    public static String defaultServerUrl() {
        return normalize(System.getProperty("avalon.serverUrl", System.getenv().getOrDefault("AVALON_SERVER_URL", "http://localhost:8080")));
    }

    public static String defaultPlayerClientUrl() {
        return normalize(System.getProperty("avalon.playerClientUrl", System.getenv().getOrDefault("AVALON_PLAYER_CLIENT_URL", "http://localhost:5173")));
    }

    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            return "http://localhost:8080";
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        try {
            return java.net.URI.create(value).resolve("/").toString().replaceAll("/+$", "").replaceAll("/$", "");
        } catch (Exception ex) {
            return value.replaceAll("/+$", "");
        }
    }
}
