package com.avalon.dnd.dm.config;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

public final class RuntimeConfig {
    private RuntimeConfig() {}

    public static String defaultServerUrl() {
        return normalize(System.getProperty("avalon.serverUrl", System.getenv().getOrDefault("AVALON_SERVER_URL", "http://localhost:8080")));
    }

    public static String defaultPlayerClientUrl() {
        String configured = System.getProperty("avalon.playerClientUrl", System.getenv("AVALON_PLAYER_CLIENT_URL"));
        if (configured != null && !configured.isBlank()) {
            return normalize(configured);
        }
        return normalize("http://" + resolveLanHost() + ":5173");
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

    private static String resolveLanHost() {
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface == null || !networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                    continue;
                }
                for (var address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address inet4 && !inet4.isLoopbackAddress() && !inet4.isLinkLocalAddress()) {
                        return inet4.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "localhost";
    }
}
