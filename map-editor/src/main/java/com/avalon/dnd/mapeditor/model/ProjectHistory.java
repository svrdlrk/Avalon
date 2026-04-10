package com.avalon.dnd.mapeditor.model;

import java.util.ArrayDeque;
import java.util.Deque;

public class ProjectHistory {

    private static final int MAX_DEPTH = 50;

    private final Deque<MapProject> undoStack = new ArrayDeque<>();
    private final Deque<MapProject> redoStack = new ArrayDeque<>();

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public void record(MapProject project) {
        if (project == null) {
            return;
        }
        undoStack.push(project.copy());
        redoStack.clear();
        while (undoStack.size() > MAX_DEPTH) {
            undoStack.removeLast();
        }
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public MapProject undo(MapProject current) {
        if (undoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            redoStack.push(current.copy());
        }
        return undoStack.pop().copy();
    }

    public MapProject redo(MapProject current) {
        if (redoStack.isEmpty()) {
            return current;
        }
        if (current != null) {
            undoStack.push(current.copy());
        }
        return redoStack.pop().copy();
    }
}
