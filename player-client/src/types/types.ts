export type Role = 'DM' | 'PLAYER';

export interface GridConfig {
    cellSize: number;
    cols:     number;
    rows:     number;
    offsetX:  number;
    offsetY:  number;
}

export interface TokenDto {
    id:       string;
    name:     string;
    col:      number;
    row:      number;
    ownerId:  string | null;
    hp:       number;
    maxHp:    number;
    gridSize: number;
    imageUrl: string | null;
    blocksMovement?: boolean;
    blocksSight?: boolean;
}

export interface PlayerDto {
    id:   string;
    name: string;
    role: Role;
}

export interface MapObjectDto {
    id:       string;
    type:     string;
    col:      number;
    row:      number;
    width:    number;
    height:   number;
    gridSize: number;
    imageUrl: string | null;
    blocksMovement?: boolean;
    blocksSight?: boolean;
}

export interface SessionStateDto {
    myPlayerId:    string;
    grid:          GridConfig;
    tokens:        TokenDto[];
    players:       PlayerDto[];
    objects:       MapObjectDto[];
    backgroundUrl?: string;
    initiative?:   InitiativeStateDto;
    referenceOverlayLayer?: unknown;
    terrainLayer?: unknown;
    wallLayer?: unknown;
    fogSettings?: unknown;
    assetPackIds?: string[];
}

export interface MapLayoutUpdateDto {
    grid:           GridConfig;
    tokens:         TokenDto[];
    objects:        MapObjectDto[];
    backgroundUrl?: string;
    referenceOverlayLayer?: unknown;
    terrainLayer?: unknown;
    wallLayer?: unknown;
    fogSettings?: unknown;
    assetPackIds?: string[];
}

// ---- Initiative ----

export interface InitiativeEntry {
    tokenId:    string;
    name:       string;
    initiative: number;
}

export interface InitiativeStateDto {
    entries:      InitiativeEntry[];
    currentIndex: number;
}

// ---- WS event types ----

export type WsEventType =
    | 'TOKEN_MOVED'
    | 'TOKEN_ADDED'
    | 'TOKEN_REMOVED'
    | 'TOKEN_ASSIGNED'
    | 'TOKEN_HP'
    | 'MAP_UPDATED'
    | 'MAP_OBJECT_ADDED'
    | 'MAP_OBJECT_REMOVED'
    | 'PLAYER_JOINED'
    | 'PLAYER_LEFT'
    | 'SESSION_STATE'
    | 'MAP_BACKGROUND_UPDATED'
    | 'INITIATIVE_UPDATED';

export interface WsMessage<T> {
    type:      WsEventType;
    sessionId: string;
    version:   number;
    payload:   T;
}