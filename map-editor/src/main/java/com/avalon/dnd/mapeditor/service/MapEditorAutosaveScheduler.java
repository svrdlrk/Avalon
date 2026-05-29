package com.avalon.dnd.mapeditor.service;

import com.avalon.dnd.mapeditor.model.MapProject;

public final class MapEditorAutosaveScheduler {

    private MapEditorAutosaveScheduler() {
    }

    public static void schedule(MapEditorPersistenceCoordinator coordinator, MapProject project) {
        if (coordinator == null || project == null) {
            return;
        }
        MapProject snapshot = project.copy();
        Thread worker = new Thread(() -> {
            try {
                coordinator.saveBackup(snapshot);
            } catch (Exception ex) {
                System.err.println("Backup autosave failed: " + ex.getMessage());
            }
        }, "map-editor-autosave");
        worker.setDaemon(true);
        worker.start();
    }
}
