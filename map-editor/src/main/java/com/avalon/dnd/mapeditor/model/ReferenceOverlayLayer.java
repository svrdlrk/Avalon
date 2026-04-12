package com.avalon.dnd.mapeditor.model;

public class ReferenceOverlayLayer extends ReferenceOverlay {

    public ReferenceOverlayLayer() {
        super();
    }

    @Override
    public ReferenceOverlayLayer copy() {
        ReferenceOverlayLayer copy = new ReferenceOverlayLayer();
        copy.setImageUrl(getImageUrl());
        copy.setVisible(isVisible());
        copy.setLocked(isLocked());
        copy.setOpacity(getOpacity());
        copy.setScale(getScale());
        copy.setOffsetX(getOffsetX());
        copy.setOffsetY(getOffsetY());
        return copy;
    }

    public ReferenceOverlayLayer copy(ReferenceOverlay source) {
        ReferenceOverlayLayer copy = new ReferenceOverlayLayer();
        if (source == null) {
            return copy;
        }
        copy.setImageUrl(source.getImageUrl());
        copy.setVisible(source.isVisible());
        copy.setLocked(source.isLocked());
        copy.setOpacity(source.getOpacity());
        copy.setScale(source.getScale());
        copy.setOffsetX(source.getOffsetX());
        copy.setOffsetY(source.getOffsetY());
        return copy;
    }
}
