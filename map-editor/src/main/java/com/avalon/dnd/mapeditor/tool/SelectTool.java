package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;

public class SelectTool implements Tool {

    @Override
    public String getId() { return "select"; }

    @Override
    public String getDisplayName() { return "Select"; }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        MapPlacement hit = canvas.findPlacementAt(event.getX(), event.getY());
        if (hit != null) {
            state.selectPlacement(hit.getId());
            state.selectAsset(hit.getAssetId());
            state.selectLayer(hit.getLayerId());
        } else {
            var wallHit = canvas.findWallPathAt(event.getX(), event.getY());
            if (wallHit != null) {
                state.selectWallPath(wallHit.getId());
            } else {
                state.clearSelection();
            }
        }
        canvas.requestRender();
    }
}
