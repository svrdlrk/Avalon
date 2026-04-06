import { create } from 'zustand';
import type {
    GridConfig,
    MapLayoutUpdateDto,
    MapObjectDto,
    PlayerDto,
    SessionStateDto,
    TokenDto,
} from '../types/types';

interface GameState {
    sessionId: string | null;
    myPlayerId: string | null;
    grid: GridConfig;
    tokens: Record<string, TokenDto>;
    objects: Record<string, MapObjectDto>;
    players: Record<string, PlayerDto>;

    applyState: (state: SessionStateDto, sessionId: string) => void;
    applyMapLayoutUpdate: (dto: MapLayoutUpdateDto) => void;
    moveToken: (token: TokenDto) => void;
    removeToken: (tokenId: string) => void;
    addObject: (obj: MapObjectDto) => void;
    removeObject: (objId: string) => void;
    addPlayer: (player: PlayerDto) => void;
}

export const useGameStore = create<GameState>((set) => ({
    sessionId: null,
    myPlayerId: null,
    grid: { cellSize: 64, cols: 20, rows: 20, offsetX: 0, offsetY: 0 },
    tokens: {},
    objects: {},
    players: {},

    applyState: (state, sessionId) =>
        set({
            sessionId,
            myPlayerId: state.myPlayerId,
            grid: state.grid,
            tokens: Object.fromEntries(state.tokens.map((t) => [t.id, t])),
            objects: Object.fromEntries(state.objects.map((o) => [o.id, o])),
            players: Object.fromEntries(state.players.map((p) => [p.id, p])),
        }),

    // Применяется при MAP_UPDATED от DM — сетка, токены и объекты обновляются вместе
    applyMapLayoutUpdate: (dto) =>
        set((state) => ({
            grid: dto.grid,
            tokens: Object.fromEntries(dto.tokens.map((t) => [t.id, t])),
            // объекты могут отсутствовать в старых сообщениях
            objects: dto.objects
                ? Object.fromEntries(dto.objects.map((o) => [o.id, o]))
                : state.objects,
        })),

    moveToken: (token) =>
        set((state) => ({
            tokens: { ...state.tokens, [token.id]: token },
        })),

    removeToken: (tokenId) =>
        set((state) => {
            const tokens = { ...state.tokens };
            delete tokens[tokenId];
            return { tokens };
        }),

    addObject: (obj) =>
        set((state) => ({
            objects: { ...state.objects, [obj.id]: obj },
        })),

    removeObject: (objId) =>
        set((state) => {
            const objects = { ...state.objects };
            delete objects[objId];
            return { objects };
        }),

    addPlayer: (player) =>
        set((state) => ({
            players: { ...state.players, [player.id]: player },
        })),
}));