package com.avalon.dnd.shared;

public class MapObjectRemoveEvent {

    private String objectId;

    public MapObjectRemoveEvent() {}

    public MapObjectRemoveEvent(String objectId) {
        this.objectId = objectId;
    }

    public String getObjectId() {
        return objectId;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }
}