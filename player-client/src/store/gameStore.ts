import { create } from 'zustand';
import type {
    GridConfig,
    InitiativeStateDto,
    MapLayoutUpdateDto,
    MapObjectDto,
    PlayerDto,
    SessionStateDto,
    TokenDto,
    VisibilityStateDto,
} from '../types/types';

function resolveBackgroundUrl(backgroundUrl: string | null | undefined): string | null {
    const direct = typeof backgroundUrl === 'string' ? backgroundUrl.trim() : '';
    return direct || null;
}

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
    visibility: VisibilityStateDto | null;
    terrainLayer: MapLayoutUpdateDto['terrainLayer'] | null;
    wallLayer: MapLayoutUpdateDto['wallLayer'] | null;

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
    visibility: null,
    terrainLayer: null,
    wallLayer: null,

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
            backgroundUrl: resolveBackgroundUrl(state.backgroundUrl ?? null),
            initiative: state.initiative ?? null,
            visibility: state.visibility ?? null,
            terrainLayer: state.terrainLayer ?? current.terrainLayer,
            wallLayer: state.wallLayer ?? current.wallLayer,
        })),

    applyMapLayoutUpdate: (dto) =>
        set((current) => ({
            grid: dto.grid,
            tokens: Object.fromEntries(dto.tokens.map((t) => [t.id, t])),
            objects: dto.objects ? Object.fromEntries(dto.objects.map((o) => [o.id, o])) : current.objects,
            backgroundUrl: resolveBackgroundUrl(dto.backgroundUrl ?? current.backgroundUrl),
            visibility: dto.visibility ?? current.visibility,
            terrainLayer: dto.terrainLayer ?? current.terrainLayer,
            wallLayer: dto.wallLayer ?? current.wallLayer,
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
