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
}

export interface SessionStateDto {
    myPlayerId: string;
    grid: GridConfig;
    tokens: TokenDto[];
    players: PlayerDto[];
    objects: MapObjectDto[];
}

export type WsEventType =
    | 'TOKEN_MOVED'
    | 'TOKEN_ADDED'
    | 'TOKEN_REMOVED'
    | 'TOKEN_ASSIGNED'
    | 'MAP_OBJECT_ADDED'
    | 'MAP_OBJECT_REMOVED'
    | 'PLAYER_JOINED'
    | 'PLAYER_LEFT'
    | 'SESSION_STATE';

export interface WsMessage<T> {
    type: WsEventType;
    sessionId: string;
    version: number;
    payload: T;
}