package com.avalon.dnd.shared.uploads;

import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AssetCatalogSupport {

    private static final Pattern SIZE_PATTERN = Pattern.compile("(?i).*(\\d+)\\s*[x×х]\\s*(\\d+).*");
    private static final Pattern DIGITS_PATTERN = Pattern.compile("(?i).*(\\d+).*");

    private AssetCatalogSupport() {
    }

    public static boolean isExcludedAssetPath(Path path) {
        if (path == null) return false;
        String normalized = path.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return normalized.contains("/uploads/maps/finished/")
                || normalized.contains("/uploads/maps/backups/");
    }

    public static boolean isImageFile(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") || name.endsWith(".gif");
    }

    public static boolean isNamesFile(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.startsWith("names") && lower.endsWith(".json");
    }

    public static int inferGridSizeFromPath(Path baseDir, String imageUrl) {
        String probe = (imageUrl == null ? "" : imageUrl).replace('\\', '/').toLowerCase(Locale.ROOT);
        if (probe.contains("/gargantuan/")) return 4;
        if (probe.contains("/huge/")) return 3;
        if (probe.contains("/large/")) return 2;
        if (probe.contains("/small/") || probe.contains("/medium/") || probe.contains("/npc/")) return 1;
        if (baseDir != null) {
            try {
                String path = baseDir.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
                if (path.contains("/gargantuan/")) return 4;
                if (path.contains("/huge/")) return 3;
                if (path.contains("/large/")) return 2;
                if (path.contains("/small/") || path.contains("/medium/") || path.contains("/npc/")) return 1;
            } catch (Exception ignored) {
            }
        }
        return 0;
    }

    public static String deriveCategory(Path baseDir, String imageUrl) {
        String path = imageUrl == null ? null : imageUrl.replace('\\', '/');
        if (path != null) {
            int uploadsIdx = path.toLowerCase(Locale.ROOT).indexOf("/uploads/");
            if (uploadsIdx >= 0) {
                String tail = path.substring(uploadsIdx + "/uploads/".length());
                int slash = tail.indexOf('/');
                if (slash > 0) {
                    return tail.substring(0, slash).toLowerCase(Locale.ROOT);
                }
            }
        }
        if (baseDir != null) {
            try {
                Path normalized = baseDir.toAbsolutePath().normalize();
                for (int i = 0; i < normalized.getNameCount(); i++) {
                    if ("assets".equalsIgnoreCase(normalized.getName(i).toString()) && i + 1 < normalized.getNameCount()) {
                        return normalized.getName(i + 1).toString().toLowerCase(Locale.ROOT);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return "misc";
    }


    public static String deriveCategory(Path baseDir, Path path) {
        return deriveCategory(baseDir, path == null ? null : path.toString());
    }
    public static String humanize(String text) {
        if (text == null || text.isBlank()) return "Asset";
        String cleaned = text.replace('_', ' ').replace('-', ' ').trim();
        if (cleaned.isBlank()) return "Asset";
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    public static String toId(String source) {
        return source == null ? "" : source.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    public static String normalizeImageUrl(String imageUrl, Path baseDir) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String normalized = imageUrl.replace('\\', '/');
        if (normalized.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) {
            return normalized;
        }
        if (normalized.startsWith("/uploads/")) {
            return normalized;
        }
        if (baseDir != null) {
            try {
                Path base = baseDir.toAbsolutePath().normalize();
                Path candidate = base.resolve(normalized).normalize();
                String candidateText = candidate.toString().replace('\\', '/');
                int uploadsIdx = candidateText.toLowerCase(Locale.ROOT).indexOf("/uploads/");
                if (uploadsIdx >= 0) {
                    return candidateText.substring(uploadsIdx);
                }
                return candidateText;
            } catch (Exception ignored) {
            }
        }
        return normalized;
    }

    public static String stripExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    public static String lastPathSegment(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash < 0 ? normalized : normalized.substring(slash + 1);
    }

    public static String normalizeKey(String source) {
        if (source == null) return "";
        return stripExtension(source.replace('\\', '/').toLowerCase(Locale.ROOT))
                .replaceAll("[^a-z0-9а-яё]+", "");
    }

    public static int[] parseSizeString(String text) {
        if (text == null) return null;
        Matcher matcher = SIZE_PATTERN.matcher(text.trim());
        if (matcher.matches()) {
            try {
                return new int[] { Math.max(1, Integer.parseInt(matcher.group(1))), Math.max(1, Integer.parseInt(matcher.group(2))) };
            } catch (Exception ignored) {
            }
        }
        Matcher digits = DIGITS_PATTERN.matcher(text.trim());
        if (digits.matches()) {
            try {
                int size = Math.max(1, Integer.parseInt(digits.group(1)));
                return new int[] { size, size };
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
