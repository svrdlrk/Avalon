package com.avalon.dnd.mapeditor.ui;

import com.avalon.dnd.mapeditor.service.ProjectRepository;
import com.avalon.dnd.shared.MicroLocationDto;
import javafx.scene.control.Spinner;

import java.nio.file.Path;

/**
 * Small path / value helpers extracted from MapEditorPane to keep the pane focused on composition.
 */
public final class MapEditorPathUtils {

    private MapEditorPathUtils() {
    }

    public static MicroLocationDto copyMicroLocation(MicroLocationDto source) {
        if (source == null) return null;
        MicroLocationDto copy = new MicroLocationDto();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setCol(source.getCol());
        copy.setRow(source.getRow());
        copy.setWidth(source.getWidth());
        copy.setHeight(source.getHeight());
        copy.setLocked(source.isLocked());
        copy.setHint(source.getHint());
        copy.setInteriorMapPath(source.getInteriorMapPath());
        return copy;
    }

    public static String safeTrim(String value) {
        return value == null ? null : (value.isBlank() ? null : value.trim());
    }

    public static int safeSpinnerInt(Spinner<Integer> spinner, int fallback) {
        Integer value = spinner == null ? null : spinner.getValue();
        return value == null ? fallback : value;
    }

    public static String defaultInteriorPath(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return "interiors/map.json";
        }
        return "interiors/" + zoneId + "/map.json";
    }

    public static Path resolveInteriorPath(ProjectRepository repository, Path documentRoot, MicroLocationDto zone) {
        String path = zone == null ? null : zone.getInteriorMapPath();
        if (path == null || path.isBlank()) {
            path = defaultInteriorPath(zone == null ? null : zone.getId());
        }
        return documentRoot == null ? Path.of(path) : repository.resolveChild(documentRoot, path);
    }
}
