import React, { useRef, useCallback, useState, useMemo, useEffect } from 'react';
import {
    Stage, Layer, Rect, Line, Circle, Text, Group, Image as KonvaImage,
} from 'react-konva';
import { useGameStore } from '../store/gameStore';
import type { TokenDto, MapObjectDto, GridConfig, MicroLocationDto } from '../types/types';
import { wsClient } from '../net/wsClient';
import useImage from '../hooks/useImage';
import type Konva from 'konva';
import { normalizeAssetUrl } from '../utils/assetUrl';

function useRemoteImage(imageUrl: string | null) {
    return useImage(normalizeAssetUrl(imageUrl, wsClient.getServerBaseUrl()));
}

function hpColor(hp: number, maxHp: number): string {
    if (maxHp <= 0) return '#2ecc71';
    const ratio = hp / maxHp;
    if (ratio > 0.5) return '#2ecc71';
    if (ratio > 0.25) return '#f39c12';
    return '#e74c3c';
}


type AnyMap = Record<string, any>;

function getBool(v: any, def = false): boolean {
    if (typeof v === 'boolean') return v;
    if (v == null) return def;
    return String(v).toLowerCase() === 'true';
}

function getNum(v: any, def = 0): number {
    const n = typeof v === 'number' ? v : Number(v);
    return Number.isFinite(n) ? n : def;
}

function isNightMode(fogSettings: unknown): boolean {
    const fog = asMap(fogSettings);
    if (!fog) return false;

    const mode = fog.timeOfDay ?? fog.dayNight ?? fog.mode;
    if (typeof mode === 'string') {
        const normalized = mode.trim().toLowerCase();
        if (normalized === 'night' || normalized === 'dark') return true;
        if (normalized === 'day' || normalized === 'light') return false;
    }

    return getBool(fog.nightMode ?? fog.isNightMode ?? fog.night ?? fog.isNight ?? fog.darkness, false);
}

function resolveVisionRadius(token: TokenDto, fogSettings: unknown): number {
    const fog = asMap(fogSettings);
    const fallback = fog ? Math.max(0, Math.floor(getNum(fog.revealRadius, 6))) : 6;
    const night = isNightMode(fogSettings);
    const preferred = night ? getNum(token.nightVision, 0) : getNum(token.dayVision, 0);
    const alternate = night ? getNum(token.dayVision, 0) : getNum(token.nightVision, 0);
    const radius = preferred > 0 ? preferred : (alternate > 0 ? alternate : fallback);
    return Math.max(0, Math.floor(radius));
}

function asMap(v: any): AnyMap | null {
    return v && typeof v === 'object' && !Array.isArray(v) ? v as AnyMap : null;
}

function buildBlockedCells(grid: GridConfig, terrainLayer: unknown, wallLayer: unknown, objects: Record<string, MapObjectDto>, forSight = false) {
    const blocked = Array.from({ length: grid.rows }, () => Array<boolean>(grid.cols).fill(false));

    Object.values(objects).forEach((obj) => {
        if (!(forSight ? (obj.blocksSight ?? obj.blocksMovement) : obj.blocksMovement)) return;
        const w = Math.max(1, obj.width ?? 1);
        const h = Math.max(1, obj.height ?? 1);
        for (let r = obj.row; r < obj.row + h; r++) {
            if (r < 0 || r >= grid.rows) continue;
            for (let c = obj.col; c < obj.col + w; c++) {
                if (c < 0 || c >= grid.cols) continue;
                blocked[r][c] = true;
            }
        }
    });

    const terrain = asMap(terrainLayer);
    const cells = terrain?.cells;
    if (Array.isArray(cells)) {
        cells.forEach((cell) => {
            const m = asMap(cell);
            if (!m) return;
            const blocks = forSight ? getBool(m.blocksSight, getBool(m.blocksMovement, false)) : getBool(m.blocksMovement, false);
            if (!blocks) return;
            const col = Math.floor(getNum(m.col));
            const row = Math.floor(getNum(m.row));
            const w = Math.max(1, Math.floor(getNum(m.width, 1)));
            const h = Math.max(1, Math.floor(getNum(m.height, 1)));
            for (let r = row; r < row + h; r++) {
                if (r < 0 || r >= grid.rows) continue;
                for (let c = col; c < col + w; c++) {
                    if (c < 0 || c >= grid.cols) continue;
                    blocked[r][c] = true;
                }
            }
        });
    }

    const wall = asMap(wallLayer);
    const paths = wall?.paths;
    if (Array.isArray(paths)) {
        const cellSize = Math.max(1, grid.cellSize);
        const ox = grid.offsetX;
        const oy = grid.offsetY;
        paths.forEach((path) => {
            const pm = asMap(path);
            if (!pm) return;
            const blocks = forSight ? getBool(pm.blocksSight, getBool(pm.blocksMovement, true)) : getBool(pm.blocksMovement, true);
            if (!blocks) return;
            const thickness = Math.max(0.5, getNum(pm.thickness, 2.5));
            const expand = Math.max(0, Math.ceil(thickness / cellSize));
            const points = Array.isArray(pm.points) ? pm.points : [];
            let prev: {x:number,y:number} | null = null;
            for (const pt of points) {
                const mm = asMap(pt);
                if (!mm) continue;
                const curr = { x: getNum(mm.x), y: getNum(mm.y) };
                if (prev) markWallSegment(blocked, prev, curr, ox, oy, cellSize, expand);
                prev = curr;
            }
        });
    }

    return blocked;
}

function markWallSegment(blocked: boolean[][], a: {x:number,y:number}, b: {x:number,y:number}, ox: number, oy: number, cellSize: number, expand: number) {
    const cells = bresenhamCells(Math.floor((a.x - ox) / cellSize), Math.floor((a.y - oy) / cellSize), Math.floor((b.x - ox) / cellSize), Math.floor((b.y - oy) / cellSize));
    for (const cell of cells) {
        for (let r = cell.row - expand; r <= cell.row + expand; r++) {
            if (r < 0 || r >= blocked.length) continue;
            for (let c = cell.col - expand; c <= cell.col + expand; c++) {
                if (c < 0 || c >= blocked[r].length) continue;
                blocked[r][c] = true;
            }
        }
    }
}

function bresenhamCells(x0: number, y0: number, x1: number, y1: number) {
    const cells: Array<{col:number,row:number}> = [];
    let x = x0, y = y0;
    const dx = Math.abs(x1 - x0);
    const dy = Math.abs(y1 - y0);
    const sx = x0 < x1 ? 1 : -1;
    const sy = y0 < y1 ? 1 : -1;
    let err = dx - dy;
    for (;;) {
        cells.push({ col: x, row: y });
        if (x === x1 && y === y1) break;
        const e2 = 2 * err;
        if (e2 > -dy) { err -= dy; x += sx; }
        if (e2 < dx) { err += dx; y += sy; }
    }
    return cells;
}

function computeVisibleCells(grid: GridConfig, tokens: Record<string, TokenDto>, _myPlayerId: string | null, fogSettings: unknown, terrainLayer: unknown, wallLayer: unknown, objects: Record<string, MapObjectDto>) {
    const fog = asMap(fogSettings);
    const enabled = fog ? getBool(fog.enabled, true) : true;
    const revealFromTokens = fog ? getBool(fog.revealFromTokens, true) : true;
    const blockers = buildBlockedCells(grid, terrainLayer, wallLayer, objects, true);
    const visible = Array.from({ length: grid.rows }, () => Array<boolean>(grid.cols).fill(!enabled));

    if (!enabled) return visible;

    const sources = Object.values(tokens).filter((t) => revealFromTokens && (t.ownerId != null));
    if (sources.length === 0) return visible;

    for (const source of sources) {
        const gs = Math.max(1, source.gridSize ?? 1);
        const sx = source.col + Math.floor(gs / 2);
        const sy = source.row + Math.floor(gs / 2);
        const revealRadius = resolveVisionRadius(source, fogSettings);
        if (revealRadius <= 0) continue;
        const radiusSq = revealRadius * revealRadius;
        for (let row = Math.max(0, sy - revealRadius); row <= Math.min(grid.rows - 1, sy + revealRadius); row++) {
            for (let col = Math.max(0, sx - revealRadius); col <= Math.min(grid.cols - 1, sx + revealRadius); col++) {
                const dx = col - sx, dy = row - sy;
                if (dx * dx + dy * dy > radiusSq) continue;
                if (hasLineOfSight(sx, sy, col, row, blockers)) visible[row][col] = true;
            }
        }
    }

    return visible;
}

function hasLineOfSight(x0: number, y0: number, x1: number, y1: number, blocked: boolean[][]) {
    const cells = bresenhamCells(x0, y0, x1, y1);
    for (const cell of cells) {
        if ((cell.col === x0 && cell.row === y0) || (cell.col === x1 && cell.row === y1)) continue;
        if (cell.row >= 0 && cell.row < blocked.length && cell.col >= 0 && cell.col < blocked[cell.row].length && blocked[cell.row][cell.col]) return false;
    }
    return true;
}

function cellKey(row: number, col: number): string {
    return `${row}:${col}`;
}

function isAnyCellVisible(visible: boolean[][], col: number, row: number, width: number, height: number) {
    for (let r = row; r < row + height; r++) {
        if (r < 0 || r >= visible.length) continue;
        for (let c = col; c < col + width; c++) {
            if (c < 0 || c >= visible[r].length) continue;
            if (visible[r][c]) return true;
        }
    }
    return false;
}

function isAnyCellExplored(explored: Set<string>, col: number, row: number, width: number, height: number) {
    for (let r = row; r < row + height; r++) {
        for (let c = col; c < col + width; c++) {
            if (explored.has(cellKey(r, c))) return true;
        }
    }
    return false;
}

function findMicroLocationIdAtCell(microLocations: MicroLocationDto[], col: number, row: number): string | null {
    for (const zone of microLocations) {
        if (!zone) continue;
        const w = Math.max(1, zone.width ?? 1);
        const h = Math.max(1, zone.height ?? 1);
        if (col >= zone.col && col < zone.col + w && row >= zone.row && row < zone.row + h) {
            return zone.id;
        }
    }
    return null;
}

function findMicroLocationIdForToken(token: TokenDto, microLocations: MicroLocationDto[]): string | null {
    const gs = Math.max(1, token.gridSize ?? 1);
    for (let r = token.row; r < token.row + gs; r++) {
        for (let c = token.col; c < token.col + gs; c++) {
            const zoneId = findMicroLocationIdAtCell(microLocations, c, r);
            if (zoneId) return zoneId;
        }
    }
    return null;
}

function getActiveMicroLocationId(tokens: Record<string, TokenDto>, microLocations: MicroLocationDto[], myPlayerId: string | null): string | null {
    if (!myPlayerId) return null;
    for (const token of Object.values(tokens)) {
        if (token.ownerId !== myPlayerId) continue;
        const zoneId = findMicroLocationIdForToken(token, microLocations);
        if (zoneId) return zoneId;
    }
    return null;
}

function shouldRenderScene(entityZoneId: string | null, activeZoneId: string | null, isDm: boolean) {
    if (isDm) return true;
    if (!activeZoneId) return !entityZoneId;
    return entityZoneId === activeZoneId;
}

function cloneToken(token: TokenDto): TokenDto {
    return { ...token };
}

function cloneObject(obj: MapObjectDto): MapObjectDto {
    return { ...obj };
}

// ================================================================ TokenShape

const TokenShape: React.FC<{
    token: TokenDto;
    isMyToken: boolean;
    isDm: boolean;
    cellSize: number;
    offsetX: number;
    offsetY: number;
    onDragStart: () => void;
    onDragEnd: (e: any, token: TokenDto) => void;
}> = React.memo(({ token, isMyToken, isDm, cellSize, offsetX, offsetY, onDragStart, onDragEnd }) => {
    const gs = Math.max(1, token.gridSize ?? 1);

    // Group positioned at top-left of the token cell(s)
    const x = offsetX + token.col * cellSize;
    const y = offsetY + token.row * cellSize;
    const size = gs * cellSize;
    const cx = size / 2;
    const cy = size / 2;
    const radius = size / 2 - 4;

    const isNpc = token.ownerId === null;
    const canDrag = isMyToken || isDm;

    const [tokenImage] = useRemoteImage(token.imageUrl);
    const [hovered, setHovered] = useState(false);

    const borderColor = isMyToken ? '#f1c40f' : isNpc ? '#e74c3c' : '#4a90d9';
    const fillColor   = isMyToken ? '#c9a227' : isNpc ? '#c0392b' : '#2980b9';
    const fontSize    = Math.max(9, Math.floor(size / 7));
    const hpRatio     = token.maxHp > 0 ? Math.max(0, Math.min(1, token.hp / token.maxHp)) : 1;
    const barW        = size - 10;

    // HP visible only to owner of this token or DM
    const showHp = token.maxHp > 0 && (isDm || isMyToken);

    return (
        <Group
            x={x}
            y={y}
            draggable={canDrag}
            onDragStart={(e) => {
                if (!canDrag) { e.target.stopDrag(); return; }
                onDragStart();
            }}
            onDragEnd={(e) => onDragEnd(e, token)}
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
        >
            {/* Glow ring for own token */}
            {isMyToken && !tokenImage && (
                <Circle x={cx} y={cy} radius={radius + 3} fill="rgba(241,196,15,0.25)" />
            )}

            {/* Base circle */}
            {!tokenImage && (
                <Circle
                    x={cx} y={cy}
                    radius={radius}
                    fill={fillColor}
                    stroke={borderColor}
                    strokeWidth={isMyToken ? 2.5 : 1.5}
                    shadowColor={borderColor}
                    shadowBlur={isMyToken ? 14 : 6}
                    shadowOpacity={0.7}
                />
            )}
            {/* Token image clipped to circle */}
            {tokenImage && (
                <KonvaImage
                    image={tokenImage}
                    x={cx - radius} y={cy - radius}
                    width={radius * 2} height={radius * 2}
                    cornerRadius={radius}
                    opacity={0.95}
                    listening={false}
                />
            )}

            {/* Name */}
            <Text
                x={0}
                y={tokenImage ? cy + radius - fontSize - 4 : cy - fontSize / 2}
                width={size}
                align="center"
                text={token.name.length > 9 ? token.name.slice(0, 8) + '…' : token.name}
                fontSize={fontSize}
                fontStyle="bold"
                fill="#fff"
                shadowColor="rgba(0,0,0,0.95)"
                shadowBlur={4}
                listening={false}
            />


            {/* HP bar — only for owner / DM */}
            {showHp && (
                <>
                    <Rect x={5} y={size + 4} width={barW} height={5}
                          fill="#1a1a1a" cornerRadius={2} listening={false} />
                    <Rect x={5} y={size + 4} width={barW * hpRatio} height={5}
                          fill={hpColor(token.hp, token.maxHp)} cornerRadius={2} listening={false} />
                </>
            )}

            {/* Tooltip — HP shown only to owner / DM */}
            {hovered && (
                <Group x={size + 4} y={0}>
                    <Rect x={0} y={0} width={140} height={showHp ? 72 : 56}
                          fill="rgba(20,20,40,0.95)" stroke="#7f8c8d" strokeWidth={1}
                          cornerRadius={5} listening={false} />
                    <Text x={6} y={8} text={token.name}
                          fontSize={12} fontStyle="bold" fill="#ecf0f1" width={128} listening={false} />
                    {showHp && (
                        <Text x={6} y={24}
                              text={`HP: ${token.hp} / ${token.maxHp}`}
                              fontSize={11} fill="#2ecc71" listening={false} />
                    )}
                    <Text x={6} y={showHp ? 40 : 24}
                          text={`Размер: ${gs}×${gs}`} fontSize={11} fill="#bdc3c7" listening={false} />
                    <Text x={6} y={showHp ? 56 : 40}
                          text={token.ownerId ? 'Игрок' : 'NPC'} fontSize={11} fill="#bdc3c7" listening={false} />
                </Group>
            )}
        </Group>
    );
});
TokenShape.displayName = 'TokenShape';

// ================================================================ ObjectShape

const ObjectShape: React.FC<{
    obj: MapObjectDto;
    cellSize: number;
    offsetX: number;
    offsetY: number;
}> = React.memo(({ obj, cellSize, offsetX, offsetY }) => {
    const w = Math.max(1, obj.width ?? 1);
    const h = Math.max(1, obj.height ?? 1);

    const fullUrl = normalizeAssetUrl(obj.imageUrl, wsClient.getServerBaseUrl());
    const [objImage] = useImage(fullUrl);

    const px = offsetX + obj.col * cellSize;
    const py = offsetY + obj.row * cellSize;
    const pw = w * cellSize;
    const ph = h * cellSize;

    return (
        <Group x={px} y={py} listening={false}>
            {objImage ? (() => {
                // Fit image inside cell while preserving aspect ratio (cover = fill cell)
                const imgW = objImage.width;
                const imgH = objImage.height;
                const scale = Math.max(pw / imgW, ph / imgH);
                const drawW = imgW * scale;
                const drawH = imgH * scale;
                const drawX = (pw - drawW) / 2;
                const drawY = (ph - drawH) / 2;
                return (
                    <KonvaImage
                        image={objImage}
                        x={drawX} y={drawY}
                        width={drawW} height={drawH}
                        opacity={0.92}
                    />
                );
            })() : (
                <>
                    {/* Default object fill only when no image is provided */}
                    <Rect x={0} y={0} width={pw} height={ph} fill="#4a3728" />
                    <Rect x={0} y={0} width={pw} height={ph}
                          fill="transparent" stroke="rgba(90,45,12,0.8)" strokeWidth={1} />
                </>
            )}
        </Group>
    );
});
ObjectShape.displayName = 'ObjectShape';

// ================================================================ BattleMap

const BattleMap: React.FC = () => {
    const { grid, tokens, objects, myPlayerId, backgroundUrl, players, terrainLayer, wallLayer, fogSettings, microLocations } = useGameStore();
    const stageRef = useRef<Konva.Stage>(null);

    // FIX: use a React state for stageDraggable so JSX prop stays in sync
    const [stageDraggable, setStageDraggable] = useState(true);

    const myPlayer = myPlayerId ? players[myPlayerId] : null;
    const isDm     = myPlayer?.role === 'DM';

    const fullBgUrl = normalizeAssetUrl(backgroundUrl, wsClient.getServerBaseUrl());
    const [bgImage] = useImage(fullBgUrl);
    const visibleCells = useMemo(() => {
        if (!grid) return null;
        if (isDm) {
            return Array.from({ length: grid.rows }, () => Array<boolean>(grid.cols).fill(true));
        }
        return computeVisibleCells(grid, tokens, myPlayerId, fogSettings, terrainLayer, wallLayer, objects);
    }, [grid, tokens, myPlayerId, fogSettings, terrainLayer, wallLayer, objects, isDm]);

    const fogEnabled = !isDm && visibleCells !== null;
    const retainExploredCells = useMemo(() => {
        const fog = fogSettings && typeof fogSettings === 'object' ? (fogSettings as Record<string, any>) : null;
        return fog ? Boolean(fog.retainExploredCells ?? true) : true;
    }, [fogSettings]);

    const activeMicroLocationId = useMemo(() => getActiveMicroLocationId(tokens, microLocations, myPlayerId), [tokens, microLocations, myPlayerId]);

    const fogMemoryRef = useRef<{
        explored: Set<string>;
        tokens: Record<string, TokenDto>;
        objects: Record<string, MapObjectDto>;
    }>({ explored: new Set<string>(), tokens: {}, objects: {} });

    useEffect(() => {
        fogMemoryRef.current = { explored: new Set<string>(), tokens: {}, objects: {} };
    }, [backgroundUrl, grid?.cols, grid?.rows, terrainLayer, wallLayer, fogSettings]);

    useEffect(() => {
        if (!fogEnabled || !grid || !visibleCells) return;
        const memory = fogMemoryRef.current;
        if (!retainExploredCells) {
            memory.explored.clear();
            memory.tokens = {};
            memory.objects = {};
        }
        for (let r = 0; r < visibleCells.length; r++) {
            for (let c = 0; c < visibleCells[r].length; c++) {
                if (visibleCells[r][c]) memory.explored.add(cellKey(r, c));
            }
        }
        Object.values(tokens).forEach((token) => {
            const gs = Math.max(1, token.gridSize ?? 1);
            if (isAnyCellVisible(visibleCells, token.col, token.row, gs, gs)) {
                memory.tokens[token.id] = cloneToken(token);
            }
        });
        Object.values(objects).forEach((obj) => {
            const w = Math.max(1, obj.width ?? 1);
            const h = Math.max(1, obj.height ?? 1);
            if (isAnyCellVisible(visibleCells, obj.col, obj.row, w, h)) {
                memory.objects[obj.id] = cloneObject(obj);
            }
        });
    }, [fogEnabled, grid, visibleCells, tokens, objects, retainExploredCells]);

    const fogExplored = retainExploredCells ? fogMemoryRef.current.explored : new Set<string>();

    const renderObjects = useMemo(() => {
        const allObjects = Object.values(objects).filter((obj) => shouldRenderScene(obj.microLocationId ?? null, activeMicroLocationId, isDm));
        if (!fogEnabled || !visibleCells) return allObjects;
        if (!retainExploredCells) {
            return allObjects.filter((obj) => {
                const w = Math.max(1, obj.width ?? 1);
                const h = Math.max(1, obj.height ?? 1);
                return isAnyCellVisible(visibleCells, obj.col, obj.row, w, h);
            });
        }
        const memory = fogMemoryRef.current.objects;
        const result: MapObjectDto[] = [];
        allObjects.forEach((obj) => {
            const w = Math.max(1, obj.width ?? 1);
            const h = Math.max(1, obj.height ?? 1);
            if (isAnyCellVisible(visibleCells, obj.col, obj.row, w, h)) {
                result.push(obj);
                return;
            }
            const snap = memory[obj.id];
            if (snap && isAnyCellExplored(fogExplored, snap.col, snap.row, w, h)) {
                result.push(snap);
            }
        });
        return result;
    }, [objects, visibleCells, fogEnabled, fogExplored, retainExploredCells, activeMicroLocationId, isDm]);

    const renderTokens = useMemo(() => {
        const sceneTokens = Object.values(tokens).filter((token) => {
            const zoneId = findMicroLocationIdForToken(token, microLocations);
            return shouldRenderScene(zoneId, activeMicroLocationId, isDm);
        });
        if (!fogEnabled || !visibleCells) return sceneTokens;
        if (!retainExploredCells) {
            return sceneTokens.filter((token) => {
                const gs = Math.max(1, token.gridSize ?? 1);
                return isAnyCellVisible(visibleCells, token.col, token.row, gs, gs);
            });
        }
        const memory = fogMemoryRef.current.tokens;
        const result: TokenDto[] = [];
        sceneTokens.forEach((token) => {
            const gs = Math.max(1, token.gridSize ?? 1);
            if (isAnyCellVisible(visibleCells, token.col, token.row, gs, gs)) {
                result.push(token);
                return;
            }
            const snap = memory[token.id];
            if (snap && isAnyCellExplored(fogExplored, snap.col, snap.row, gs, gs)) {
                result.push(snap);
            }
        });
        return result;
    }, [tokens, microLocations, visibleCells, fogEnabled, fogExplored, retainExploredCells, activeMicroLocationId, isDm]);

    // FIX: set React state, not imperative Konva call, so re-renders respect it
    const handleTokenDragStart = useCallback(() => {
        setStageDraggable(false);
    }, []);

    const handleDragEnd = useCallback((e: any, token: TokenDto) => {
        setStageDraggable(true);

        if (!grid) return;
        const node = e.target;
        const gs   = Math.max(1, token.gridSize ?? 1);

        // node.x() / node.y() = top-left of the group after drag
        const rawX = node.x();
        const rawY = node.y();

        const newCol = Math.round((rawX - grid.offsetX) / grid.cellSize);
        const newRow = Math.round((rawY - grid.offsetY) / grid.cellSize);
        const clampedCol = Math.max(0, Math.min(newCol, grid.cols - gs));
        const clampedRow = Math.max(0, Math.min(newRow, grid.rows - gs));

        const movedBounds = {
            left: clampedCol,
            top: clampedRow,
            right: clampedCol + gs,
            bottom: clampedRow + gs,
        };

        const collidesWithToken = Object.values(tokens).some((other) => {
            if (other.id === token.id) return false;
            const otherSize = Math.max(1, other.gridSize ?? 1);
            return !(
                movedBounds.right <= other.col ||
                movedBounds.left >= other.col + otherSize ||
                movedBounds.bottom <= other.row ||
                movedBounds.top >= other.row + otherSize
            );
        });

        const collidesWithObject = Object.values(objects).some((obj) => {
            if (!obj.blocksMovement) return false;
            const objWidth = Math.max(1, obj.width ?? 1);
            const objHeight = Math.max(1, obj.height ?? 1);
            return !(
                movedBounds.right <= obj.col ||
                movedBounds.left >= obj.col + objWidth ||
                movedBounds.bottom <= obj.row ||
                movedBounds.top >= obj.row + objHeight
            );
        });

        if (collidesWithToken || collidesWithObject) {
            node.position({
                x: grid.offsetX + token.col * grid.cellSize,
                y: grid.offsetY + token.row * grid.cellSize,
            });
            return;
        }

        // Snap to grid
        node.position({
            x: grid.offsetX + clampedCol * grid.cellSize,
            y: grid.offsetY + clampedRow * grid.cellSize,
        });

        wsClient.send('/token.move', {
            tokenId: token.id,
            toCol: clampedCol,
            toRow: clampedRow,
        });
    }, [grid, tokens, objects]);

    if (!grid) {
        return (
            <div style={{
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                height: '100vh', color: '#9ca3af', fontSize: '18px', background: '#0f1117',
            }}>
                Подключитесь к сессии...
            </div>
        );
    }

    const gridPixelW = grid.cols * grid.cellSize;
    const gridPixelH = grid.rows * grid.cellSize;
    const stageW     = Math.max(window.innerWidth,  grid.offsetX + gridPixelW + 100);
    const stageH     = Math.max(window.innerHeight, grid.offsetY + gridPixelH + 100);

    const gridLines: React.ReactNode[] = [];
    for (let c = 0; c <= grid.cols; c++) {
        const lx = grid.offsetX + c * grid.cellSize;
        gridLines.push(
            <Line key={`v-${c}`}
                  points={[lx, grid.offsetY, lx, grid.offsetY + gridPixelH]}
                  stroke="rgba(255,255,255,0.12)" strokeWidth={0.5} />,
        );
    }
    for (let r = 0; r <= grid.rows; r++) {
        const ly = grid.offsetY + r * grid.cellSize;
        gridLines.push(
            <Line key={`h-${r}`}
                  points={[grid.offsetX, ly, grid.offsetX + gridPixelW, ly]}
                  stroke="rgba(255,255,255,0.12)" strokeWidth={0.5} />,
        );
    }

    return (
        <div style={{ background: '#0f1117', overflow: 'hidden', width: '100vw', height: '100vh' }}>
            <Stage
                ref={stageRef}
                width={stageW}
                height={stageH}
                // FIX: controlled via React state — not overwritten on re-render
                draggable={stageDraggable}
                onWheel={(e) => {
                    e.evt.preventDefault();
                    const stage = stageRef.current;
                    if (!stage) return;
                    const oldScale = stage.scaleX() || 1;
                    const pointer  = stage.getPointerPosition();
                    if (!pointer) return;
                    const mousePointTo = {
                        x: (pointer.x - stage.x()) / oldScale,
                        y: (pointer.y - stage.y()) / oldScale,
                    };
                    const direction = e.evt.deltaY > 0 ? -1 : 1;
                    const newScale  = Math.max(0.2, Math.min(4, oldScale * (1 + direction * 0.1)));
                    stage.scale({ x: newScale, y: newScale });
                    stage.position({
                        x: pointer.x - mousePointTo.x * newScale,
                        y: pointer.y - mousePointTo.y * newScale,
                    });
                    stage.batchDraw();
                }}
            >
                {/* Background */}
                <Layer>
                    <Rect x={0} y={0} width={stageW} height={stageH} fill="#0f1117" />
                    {bgImage ? (
                        <KonvaImage
                            image={bgImage}
                            x={grid.offsetX} y={grid.offsetY}
                            width={gridPixelW} height={gridPixelH}
                        />
                    ) : (
                        <Rect
                            x={grid.offsetX} y={grid.offsetY}
                            width={gridPixelW} height={gridPixelH}
                            fill="#1a2035"
                        />
                    )}
                </Layer>

                {/* Terrain / walls / fog */}
                <Layer listening={false}>
                    {Array.isArray((terrainLayer as any)?.cells) && (terrainLayer as any).cells.map((cell: any, idx: number) => {
                        const col = Math.floor(getNum(cell?.col));
                        const row = Math.floor(getNum(cell?.row));
                        const w = Math.max(1, Math.floor(getNum(cell?.width, 1)));
                        const h = Math.max(1, Math.floor(getNum(cell?.height, 1)));
                        const type = String(cell?.terrainType ?? 'grass');
                        const fill = type.includes('water') ? 'rgba(52,152,219,0.34)'
                            : type.includes('sand') ? 'rgba(241,196,15,0.18)'
                            : type.includes('stone') || type.includes('rock') ? 'rgba(149,165,166,0.20)'
                            : type.includes('dirt') ? 'rgba(139,69,19,0.18)'
                            : 'rgba(46,204,113,0.14)';
                        return <Rect key={`terrain-${idx}`} x={grid.offsetX + col * grid.cellSize} y={grid.offsetY + row * grid.cellSize} width={w * grid.cellSize} height={h * grid.cellSize} fill={fill} listening={false} />;
                    })}
                    {Array.isArray((wallLayer as any)?.paths) && (wallLayer as any).paths.map((path: any, idx: number) => {
                        const points = Array.isArray(path?.points) ? path.points : [];
                        const flat: number[] = [];
                        for (const pt of points) {
                            flat.push(getNum(pt?.x), getNum(pt?.y));
                        }
                        if (flat.length < 4) return null;
                        const thickness = Math.max(1.5, getNum(path?.thickness, 2.5));
                        return <Line key={`wall-${idx}`} points={flat} stroke={path?.blocksSight === false ? 'rgba(189,195,199,0.75)' : 'rgba(236,240,241,0.85)'} strokeWidth={thickness} lineCap="round" lineJoin="round" listening={false} />;
                    })}
                    {/* Unseen cells are left as the base map; explored memory is restored via token/object snapshots. */}
                </Layer>

                {/* Grid */}
                <Layer listening={false}>{gridLines}</Layer>

                {/* Objects */}
                <Layer>
                    {renderObjects.map((obj: MapObjectDto) => (
                        <ObjectShape
                            key={obj.id}
                            obj={obj}
                            cellSize={grid.cellSize}
                            offsetX={grid.offsetX}
                            offsetY={grid.offsetY}
                        />
                    ))}
                </Layer>

                {/* Tokens */}
                <Layer>
                    {renderTokens.map((token: TokenDto) => (
                        <TokenShape
                            key={token.id}
                            token={token}
                            isMyToken={token.ownerId === myPlayerId}
                            isDm={isDm}
                            cellSize={grid.cellSize}
                            offsetX={grid.offsetX}
                            offsetY={grid.offsetY}
                            onDragStart={handleTokenDragStart}
                            onDragEnd={handleDragEnd}
                        />
                    ))}
                </Layer>
            </Stage>
        </div>
    );
};

export default BattleMap;