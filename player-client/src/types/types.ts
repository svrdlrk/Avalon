export type Role = 'DM' | 'PLAYER';

export interface GridConfig {
    cellSize: number;
    cols: number;
    rows: number;
    offsetX: number;
    offsetY: number;
}

export interface TokenDto {
    id: string;
    name: string;
    col: number;
    row: number;
    ownerId: string | null;
    hp: number;
    maxHp: number;
    /** Размер токена в клетках (1..4). */
    gridSize: number;
    /** Относительный URL изображения или null. */
    imageUrl: string | null;
}

export interface PlayerDto {
    id: string;
    name: string;
    role: Role;
}

export interface MapObjectDto {
    id: string;
    type: string;
    col: number;
    row: number;
    width: number;
    height: number;
    /** Размер объекта в клетках (1..4). */
    gridSize: number;
    /** Относительный URL текстуры или null. */
    imageUrl: string | null;
}

export interface SessionStateDto {
    myPlayerId: string;
    grid: GridConfig;
    tokens: TokenDto[];
    players: PlayerDto[];
    objects: MapObjectDto[];
    backgroundUrl?: string;
}

export interface MapLayoutUpdateDto {
    grid: GridConfig;
    tokens: TokenDto[];
    objects: MapObjectDto[];
    backgroundUrl?: string;
}

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
    | 'MAP_BACKGROUND_UPDATED';

export interface WsMessage<T> {
    type: WsEventType;
    sessionId: string;
    version: number;
    payload: T;
}