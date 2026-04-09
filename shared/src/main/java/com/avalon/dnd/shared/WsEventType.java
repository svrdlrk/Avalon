package com.avalon.dnd.shared;

public enum WsEventType {
    TOKEN_MOVED,
    TOKEN_ADDED,
    TOKEN_REMOVED,
    TOKEN_ASSIGNED,
    TOKEN_HP,
    MAP_UPDATED,
    MAP_OBJECT_ADDED,
    MAP_OBJECT_REMOVED,
    PLAYER_JOINED,
    PLAYER_LEFT,
    SESSION_STATE,
    MAP_BACKGROUND_UPDATED,
    INITIATIVE_UPDATED    // <-- новый: рассылка порядка инициативы всем клиентам
}