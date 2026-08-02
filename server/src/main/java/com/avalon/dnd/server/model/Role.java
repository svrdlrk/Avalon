package com.avalon.dnd.server.model;

public enum Role {
    DM,
    PLAYER,
    /** Read-only browser connection created from a DM-issued projector link. */
    OBSERVER
}
