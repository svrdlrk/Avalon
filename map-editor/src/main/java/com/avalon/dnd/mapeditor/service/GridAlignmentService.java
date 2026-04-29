package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.MapProject;
import com.avalon.dnd.mapeditor.model.ReferenceOverlay;
import com.avalon.dnd.shared.GridConfig;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class GridAlignmentService {

    private GridAlignmentService() {}

    public static Optional<GridConfig> fitToReference(MapProject project, ReferenceOverlay reference) {
        if (project == null || reference == null) {
            return Optional.empty();
        }
        String url = reference.getImageUrl();
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        Image image = loadImage(url);
        if (image == null || image.isError() || image.getWidth() <= 1 || image.getHeight() <= 1) {
            return Optional.empty();
        }

        PixelReader reader = image.getPixelReader();
        if (reader == null) {
            return Optional.empty();
        }

        double scale = Math.max(0.1, reference.getScale());
        int sampledWidth = Math.max(8, (int) Math.round(image.getWidth() * scale));
        int sampledHeight = Math.max(8, (int) Math.round(image.getHeight() * scale));

        PeriodEstimate xEstimate = estimatePeriod(reader, (int) image.getWidth(), (int) image.getHeight(), true);
        PeriodEstimate yEstimate = estimatePeriod(reader, (int) image.getWidth(), (int) image.getHeight(), false);
        int periodPx = choosePeriod(xEstimate, yEstimate);
        if (periodPx <= 0) {
            return Optional.empty();
        }

        int phaseX = xEstimate == null ? 0 : xEstimate.phase;
        int phaseY = yEstimate == null ? 0 : yEstimate.phase;

        GridConfig grid = project.getGrid();
        if (grid == null) {
            grid = new GridConfig(64, 40, 30);
            project.setGrid(grid);
        }

        int cellSize = Math.max(4, (int) Math.round(periodPx * scale));
        int offsetX = (int) Math.round(reference.getOffsetX() + phaseX * scale);
        int offsetY = (int) Math.round(reference.getOffsetY() + phaseY * scale);
        int cols = Math.max(1, (int) Math.round(sampledWidth / (double) cellSize));
        int rows = Math.max(1, (int) Math.round(sampledHeight / (double) cellSize));

        grid.setCellSize(cellSize);
        grid.setCols(cols);
        grid.setRows(rows);
        grid.setOffsetX(offsetX);
        grid.setOffsetY(offsetY);
        return Optional.of(grid);
    }


    private static Image loadImage(String url) {
        String resolved = resolveImageSource(url);
        if (resolved == null || resolved.isBlank()) {
            return null;
        }
        try {
            return new Image(resolved, false);
        } catch (IllegalArgumentException ex) {
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String resolveImageSource(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("jar:") ||
                trimmed.startsWith("file:") || trimmed.startsWith("data:")) {
            return encodeUrl(trimmed);
        }

        String cleaned = trimmed.replace('\\', '/');
        if (cleaned.startsWith("/uploads/") || cleaned.startsWith("uploads/")) {
            Path local = resolveProjectPath(cleaned.startsWith("/") ? cleaned.substring(1) : cleaned);
            if (local != null) {
                return local.toUri().toString();
            }
            return cleaned.startsWith("/") ? cleaned : "/" + cleaned;
        }

        Path local = resolveProjectPath(cleaned.startsWith("/") ? cleaned.substring(1) : cleaned);
        if (local != null) {
            return local.toUri().toString();
        }

        return encodeUrl(cleaned);
    }

    private static Path resolveProjectPath(String relative) {
        if (relative == null || relative.isBlank()) {
            return null;
        }
        String cleaned = relative.startsWith("/") ? relative.substring(1) : relative;
        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path current = cwd;
        for (int i = 0; i < 6 && current != null; i++, current = current.getParent()) {
            Path candidate = current.resolve(cleaned).normalize();
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        Path direct = Path.of(cleaned);
        if (Files.exists(direct)) {
            return direct.toAbsolutePath().normalize();
        }
        return null;
    }

    private static String encodeUrl(String url) {
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

    private static String encodePathSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return segment == null ? "" : segment;
        }
        try {
            return new URI(null, null, "/" + segment, null).toASCIIString().substring(1);
        } catch (Exception e) {
            byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                int v = b & 0xFF;
                if ((v >= 'A' && v <= 'Z') || (v >= 'a' && v <= 'z') ||
                        (v >= '0' && v <= '9') || v == '-' || v == '_' || v == '.' || v == '~' || v == '+') {
                    sb.append((char) v);
                } else {
                    sb.append(String.format("%%%02X", v));
                }
            }
            return sb.toString();
        }
    }

    private static int choosePeriod(PeriodEstimate xEstimate, PeriodEstimate yEstimate) {
        if (xEstimate == null) return yEstimate == null ? -1 : yEstimate.period;
        if (yEstimate == null) return xEstimate.period;
        double xScore = xEstimate.score;
        double yScore = yEstimate.score;
        if (xScore <= 0 && yScore <= 0) {
            return Math.max(xEstimate.period, yEstimate.period);
        }
        if (xScore >= yScore) {
            return xEstimate.period;
        }
        return yEstimate.period;
    }

    private static PeriodEstimate estimatePeriod(PixelReader reader, int width, int height, boolean horizontalScan) {
        int minPeriod = 8;
        int maxPeriod = Math.max(minPeriod + 1, Math.min(256, horizontalScan ? width / 2 : height / 2));
        if (maxPeriod <= minPeriod) {
            return null;
        }

        double[] samples = new double[horizontalScan ? width : height];
        int cross = horizontalScan ? height : width;
        int start = (int) Math.round(cross * 0.25);
        int end = (int) Math.round(cross * 0.75);
        if (end <= start) {
            start = 0;
            end = cross;
        }

        for (int i = 0; i < samples.length; i++) {
            double sum = 0.0;
            int count = 0;
            for (int j = start; j < end; j++) {
                Color c = horizontalScan ? reader.getColor(i, j) : reader.getColor(j, i);
                sum += luminance(c);
                count++;
            }
            samples[i] = count == 0 ? 1.0 : sum / count;
        }

        double bestScore = Double.NEGATIVE_INFINITY;
        int bestPeriod = -1;
        int bestPhase = 0;
        for (int period = minPeriod; period <= maxPeriod; period++) {
            PeriodEstimate candidate = scorePeriod(samples, period);
            if (candidate != null && candidate.score > bestScore) {
                bestScore = candidate.score;
                bestPeriod = candidate.period;
                bestPhase = candidate.phase;
            }
        }

        if (bestPeriod <= 0) {
            return null;
        }
        return new PeriodEstimate(bestPeriod, bestPhase, bestScore);
    }

    private static PeriodEstimate scorePeriod(double[] samples, int period) {
        double[] bucketSums = new double[period];
        int[] bucketCounts = new int[period];
        for (int i = 0; i < samples.length; i++) {
            int bucket = Math.floorMod(i, period);
            bucketSums[bucket] += samples[i];
            bucketCounts[bucket]++;
        }

        double[] bucketMeans = new double[period];
        double mean = 0.0;
        for (int i = 0; i < period; i++) {
            bucketMeans[i] = bucketCounts[i] == 0 ? 1.0 : bucketSums[i] / bucketCounts[i];
            mean += bucketMeans[i];
        }
        mean /= period;

        double variance = 0.0;
        for (double value : bucketMeans) {
            double delta = value - mean;
            variance += delta * delta;
        }
        variance /= period;

        int phase = 0;
        double darkest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < period; i++) {
            if (bucketMeans[i] < darkest) {
                darkest = bucketMeans[i];
                phase = i;
            }
        }

        return new PeriodEstimate(period, phase, variance);
    }

    private static double luminance(Color color) {
        if (color == null) {
            return 1.0;
        }
        return 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
    }

    private record PeriodEstimate(int period, int phase, double score) {}
}
