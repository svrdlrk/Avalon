package com.avalon.dnd.dm.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches asset catalog payloads from the server without mixing network code into MainStage.
 */
public final class DmCatalogFetcher {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final java.util.concurrent.ConcurrentHashMap<String, CatalogData> cache = new java.util.concurrent.ConcurrentHashMap<>();

    public CatalogData fetch(String baseUrl) throws IOException {
        if (baseUrl == null || baseUrl.isBlank()) {
            return new CatalogData(List.of(), List.of());
        }
        CatalogData cached = cache.get(baseUrl);
        if (cached != null) {
            return cached;
        }

        List<JsonNode> loadedTokens = new ArrayList<>();
        List<JsonNode> loadedObjects = new ArrayList<>();

        var req = new okhttp3.Request.Builder()
                .url(baseUrl + "/api/assets/catalog").build();
        try (var response = new okhttp3.OkHttpClient().newCall(req).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonNode root = objectMapper.readTree(response.body().string());
                if (root.has("tokens")) {
                    root.get("tokens").forEach(loadedTokens::add);
                }
                if (root.has("objects")) {
                    root.get("objects").forEach(loadedObjects::add);
                }
            }
        }
        CatalogData data = new CatalogData(List.copyOf(loadedTokens), List.copyOf(loadedObjects));
        cache.put(baseUrl, data);
        return data;
    }

    public record CatalogData(List<JsonNode> tokens, List<JsonNode> objects) {
    }
}
