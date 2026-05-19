import { create } from 'zustand';
import type {
    GridConfig,
    InitiativeStateDto,
    JsonValue,
    MapLayoutUpdateDto,
    MapObjectDto,
    MicroLocationDto,
    PlayerDto,
    SessionStateDto,
    TokenDto,
    VisibilityStateDto,
    VisibilityShareSuggestionDto,
} from '../types/types';

interface GameState {
    sessionId: string | null;
    myPlayerId: string | null;
    selectedTokenId: string | null;
    grid: GridConfig;
    tokens: Record<string, TokenDto>;
    objects: Record<string, MapObjectDto>;
    players: Record<string, PlayerDto>;
    backgroundUrl: string | null;
    initiative: InitiativeStateDto | null;
    referenceOverlayLayer: JsonValue | null;
    terrainLayer: JsonValue | null;
    wallLayer: JsonValue | null;
    fogSettings: JsonValue | null;
    visibility: VisibilityStateDto | null;
    visibilityShareSuggestions: VisibilityShareSuggestionDto[];
    microLocations: MicroLocationDto[];
    assetPackIds: string[];

    applyState: (state: SessionStateDto, sessionId: string) => void;
    applyMapLayoutUpdate: (dto: MapLayoutUpdateDto) => void;
    moveToken: (token: TokenDto) => void;
    removeToken: (tokenId: string) => void;
    addObject: (obj: MapObjectDto) => void;
    removeObject: (objId: string) => void;
    addPlayer: (player: PlayerDto) => void;
    removePlayer: (playerId: string) => void;
    setBackground: (url: string | null) => void;
    setInitiative: (state: InitiativeStateDto | null) => void;
    setSelectedTokenId: (tokenId: string | null) => void;
    clearSelection: () => void;
}

export const useGameStore = create<GameState>((set) => ({
    sessionId: null,
    myPlayerId: null,
    selectedTokenId: null,
    grid: { cellSize: 64, cols: 20, rows: 20, offsetX: 0, offsetY: 0 },
    tokens: {},
    objects: {},
    players: {},
    backgroundUrl: null,
    initiative: null,
    referenceOverlayLayer: null,
    terrainLayer: null,
    wallLayer: null,
    fogSettings: null,
    visibility: null,
    visibilityShareSuggestions: [],
    microLocations: [],
    assetPackIds: [],

    applyState: (state, sessionId) =>
        set((current) => ({
            sessionId,
            myPlayerId: state.myPlayerId,
            selectedTokenId:
                current.selectedTokenId && state.tokens.some((token) => token.id === current.selectedTokenId)
                    ? current.selectedTokenId
                    : null,
            grid: state.grid,
            tokens: Object.fromEntries(state.tokens.map((t) => [t.id, t])),
            objects: Object.fromEntries(state.objects.map((o) => [o.id, o])),
            players: Object.fromEntries(state.players.map((p) => [p.id, p])),
            backgroundUrl: state.backgroundUrl ?? null,
            initiative: state.initiative ?? null,
            referenceOverlayLayer: state.referenceOverlayLayer ?? null,
            terrainLayer: state.terrainLayer ?? null,
            wallLayer: state.wallLayer ?? null,
            fogSettings: state.fogSettings ?? null,
            visibility: state.visibility ?? null,
            visibilityShareSuggestions: state.visibilityShareSuggestions ?? [],
            microLocations: state.microLocations ?? [],
            assetPackIds: state.assetPackIds ?? [],
        })),

    applyMapLayoutUpdate: (dto) =>
        set((current) => ({
            grid: dto.grid,
            tokens: Object.fromEntries(dto.tokens.map((t) => [t.id, t])),
            objects: dto.objects ? Object.fromEntries(dto.objects.map((o) => [o.id, o])) : current.objects,
            backgroundUrl: dto.backgroundUrl ?? current.backgroundUrl,
            referenceOverlayLayer: dto.referenceOverlayLayer ?? current.referenceOverlayLayer,
            terrainLayer: dto.terrainLayer ?? current.terrainLayer,
            wallLayer: dto.wallLayer ?? current.wallLayer,
            fogSettings: dto.fogSettings ?? current.fogSettings,
            visibility: dto.visibility ?? current.visibility,
            visibilityShareSuggestions: current.visibilityShareSuggestions,
            microLocations: dto.microLocations ?? current.microLocations,
            assetPackIds: dto.assetPackIds ?? current.assetPackIds,
        })),

    moveToken: (token) =>
        set((current) => ({
            tokens: { ...current.tokens, [token.id]: token },
            selectedTokenId: current.selectedTokenId === token.id ? token.id : current.selectedTokenId,
        })),

    removeToken: (tokenId) =>
        set((current) => {
            const tokens = { ...current.tokens };
            delete tokens[tokenId];
            return {
                tokens,
                selectedTokenId: current.selectedTokenId === tokenId ? null : current.selectedTokenId,
            };
        }),

    addObject: (obj) => set((current) => ({ objects: { ...current.objects, [obj.id]: obj } })),

    removeObject: (objId) =>
        set((current) => {
            const objects = { ...current.objects };
            delete objects[objId];
            return { objects };
        }),

    addPlayer: (player) => set((current) => ({ players: { ...current.players, [player.id]: player } })),

    removePlayer: (playerId) =>
        set((current) => {
            const players = { ...current.players };
            delete players[playerId];
            return { players };
        }),

    setBackground: (url) => set({ backgroundUrl: url }),
    setInitiative: (initiative) => set({ initiative }),
    setSelectedTokenId: (tokenId) => set({ selectedTokenId: tokenId }),
    clearSelection: () => set({ selectedTokenId: null }),
}));
