package com.avalon.dnd.dm.ui;

import com.avalon.dnd.shared.InitEntry;
import com.avalon.dnd.shared.TokenDto;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Small formatting helper extracted from the DM stage to keep UI code focused on wiring.
 */
public final class DmUiFormatters {

    private DmUiFormatters() {
    }

    public static String shortId(String id) {
        return id == null ? "" : id.length() <= 8 ? id : id.substring(0, 8) + "…";
    }

    public static String firstCatalogImageUrl(JsonNode node) {
        if (node == null || node.isNull()) return null;
        for (String key : List.of("imageUrl", "imagePath", "image", "path", "file", "src", "url", "assetPath", "sprite", "thumbnail")) {
            JsonNode field = node.get(key);
            if (field == null || field.isNull()) continue;
            String value = field.asText(null);
            if (value != null && !value.isBlank() && looksLikeImagePath(value)) return value;
        }
        return null;
    }

    public static boolean looksLikeImagePath(String value) {
        if (value == null) return false;
        String lower = value.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (lower.isBlank() || lower.endsWith("/")) return false;
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".svg")
                || lower.contains("/uploads/") || lower.contains("/assets/") || lower.contains(".png?") || lower.contains(".jpg?");
    }

    public static String normalizeCatalogImageUrl(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replace('\\', '/');
        if (value.isBlank()) return null;
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:") || value.startsWith("jar:")) {
            return value;
        }
        if (value.startsWith("file:")) {
            String extracted = extractAssetPath(value);
            return extracted != null ? extracted : value;
        }
        if (value.startsWith("/uploads/") || value.startsWith("uploads/")) {
            return value.startsWith("/") ? value : "/" + value;
        }
        if (value.startsWith("/assets/") || value.startsWith("assets/")) {
            return value.startsWith("/") ? value : "/" + value;
        }
        value = value.startsWith("/") ? value.substring(1) : value;
        return "/uploads/assets/" + value;
    }

    private static String extractAssetPath(String raw) {
        String normalized = raw.replace('\\', '/');
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String marker : List.of("/uploads/", "uploads/", "/assets/", "assets/")) {
            int idx = lower.indexOf(marker);
            if (idx >= 0) {
                String slice = normalized.substring(idx).replaceFirst("^/+", "");
                return "/" + slice;
            }
        }
        return null;
    }

    public static String formatTokenLabel(TokenDto token, Function<String, String> playerNameResolver) {
        if (token == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(token.getName() == null || token.getName().isBlank() ? "—" : token.getName());
        if (token.getId() != null && !token.getId().isBlank()) {
            sb.append(" · #").append(shortId(token.getId()));
        }
        sb.append(" · HP ").append(token.getHp()).append('/').append(token.getMaxHp());
        if (token.getGridSize() > 1) {
            sb.append(" · ").append(token.getGridSize()).append('×').append(token.getGridSize());
        }
        String owner = token.getOwnerId() == null ? "NPC" : playerNameResolver.apply(token.getOwnerId());
        if (owner != null && !owner.isBlank()) {
            sb.append(" · ").append(owner);
        }
        sb.append(" · @ ").append(token.getCol()).append(',').append(token.getRow());
        return sb.toString();
    }

    public static String formatInitiativeEntry(InitEntry entry,
                                               Function<String, String> playerNameResolver,
                                               Function<String, TokenDto> tokenResolver) {
        if (entry == null) return "";
        TokenDto live = entry.id() == null ? null : tokenResolver.apply(entry.id());
        StringBuilder sb = new StringBuilder();
        if (live != null) {
            sb.append(formatTokenLabel(live, playerNameResolver));
        } else {
            sb.append(entry.name() == null || entry.name().isBlank() ? "—" : entry.name());
            if (entry.id() != null && !entry.id().isBlank()) {
                sb.append(" · #").append(shortId(entry.id()));
            }
            sb.append(" · HP —/— · @ —");
        }
        sb.append(" · Init ").append(entry.initiative());
        return sb.toString();
    }


    public static String sessionSummary(String currentSessionId) {
        String sid = currentSessionId == null || currentSessionId.isBlank() ? "—" : shortId(currentSessionId);
        return "Session " + sid;
    }

    public static String sessionCounts(int players, int tokens, int objects) {
        return String.format(Locale.ROOT, "%d players • %d tokens • %d objects", players, tokens, objects);
    }
    public static String buildTokenMetaLine(TokenDto token, Function<String, String> playerNameResolver) {
        StringBuilder sb = new StringBuilder();
        if (token.getId() != null && !token.getId().isBlank()) {
            sb.append("#").append(shortId(token.getId()));
        }
        sb.append(" · HP ").append(token.getHp()).append('/').append(token.getMaxHp());
        if (token.getGridSize() > 1) {
            sb.append(" · ").append(token.getGridSize()).append('×').append(token.getGridSize());
        }
        String owner = token.getOwnerId() == null ? "NPC" : playerNameResolver.apply(token.getOwnerId());
        if (owner != null && !owner.isBlank()) {
            sb.append(" · ").append(owner);
        }
        sb.append(" · @ ").append(token.getCol()).append(',').append(token.getRow());
        return sb.toString();
    }

    public static int countDuplicateTokens(Collection<TokenDto> tokens, TokenDto token) {
        if (token == null) return 0;
        String key = normalizeName(token.getName());
        if (key.isEmpty()) return 1;
        int count = 0;
        for (TokenDto t : tokens) {
            if (t != null && key.equals(normalizeName(t.getName()))) count++;
        }
        return count;
    }

    public static int countDuplicateInitEntries(Collection<InitEntry> entries, InitEntry entry) {
        if (entry == null) return 0;
        String key = normalizeName(entry.name());
        if (key.isEmpty()) return 1;
        int count = 0;
        for (InitEntry e : entries) {
            if (e != null && key.equals(normalizeName(e.name()))) count++;
        }
        return count;
    }

    public static String normalizeName(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
