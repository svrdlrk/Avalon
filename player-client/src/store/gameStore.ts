import { create } from 'zustand';
import type {
    GridConfig,
    InitiativeStateDto,
    MapLayoutUpdateDto,
    MapObjectDto,
    MicroLocationDto,
    PlayerDto,
    SessionStateDto,
    TokenDto,
} from '../types/types';

interface GameState {
    sessionId:     string | null;
    myPlayerId:    string | null;
    grid:          GridConfig;
    tokens:        Record<string, TokenDto>;
    objects:       Record<string, MapObjectDto>;
    players:       Record<string, PlayerDto>;
    backgroundUrl: string | null;
    initiative:    InitiativeStateDto | null;
    referenceOverlayLayer: unknown | null;
    terrainLayer: unknown | null;
    wallLayer: unknown | null;
    fogSettings: unknown | null;
    microLocations: MicroLocationDto[];
    assetPackIds: string[];

    applyState:           (state: SessionStateDto, sessionId: string) => void;
    applyMapLayoutUpdate: (dto: MapLayoutUpdateDto) => void;
    moveToken:            (token: TokenDto)  => void;
    removeToken:          (tokenId: string)  => void;
    addObject:            (obj: MapObjectDto) => void;
    removeObject:         (objId: string)    => void;
    addPlayer:            (player: PlayerDto) => void;
    removePlayer:         (playerId: string) => void;
    setBackground:        (url: string | null) => void;
    setInitiative:        (state: InitiativeStateDto | null) => void;
}

export const useGameStore = create<GameState>((set) => ({
    sessionId:     null,
    myPlayerId:    null,
    grid:          { cellSize: 64, cols: 20, rows: 20, offsetX: 0, offsetY: 0 },
    tokens:        {},
    objects:       {},
    players:       {},
    backgroundUrl: null,
    initiative:    null,
    referenceOverlayLayer: null,
    terrainLayer: null,
    wallLayer: null,
    fogSettings: null,
    microLocations: [],
    assetPackIds: [],

    applyState: (state, sessionId) =>
        set({
            sessionId,
            myPlayerId:    state.myPlayerId,
            grid:          state.grid,
            tokens:        Object.fromEntries(state.tokens.map((t) => [t.id, t])),
            objects:       Object.fromEntries(state.objects.map((o) => [o.id, o])),
            players:       Object.fromEntries(state.players.map((p) => [p.id, p])),
            backgroundUrl: state.backgroundUrl ?? null,
            initiative:    state.initiative    ?? null,
            referenceOverlayLayer: state.referenceOverlayLayer ?? null,
            terrainLayer: state.terrainLayer ?? null,
            wallLayer: state.wallLayer ?? null,
            fogSettings: state.fogSettings ?? null,
            microLocations: state.microLocations ?? [],
            assetPackIds: state.assetPackIds ?? [],
        }),

    applyMapLayoutUpdate: (dto) =>
        set((s) => ({
            grid:          dto.grid,
            tokens:        Object.fromEntries(dto.tokens.map((t) => [t.id, t])),
            objects:       dto.objects
                ? Object.fromEntries(dto.objects.map((o) => [o.id, o]))
                : s.objects,
            backgroundUrl: dto.backgroundUrl ?? s.backgroundUrl,
            referenceOverlayLayer: dto.referenceOverlayLayer ?? s.referenceOverlayLayer,
            terrainLayer: dto.terrainLayer ?? s.terrainLayer,
            wallLayer: dto.wallLayer ?? s.wallLayer,
            fogSettings: dto.fogSettings ?? s.fogSettings,
            microLocations: dto.microLocations ?? s.microLocations,
            assetPackIds: dto.assetPackIds ?? s.assetPackIds,
        })),

    moveToken: (token) =>
        set((s) => ({ tokens: { ...s.tokens, [token.id]: token } })),

    removeToken: (tokenId) =>
        set((s) => {
            const tokens = { ...s.tokens };
            delete tokens[tokenId];
            return { tokens };
        }),

    addObject: (obj) =>
        set((s) => ({ objects: { ...s.objects, [obj.id]: obj } })),

    removeObject: (objId) =>
        set((s) => {
            const objects = { ...s.objects };
            delete objects[objId];
            return { objects };
        }),

    addPlayer: (player) =>
        set((s) => ({ players: { ...s.players, [player.id]: player } })),

    removePlayer: (playerId) =>
        set((s) => {
            const players = { ...s.players };
            delete players[playerId];
            return { players };
        }),

    setBackground:  (url)   => set({ backgroundUrl: url }),
    setInitiative:  (state) => set({ initiative: state }),
}));