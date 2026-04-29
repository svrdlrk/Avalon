package com.avalon.dnd.mapeditor.tool;

import com.avalon.dnd.mapeditor.model.AssetDefinition;
import com.avalon.dnd.mapeditor.model.EditorState;
import com.avalon.dnd.mapeditor.model.MapLayer;
import com.avalon.dnd.mapeditor.model.MapPlacement;
import com.avalon.dnd.mapeditor.model.PlacementKind;
import com.avalon.dnd.mapeditor.ui.MapEditorCanvas;
import javafx.scene.input.MouseEvent;

import java.util.UUID;

public class TokenPlaceTool implements Tool {

    @Override
    public String getId() {
        return "token";
    }

    @Override
    public String getDisplayName() {
        return "Token";
    }

    @Override
    public void onMousePressed(MouseEvent event, MapEditorCanvas canvas, EditorState state) {
        AssetDefinition asset = state.selectedTokenAsset();
        if (asset == null || state.getProject() == null) {
            return;
        }
        if (asset.getKind() != PlacementKind.TOKEN && asset.getKind() != PlacementKind.SPAWN) {
            return;
        }

        int[] cell = canvas.screenToCell(event.getX(), event.getY());
        if (cell == null) {
            return;
        }

        MapLayer layer = state.selectedLayer();
        String layerId = layer == null ? null : layer.getId();
        if (state.isLayerLocked(layerId)) {
            return;
        }

        state.recordHistory();
        MapPlacement placement = MapPlacement.fromAsset(UUID.randomUUID().toString(), asset, cell[0], cell[1], layerId);
        placement.setOwnerId(null);
        placement.setFaction(null);
        placement.setHp(10);
        placement.setMaxHp(10);
        placement.setHidden(false);
        placement.setInitiativeOrder(0);
        placement.setNpc(asset.getKind() == PlacementKind.SPAWN);
        state.getProject().addPlacement(placement);
        state.selectPlacement(placement.getId());
        canvas.requestRender();
    }
}
