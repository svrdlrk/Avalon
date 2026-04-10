package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;

public class EraseTool implements Tool {

    @Override
    public String getId() { return "erase"; }

    @Override
    public String getDisplayName() { return "Erase"; }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        MapPlacement hit = canvas.findPlacementAt(event.getX(), event.getY());
        if (hit != null && state.getProject() != null && !hit.isLocked() && !state.isLayerLocked(hit.getLayerId())) {
            state.recordHistory();
            state.getProject().removePlacementById(hit.getId());
            state.clearSelection();
            canvas.requestRender();
        }
    }
}
