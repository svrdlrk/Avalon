package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.AssetDefinition;
import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;

import java.util.UUID;

public class PlaceAssetTool implements Tool {

    @Override
    public String getId() { return "place"; }

    @Override
    public String getDisplayName() { return "Place"; }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        AssetDefinition asset = state.selectedAsset();
        if (asset == null || state.getProject() == null) return;

        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) return;

        MapLayer layer = state.selectedLayer();
        String layerId = layer == null ? null : layer.getId();
        if (state.isLayerLocked(layerId)) {
            return;
        }

        state.recordHistory();
        MapPlacement placement = MapPlacement.fromAsset(UUID.randomUUID().toString(), asset, cell[0], cell[1], layerId);
        state.getProject().addPlacement(placement);
        state.selectPlacement(placement.getId());
        canvas.requestRender();
    }
}
