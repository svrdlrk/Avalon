import React, { useRef, useCallback, useState, useMemo, useEffect } from 'react';
import {
    Stage, Layer, Rect, Line, Circle, Text, Group, Image as KonvaImage,
} from 'react-konva';
import { useGameStore } from '../store/gameStore';
import type { JsonValue, TokenDto, MapObjectDto, GridConfig } from '../types/types';
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


function cellKey(row: number, col: number): string {
    return `${row}:${col}`;
}

type StageTransform = {
    x: number;
    y: number;
    scale: number;
};

type ViewBounds = {
    left: number;
    top: number;
    right: number;
    bottom: number;
};

function rectIntersects(a: ViewBounds, b: ViewBounds): boolean {
    return a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top;
}

function computeVisibleBounds(transform: StageTransform, viewport: { width: number; height: number }): ViewBounds {
    const scale = Math.max(0.01, transform.scale || 1);
    const left = -transform.x / scale;
    const top = -transform.y / scale;
    return {
        left,
        top,
        right: left + viewport.width / scale,
        bottom: top + viewport.height / scale,
    };
}

function itemBounds(x: number, y: number, width: number, height: number): ViewBounds {
    return { left: x, top: y, right: x + width, bottom: y + height };
}

type JsonRecord = Record<string, JsonValue>;

type WallSegment = {
    id: string;
    points: number[];
    thickness: number;
    opacity: number;
    blocksMovement: boolean;
    blocksSight: boolean;
};

function isRecord(value: JsonValue | null | undefined): value is JsonRecord {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function asArray(value: JsonValue | null | undefined): JsonValue[] {
    return Array.isArray(value) ? value : [];
}

function firstArray(...values: Array<JsonValue | null | undefined>): JsonValue[] {
    for (const value of values) {
        if (Array.isArray(value)) return value;
    }
    return [];
}

function readBoolean(value: JsonValue | null | undefined, fallback: boolean): boolean {
    if (typeof value === 'boolean') return value;
    if (typeof value === 'string') return value.trim().toLowerCase() === 'true';
    return fallback;
}

function readNumber(value: JsonValue | null | undefined, fallback: number): number {
    return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}

function createBlockedCells(grid: GridConfig): boolean[][] {
    return Array.from({ length: Math.max(0, grid.rows) }, () => Array<boolean>(Math.max(0, grid.cols)).fill(false));
}

function markRect(blocked: boolean[][], col: number, row: number, width: number, height: number) {
    for (let r = Math.max(0, row); r < Math.min(blocked.length, row + height); r++) {
        for (let c = Math.max(0, col); c < Math.min(blocked[r]?.length ?? 0, col + width); c++) {
            blocked[r][c] = true;
        }
    }
}

function lineCells(startCol: number, startRow: number, endCol: number, endRow: number): Array<{ col: number; row: number }> {
    const cells: Array<{ col: number; row: number }> = [];
    let x = startCol;
    let y = startRow;
    const dx = Math.abs(endCol - startCol);
    const dy = Math.abs(endRow - startRow);
    const sx = startCol < endCol ? 1 : -1;
    const sy = startRow < endRow ? 1 : -1;
    let err = dx - dy;

    while (true) {
        cells.push({ col: x, row: y });
        if (x === endCol && y === endRow) break;
        const e2 = 2 * err;
        if (e2 > -dy) {
            err -= dy;
            x += sx;
        }
        if (e2 < dx) {
            err += dx;
            y += sy;
        }
    }

    return cells;
}

function markSegment(blocked: boolean[][], grid: GridConfig, ax: number, ay: number, bx: number, by: number, expand: number) {
    const cellSize = Math.max(1, grid.cellSize);
    const startCol = Math.floor((ax - grid.offsetX) / cellSize);
    const startRow = Math.floor((ay - grid.offsetY) / cellSize);
    const endCol = Math.floor((bx - grid.offsetX) / cellSize);
    const endRow = Math.floor((by - grid.offsetY) / cellSize);

    for (const cell of lineCells(startCol, startRow, endCol, endRow)) {
        for (let row = cell.row - expand; row <= cell.row + expand; row++) {
            if (row < 0 || row >= blocked.length) continue;
            for (let col = cell.col - expand; col <= cell.col + expand; col++) {
                if (col < 0 || col >= (blocked[row]?.length ?? 0)) continue;
                blocked[row][col] = true;
            }
        }
    }
}

function buildTerrainBlockedCells(grid: GridConfig, terrainLayer: JsonValue | null | undefined, forSight: boolean): boolean[][] {
    const blocked = createBlockedCells(grid);
    if (!isRecord(terrainLayer)) return blocked;

    for (const cellValue of asArray(terrainLayer.cells)) {
        if (!isRecord(cellValue)) continue;
        const blocks = forSight
            ? readBoolean(cellValue.blocksSight, readBoolean(cellValue.blocksMovement, false))
            : readBoolean(cellValue.blocksMovement, false);
        if (!blocks) continue;

        markRect(
            blocked,
            Math.trunc(readNumber(cellValue.col, 0)),
            Math.trunc(readNumber(cellValue.row, 0)),
            Math.max(1, Math.trunc(readNumber(cellValue.width, 1))),
            Math.max(1, Math.trunc(readNumber(cellValue.height, 1))),
        );
    }

    return blocked;
}

function extractWallSegments(wallLayer: JsonValue | null | undefined): WallSegment[] {
    if (!isRecord(wallLayer)) return [];
    if (!readBoolean(wallLayer.visible, true)) return [];
    const layerOpacity = Math.max(0, Math.min(1, readNumber(wallLayer.opacity, 1)));
    const paths = firstArray(wallLayer.paths, wallLayer.walls, wallLayer.segments, wallLayer.polylines, wallLayer.lines);

    return paths.flatMap((pathValue, index) => {
        if (!isRecord(pathValue)) return [];
        if (!readBoolean(pathValue.visible, true)) return [];
        const points = firstArray(pathValue.points, pathValue.vertices, pathValue.coords, pathValue.pts);
        const pointPairs = points
            .filter(isRecord)
            .map((point) => ({
                x: readNumber(point.x, Number.NaN),
                y: readNumber(point.y, Number.NaN),
            }))
            .filter((point) => Number.isFinite(point.x) && Number.isFinite(point.y));

        if (pointPairs.length < 2) return [];
        return [{
            id: typeof pathValue.id === 'string' && pathValue.id.trim() ? pathValue.id : `wall-${index}`,
            points: pointPairs.flatMap((point) => [point.x, point.y]),
            thickness: Math.max(0.5, readNumber(pathValue.thickness, readNumber(wallLayer.defaultThickness, 2.5))),
            opacity: Math.max(0, Math.min(1, readNumber(pathValue.opacity, 1) * layerOpacity)),
            blocksMovement: readBoolean(pathValue.blocksMovement, readBoolean(wallLayer.defaultBlocksMovement, true)),
            blocksSight: readBoolean(pathValue.blocksSight, readBoolean(wallLayer.defaultBlocksSight, true)),
        }];
    });
}

function buildWallBlockedCells(grid: GridConfig, wallSegments: WallSegment[], forSight: boolean): boolean[][] {
    const blocked = createBlockedCells(grid);
    const cellSize = Math.max(1, grid.cellSize);

    for (const wall of wallSegments) {
        const blocks = forSight ? wall.blocksSight : wall.blocksMovement;
        if (!blocks || wall.points.length < 4) continue;
        const expand = Math.max(0, Math.ceil(wall.thickness / cellSize));
        for (let i = 0; i <= wall.points.length - 4; i += 2) {
            markSegment(blocked, grid, wall.points[i], wall.points[i + 1], wall.points[i + 2], wall.points[i + 3], expand);
        }
    }

    return blocked;
}

function mergeBlockedCells(left: boolean[][], right: boolean[][]): boolean[][] {
    const rows = Math.max(left.length, right.length);
    const merged = Array.from({ length: rows }, (_, row) => {
        const cols = Math.max(left[row]?.length ?? 0, right[row]?.length ?? 0);
        return Array<boolean>(cols).fill(false);
    });

    for (let row = 0; row < rows; row++) {
        for (let col = 0; col < merged[row].length; col++) {
            merged[row][col] = Boolean(left[row]?.[col] || right[row]?.[col]);
        }
    }

    return merged;
}

function intersectsBlocked(blocked: boolean[][], col: number, row: number, width: number, height: number): boolean {
    for (let r = row; r < row + height; r++) {
        if (r < 0 || r >= blocked.length) return true;
        for (let c = col; c < col + width; c++) {
            if (c < 0 || c >= (blocked[r]?.length ?? 0)) return true;
            if (blocked[r][c]) return true;
        }
    }
    return false;
}

function isBlockedCell(blocked: boolean[][], col: number, row: number): boolean {
    return row < 0 || row >= blocked.length || col < 0 || col >= (blocked[row]?.length ?? 0) || Boolean(blocked[row][col]);
}

function hasClearMovementPath(blocked: boolean[][], startCol: number, startRow: number, endCol: number, endRow: number): boolean {
    for (const cell of lineCells(startCol, startRow, endCol, endRow)) {
        if (cell.col === startCol && cell.row === startRow) continue;
        if (cell.col === endCol && cell.row === endRow) continue;
        if (isBlockedCell(blocked, cell.col, cell.row)) {
            return false;
        }
    }
    return true;
}

function orientation(ax: number, ay: number, bx: number, by: number, px: number, py: number): number {
    return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
}

function isOnSegment(ax: number, ay: number, bx: number, by: number, px: number, py: number): boolean {
    const epsilon = 0.000001;
    return Math.abs(orientation(ax, ay, bx, by, px, py)) <= epsilon
        && px >= Math.min(ax, bx) - epsilon
        && px <= Math.max(ax, bx) + epsilon
        && py >= Math.min(ay, by) - epsilon
        && py <= Math.max(ay, by) + epsilon;
}

function segmentsIntersect(ax: number, ay: number, bx: number, by: number, cx: number, cy: number, dx: number, dy: number): boolean {
    const o1 = orientation(ax, ay, bx, by, cx, cy);
    const o2 = orientation(ax, ay, bx, by, dx, dy);
    const o3 = orientation(cx, cy, dx, dy, ax, ay);
    const o4 = orientation(cx, cy, dx, dy, bx, by);

    if (((o1 > 0 && o2 < 0) || (o1 < 0 && o2 > 0))
        && ((o3 > 0 && o4 < 0) || (o3 < 0 && o4 > 0))) {
        return true;
    }

    return isOnSegment(ax, ay, bx, by, cx, cy)
        || isOnSegment(ax, ay, bx, by, dx, dy)
        || isOnSegment(cx, cy, dx, dy, ax, ay)
        || isOnSegment(cx, cy, dx, dy, bx, by);
}

function crossesBlockingWall(grid: GridConfig, wallSegments: WallSegment[], fromCol: number, fromRow: number, tokenSize: number, toCol: number, toRow: number): boolean {
    const size = Math.max(1, tokenSize);
    const ax = grid.offsetX + (fromCol + size / 2) * grid.cellSize;
    const ay = grid.offsetY + (fromRow + size / 2) * grid.cellSize;
    const bx = grid.offsetX + (toCol + size / 2) * grid.cellSize;
    const by = grid.offsetY + (toRow + size / 2) * grid.cellSize;

    for (const wall of wallSegments) {
        if (!wall.blocksMovement || wall.points.length < 4) continue;
        for (let i = 0; i <= wall.points.length - 4; i += 2) {
            if (segmentsIntersect(ax, ay, bx, by, wall.points[i], wall.points[i + 1], wall.points[i + 2], wall.points[i + 3])) {
                return true;
            }
        }
    }

    return false;
}

type TokenShapeProps = {
    token: TokenDto;
    isMyToken: boolean;
    isDm: boolean;
    canDrag: boolean;
    isSelected: boolean;
    cellSize: number;
    offsetX: number;
    offsetY: number;
    onSelect: (tokenId: string) => void;
    onDragStart: () => void;
    onDragEnd: (e: any, token: TokenDto) => void;
};

type ObjectShapeProps = {
    obj: MapObjectDto;
    cellSize: number;
    offsetX: number;
    offsetY: number;
};

function tokenShapePropsEqual(prev: TokenShapeProps, next: TokenShapeProps): boolean {
    const a = prev.token;
    const b = next.token;
    return prev.isMyToken === next.isMyToken
        && prev.isDm === next.isDm
        && prev.canDrag === next.canDrag
        && prev.isSelected === next.isSelected
        && prev.cellSize === next.cellSize
        && prev.offsetX === next.offsetX
        && prev.offsetY === next.offsetY
        && prev.onSelect === next.onSelect
        && prev.onDragStart === next.onDragStart
        && prev.onDragEnd === next.onDragEnd
        && a.id === b.id
        && a.name === b.name
        && a.col === b.col
        && a.row === b.row
        && a.ownerId === b.ownerId
        && a.hp === b.hp
        && a.maxHp === b.maxHp
        && a.gridSize === b.gridSize
        && a.imageUrl === b.imageUrl
        && a.dayVision === b.dayVision
        && a.nightVision === b.nightVision
        && a.facingAngleDeg === b.facingAngleDeg
        && a.microLocationId === b.microLocationId
        && a.blocksMovement === b.blocksMovement
        && a.blocksSight === b.blocksSight;
}

function objectShapePropsEqual(prev: ObjectShapeProps, next: ObjectShapeProps): boolean {
    const a = prev.obj;
    const b = next.obj;
    return prev.cellSize === next.cellSize
        && prev.offsetX === next.offsetX
        && prev.offsetY === next.offsetY
        && a.id === b.id
        && a.type === b.type
        && a.col === b.col
        && a.row === b.row
        && a.width === b.width
        && a.height === b.height
        && a.gridSize === b.gridSize
        && a.imageUrl === b.imageUrl
        && a.dayVision === b.dayVision
        && a.nightVision === b.nightVision
        && a.microLocationId === b.microLocationId
        && a.blocksMovement === b.blocksMovement
        && a.blocksSight === b.blocksSight;
}

// ================================================================ TokenShape

const TokenShape: React.FC<TokenShapeProps> = React.memo(({ token, isMyToken, isDm, canDrag, isSelected, cellSize, offsetX, offsetY, onSelect, onDragStart, onDragEnd }) => {
    const gs = Math.max(1, token.gridSize ?? 1);

    // Group positioned at top-left of the token cell(s)
    const x = offsetX + token.col * cellSize;
    const y = offsetY + token.row * cellSize;
    const size = gs * cellSize;
    const cx = size / 2;
    const cy = size / 2;
    const radius = size / 2 - 4;

    const isNpc = token.ownerId === null;
    const facingAngle = token.facingAngleDeg ?? 0;

    const [tokenImage] = useRemoteImage(token.imageUrl);
    const [hovered, setHovered] = useState(false);

    const borderColor = isMyToken ? '#f1c40f' : isNpc ? '#e74c3c' : '#4a90d9';
    const fillColor   = isMyToken ? '#c9a227' : isNpc ? '#c0392b' : '#2980b9';
    const fontSize    = Math.max(9, Math.floor(size / 7));
    const hpRatio     = token.maxHp > 0 ? Math.max(0, Math.min(1, token.hp / token.maxHp)) : 1;
    const barW        = size - 10;

    // HP visible to player-owned tokens or DM
    const showHp = token.maxHp > 0 && (isDm || token.ownerId !== null);

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
            onMouseDown={(e) => {
                e.cancelBubble = true;
                onSelect(token.id);
            }}
            onTouchStart={(e) => {
                e.cancelBubble = true;
                onSelect(token.id);
            }}
            onClick={(e) => {
                e.cancelBubble = true;
                onSelect(token.id);
            }}
            onTap={(e) => {
                e.cancelBubble = true;
                onSelect(token.id);
            }}
            onDblClick={(e) => {
                e.cancelBubble = true;
                onSelect(token.id);
            }}
        >
            {/* Invisible interactive hit area (critical for image-based tokens) */}
            <Circle
                x={cx}
                y={cy}
                radius={radius + 12}
                fill="rgba(0,0,0,0.01)"
                listening={true}
            />

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
                <Group x={cx} y={cy} rotation={facingAngle} listening={false}>
                    <KonvaImage
                        image={tokenImage}
                        x={-radius}
                        y={-radius}
                        width={radius * 2}
                        height={radius * 2}
                        cornerRadius={radius}
                        opacity={0.95}
                        listening={false}
                    />
                </Group>
            )}

            {isSelected && (
                <Circle
                    x={cx}
                    y={cy}
                    radius={radius + 7}
                    stroke="rgba(245, 158, 11, 0.95)"
                    strokeWidth={2}
                    dash={[8, 4]}
                    shadowColor="rgba(245, 158, 11, 0.55)"
                    shadowBlur={10}
                    shadowOpacity={0.7}
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

            {/* Tooltip — HP shown to player-owned tokens or DM */}
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
}, tokenShapePropsEqual);
TokenShape.displayName = 'TokenShape';

// ================================================================ ObjectShape

const ObjectShape: React.FC<ObjectShapeProps> = React.memo(({ obj, cellSize, offsetX, offsetY }) => {
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
}, objectShapePropsEqual);
ObjectShape.displayName = 'ObjectShape';


// ================================================================ BattleMap

const BattleMap: React.FC = () => {
    const grid = useGameStore((s) => s.grid);
    const tokens = useGameStore((s) => s.tokens);
    const objects = useGameStore((s) => s.objects);
    const players = useGameStore((s) => s.players);
    const myPlayerId = useGameStore((s) => s.myPlayerId);
    const viewerRole = useGameStore((s) => s.viewerRole);
    const initiative = useGameStore((s) => s.initiative);
    const selectedTokenId = useGameStore((s) => s.selectedTokenId);
    const setSelectedTokenId = useGameStore((s) => s.setSelectedTokenId);
    const clearSelection = useGameStore((s) => s.clearSelection);
    const backgroundUrl = useGameStore((s) => s.backgroundUrl);
    const visibility = useGameStore((s) => s.visibility);
    const terrainLayer = useGameStore((s) => s.terrainLayer);
    const wallLayer = useGameStore((s) => s.wallLayer);
    const stageRef = useRef<Konva.Stage>(null);
    const gridRef = useRef(grid);
    const tokensRef = useRef(tokens);
    const objectsRef = useRef(objects);
    const terrainLayerRef = useRef(terrainLayer);
    const wallLayerRef = useRef(wallLayer);
    const [stageTransform, setStageTransform] = useState<StageTransform>({ x: 0, y: 0, scale: 1 });
    const stageTransformFrameRef = useRef<number | null>(null);
    const stageTransformPendingRef = useRef<StageTransform | null>(null);
    const lastAutoFitSceneRef = useRef<string | null>(null);
    const [stageDraggable, setStageDraggable] = useState(true);
    const [viewport, setViewport] = useState(() => ({
        width: typeof window !== 'undefined' ? window.innerWidth : 1280,
        height: typeof window !== 'undefined' ? window.innerHeight : 720,
    }));

    useEffect(() => {
        gridRef.current = grid;
    }, [grid]);

    useEffect(() => {
        tokensRef.current = tokens;
    }, [tokens]);

    useEffect(() => {
        objectsRef.current = objects;
    }, [objects]);

    useEffect(() => {
        terrainLayerRef.current = terrainLayer;
    }, [terrainLayer]);

    useEffect(() => {
        wallLayerRef.current = wallLayer;
    }, [wallLayer]);

    useEffect(() => {
        const update = () => setViewport({
            width: window.innerWidth,
            height: window.innerHeight,
        });

        update();
        window.addEventListener('resize', update);
        window.addEventListener('orientationchange', update);

        return () => {
            window.removeEventListener('resize', update);
            window.removeEventListener('orientationchange', update);
        };
    }, []);

    useEffect(() => {
        return () => {
            if (stageTransformFrameRef.current != null) {
                window.cancelAnimationFrame(stageTransformFrameRef.current);
                stageTransformFrameRef.current = null;
            }
        };
    }, []);

    const myPlayer = myPlayerId ? players[myPlayerId] : null;
    const isDm = viewerRole === 'DM' || myPlayer?.role === 'DM';

    const fullBgUrl = normalizeAssetUrl(backgroundUrl, wsClient.getServerBaseUrl());
    const [bgImage] = useImage(fullBgUrl);

    const visibleCells = useMemo(() => {
        if (!grid) return null;
        if (isDm) {
            return Array.from({ length: grid.rows }, () => Array<boolean>(grid.cols).fill(true));
        }
        return visibility?.visibleCells ?? null;
    }, [grid, isDm, visibility]);

    const exploredCells = useMemo(() => {
        if (isDm) {
            return new Set<string>();
        }
        return new Set(visibility?.exploredCells ?? []);
    }, [isDm, visibility]);

    const visibleBounds = useMemo(() => computeVisibleBounds(stageTransform, viewport), [stageTransform, viewport]);
    const wallSegments = useMemo(() => extractWallSegments(wallLayer), [wallLayer]);
    const canControlToken = useCallback((token: TokenDto) => {
        if (viewerRole === 'DM') return true;
        if (viewerRole !== 'PLAYER' || token.ownerId !== myPlayerId) return false;
        const entries = initiative?.entries ?? [];
        if (entries.length === 0) return true;
        const current = entries[initiative?.currentIndex ?? -1];
        return current?.tokenId === token.id;
    }, [initiative, myPlayerId, viewerRole]);

    const commitStageTransform = useCallback((next: StageTransform) => {
        stageTransformPendingRef.current = next;
        if (stageTransformFrameRef.current != null) return;
        stageTransformFrameRef.current = window.requestAnimationFrame(() => {
            stageTransformFrameRef.current = null;
            const pending = stageTransformPendingRef.current;
            if (pending) {
                setStageTransform(pending);
            }
        });
    }, []);

    const syncStageTransformFromNode = useCallback(() => {
        const stage = stageRef.current;
        if (!stage) return;
        commitStageTransform({
            x: stage.x(),
            y: stage.y(),
            scale: stage.scaleX() || 1,
        });
    }, [commitStageTransform]);

    const clampScale = useCallback((value: number) => Math.max(0.22, Math.min(4, value)), []);

    const applyStageTransform = useCallback((scale: number, x: number, y: number) => {
        const stage = stageRef.current;
        if (!stage) return;
        const nextScale = clampScale(scale);
        stage.scale({ x: nextScale, y: nextScale });
        stage.position({ x, y });
        stage.batchDraw();
        commitStageTransform({ x, y, scale: nextScale });
    }, [clampScale, commitStageTransform]);

    const zoomAtCenter = useCallback((direction: 1 | -1) => {
        const stage = stageRef.current;
        if (!stage) return;
        const oldScale = stage.scaleX() || 1;
        const nextScale = clampScale(oldScale * (direction > 0 ? 1.12 : 0.89));
        const focusX = viewport.width / 2;
        const focusY = viewport.height / 2;
        const mousePointTo = {
            x: (focusX - stage.x()) / oldScale,
            y: (focusY - stage.y()) / oldScale,
        };
        applyStageTransform(
            nextScale,
            focusX - mousePointTo.x * nextScale,
            focusY - mousePointTo.y * nextScale,
        );
    }, [applyStageTransform, clampScale, viewport.height, viewport.width]);

    const resetView = useCallback(() => {
        applyStageTransform(1, 0, 0);
    }, [applyStageTransform]);

    const fitView = useCallback(() => {
        if (!grid) return;
        const gridPixelW = grid.cols * grid.cellSize;
        const gridPixelH = grid.rows * grid.cellSize;
        const availableW = Math.max(320, viewport.width - 24);
        const availableH = Math.max(320, viewport.height - 24);
        const scale = clampScale(Math.min(availableW / Math.max(1, gridPixelW), availableH / Math.max(1, gridPixelH), 1));
        const x = (viewport.width - gridPixelW * scale) / 2 - grid.offsetX * scale;
        const y = (viewport.height - gridPixelH * scale) / 2 - grid.offsetY * scale;
        applyStageTransform(scale, x, y);
    }, [applyStageTransform, clampScale, grid, viewport.height, viewport.width]);

    useEffect(() => {
        if (!grid || viewport.width <= 0 || viewport.height <= 0) return;
        const sceneKey = [
            backgroundUrl ?? '',
            grid.cols,
            grid.rows,
            grid.cellSize,
            grid.offsetX,
            grid.offsetY,
        ].join('|');
        if (lastAutoFitSceneRef.current === sceneKey) return;
        lastAutoFitSceneRef.current = sceneKey;
        const raf = window.requestAnimationFrame(() => fitView());
        return () => window.cancelAnimationFrame(raf);
    }, [backgroundUrl, fitView, grid, viewport.height, viewport.width]);

    const centerSelectedToken = useCallback(() => {
        if (!grid || !selectedTokenId) return;
        const stage = stageRef.current;
        const token = tokens[selectedTokenId];
        if (!stage || !token) return;

        const scale = clampScale(stage.scaleX() || 1);
        const tokenSize = Math.max(1, token.gridSize ?? 1);
        const centerX = (grid.offsetX + (token.col + tokenSize / 2) * grid.cellSize) * scale;
        const centerY = (grid.offsetY + (token.row + tokenSize / 2) * grid.cellSize) * scale;
        applyStageTransform(scale, viewport.width / 2 - centerX, viewport.height / 2 - centerY);
    }, [applyStageTransform, clampScale, grid, selectedTokenId, tokens, viewport.height, viewport.width]);

    useEffect(() => {
        const onCommand = (event: Event) => {
            const type = event.type;
            if (type === 'avalon-map:zoom-in') zoomAtCenter(1);
            if (type === 'avalon-map:zoom-out') zoomAtCenter(-1);
            if (type === 'avalon-map:reset') resetView();
            if (type === 'avalon-map:fit') fitView();
            if (type === 'avalon-map:center-selected') centerSelectedToken();
        };

        const onKeyDown = (event: KeyboardEvent) => {
            if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey) return;
            if (event.target instanceof HTMLInputElement || event.target instanceof HTMLTextAreaElement) return;

            if (event.key === '+' || event.key === '=') {
                event.preventDefault();
                zoomAtCenter(1);
            } else if (event.key === '-' || event.key === '_') {
                event.preventDefault();
                zoomAtCenter(-1);
            } else if (event.key === '0') {
                event.preventDefault();
                resetView();
            } else if (event.key.toLowerCase() === 'f') {
                event.preventDefault();
                fitView();
            } else if (event.key.toLowerCase() === 'c') {
                event.preventDefault();
                centerSelectedToken();
            }
        };

        window.addEventListener('avalon-map:zoom-in', onCommand);
        window.addEventListener('avalon-map:zoom-out', onCommand);
        window.addEventListener('avalon-map:reset', onCommand);
        window.addEventListener('avalon-map:fit', onCommand);
        window.addEventListener('avalon-map:center-selected', onCommand);
        window.addEventListener('keydown', onKeyDown);

        return () => {
            window.removeEventListener('avalon-map:zoom-in', onCommand);
            window.removeEventListener('avalon-map:zoom-out', onCommand);
            window.removeEventListener('avalon-map:reset', onCommand);
            window.removeEventListener('avalon-map:fit', onCommand);
            window.removeEventListener('avalon-map:center-selected', onCommand);
            window.removeEventListener('keydown', onKeyDown);
        };
    }, [centerSelectedToken, fitView, resetView, zoomAtCenter]);

    if (!grid) {
        return (
            <div className="battle-map battle-map--empty">
                <div className="battle-map__empty-card">
                    <div className="battle-map__empty-eyebrow">Awaiting session</div>
                    <h2>Подключитесь к сессии</h2>
                    <p>После соединения здесь появится карта, инициатива и тактические слои.</p>
                    <div className="battle-map__empty-hints">
                        <span className="hud-chip">Join session</span>
                        <span className="hud-chip">Load map</span>
                        <span className="hud-chip">Start combat</span>
                    </div>
                </div>
            </div>
        );
    }

    const gridPixelW = grid.cols * grid.cellSize;
    const gridPixelH = grid.rows * grid.cellSize;

    const gridLines = useMemo(() => {
        const lines: React.ReactNode[] = [];
        const left = visibleBounds.left;
        const top = visibleBounds.top;
        const right = visibleBounds.right;
        const bottom = visibleBounds.bottom;
        const startCol = Math.max(0, Math.floor((left - grid.offsetX) / grid.cellSize) - 1);
        const endCol = Math.min(grid.cols, Math.ceil((right - grid.offsetX) / grid.cellSize) + 1);
        const startRow = Math.max(0, Math.floor((top - grid.offsetY) / grid.cellSize) - 1);
        const endRow = Math.min(grid.rows, Math.ceil((bottom - grid.offsetY) / grid.cellSize) + 1);
        const lineTop = Math.max(grid.offsetY, top - grid.cellSize);
        const lineBottom = Math.min(grid.offsetY + gridPixelH, bottom + grid.cellSize);
        const lineLeft = Math.max(grid.offsetX, left - grid.cellSize);
        const lineRight = Math.min(grid.offsetX + gridPixelW, right + grid.cellSize);

        for (let c = startCol; c <= endCol; c++) {
            const lx = grid.offsetX + c * grid.cellSize;
            lines.push(
                <Line key={`v-${c}`} points={[lx, lineTop, lx, lineBottom]} stroke="rgba(255,255,255,0.12)" strokeWidth={0.5} />,
            );
        }
        for (let r = startRow; r <= endRow; r++) {
            const ly = grid.offsetY + r * grid.cellSize;
            lines.push(
                <Line key={`h-${r}`} points={[lineLeft, ly, lineRight, ly]} stroke="rgba(255,255,255,0.12)" strokeWidth={0.5} />,
            );
        }
        return lines;
    }, [grid.cellSize, grid.offsetX, grid.offsetY, grid.rows, grid.cols, gridPixelH, gridPixelW, visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom]);

    const fogOverlay = useMemo(() => {
        if (isDm) return [];

        const leftCol = Math.max(0, Math.floor((visibleBounds.left - grid.offsetX) / grid.cellSize) - 1);
        const rightCol = Math.min(grid.cols - 1, Math.ceil((visibleBounds.right - grid.offsetX) / grid.cellSize) + 1);
        const topRow = Math.max(0, Math.floor((visibleBounds.top - grid.offsetY) / grid.cellSize) - 1);
        const bottomRow = Math.min(grid.rows - 1, Math.ceil((visibleBounds.bottom - grid.offsetY) / grid.cellSize) + 1);
        const nodes: React.ReactNode[] = [];

        for (let row = topRow; row <= bottomRow; row++) {
            for (let col = leftCol; col <= rightCol; col++) {
                // Ключевое изменение: null visibleCells → все клетки закрыты туманом
                const visible = visibleCells != null
                    ? Boolean(visibleCells[row]?.[col])
                    : false;
                if (visible) continue;
                const explored = exploredCells.has(cellKey(row, col));
                const x = grid.offsetX + col * grid.cellSize;
                const y = grid.offsetY + row * grid.cellSize;
                nodes.push(
                    <Rect
                        key={`fog-${row}-${col}`}
                        x={x}
                        y={y}
                        width={grid.cellSize}
                        height={grid.cellSize}
                        fill={explored ? 'rgba(8, 12, 20, 0.45)' : 'rgba(3, 5, 10, 0.82)'}
                        listening={false}
                    />,
                );
            }
        }
        return nodes;
    }, [exploredCells, grid.cellSize, grid.cols, grid.offsetX, grid.offsetY, grid.rows, isDm, visibleCells, visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom]);

    const renderObjects = useMemo(() => {
        const allObjects = Object.values(objects);
        return allObjects.filter((obj) => {
            const w = Math.max(1, obj.width ?? 1) * grid.cellSize;
            const h = Math.max(1, obj.height ?? 1) * grid.cellSize;
            const x = grid.offsetX + obj.col * grid.cellSize;
            const y = grid.offsetY + obj.row * grid.cellSize;
            return rectIntersects(visibleBounds, itemBounds(x, y, w, h));
        });
    }, [objects, grid.cellSize, grid.offsetX, grid.offsetY, visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom]);

    const renderTokens = useMemo(() => {
        const sceneTokens = Object.values(tokens);
        return sceneTokens.filter((token) => {
            const gs = Math.max(1, token.gridSize ?? 1);
            const size = gs * grid.cellSize;
            const x = grid.offsetX + token.col * grid.cellSize;
            const y = grid.offsetY + token.row * grid.cellSize;
            return rectIntersects(visibleBounds, itemBounds(x, y, size, size));
        });
    }, [grid.cellSize, grid.offsetX, grid.offsetY, tokens, visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom]);

    const handleTokenDragStart = useCallback(() => {
        setStageDraggable(false);
    }, []);

    const handleDragEnd = useCallback((e: any, token: TokenDto) => {
        setStageDraggable(true);

        const gridValue = gridRef.current;
        if (!gridValue) return;
        const node = e.target;
        const gs = Math.max(1, token.gridSize ?? 1);

        const rawX = node.x();
        const rawY = node.y();

        const newCol = Math.round((rawX - gridValue.offsetX) / gridValue.cellSize);
        const newRow = Math.round((rawY - gridValue.offsetY) / gridValue.cellSize);
        const clampedCol = Math.max(0, Math.min(newCol, gridValue.cols - gs));
        const clampedRow = Math.max(0, Math.min(newRow, gridValue.rows - gs));

        const movedBounds = {
            left: clampedCol,
            top: clampedRow,
            right: clampedCol + gs,
            bottom: clampedRow + gs,
        };

        const currentTokens = tokensRef.current;
        const currentObjects = objectsRef.current;
        const currentWallSegments = extractWallSegments(wallLayerRef.current);
        const currentBlockedCells = mergeBlockedCells(
            buildTerrainBlockedCells(gridValue, terrainLayerRef.current, false),
            buildWallBlockedCells(gridValue, currentWallSegments, false),
        );

        const collidesWithToken = Object.values(currentTokens).some((other) => {
            if (other.id === token.id) return false;
            const otherSize = Math.max(1, other.gridSize ?? 1);
            return !(
                movedBounds.right <= other.col ||
                movedBounds.left >= other.col + otherSize ||
                movedBounds.bottom <= other.row ||
                movedBounds.top >= other.row + otherSize
            );
        });

        const collidesWithObject = Object.values(currentObjects).some((obj) => {
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

        const fromCenterCol = token.col + Math.floor(gs / 2);
        const fromCenterRow = token.row + Math.floor(gs / 2);
        const toCenterCol = clampedCol + Math.floor(gs / 2);
        const toCenterRow = clampedRow + Math.floor(gs / 2);
        const collidesWithBattleGeometry = intersectsBlocked(currentBlockedCells, clampedCol, clampedRow, gs, gs)
            || !hasClearMovementPath(currentBlockedCells, fromCenterCol, fromCenterRow, toCenterCol, toCenterRow)
            || crossesBlockingWall(gridValue, currentWallSegments, token.col, token.row, gs, clampedCol, clampedRow);

        if (collidesWithToken || collidesWithObject || collidesWithBattleGeometry) {
            node.position({
                x: gridValue.offsetX + token.col * gridValue.cellSize,
                y: gridValue.offsetY + token.row * gridValue.cellSize,
            });
            return;
        }

        const updatedToken: TokenDto = {
            ...token,
            col: clampedCol,
            row: clampedRow,
        };

        node.position({
            x: gridValue.offsetX + clampedCol * gridValue.cellSize,
            y: gridValue.offsetY + clampedRow * gridValue.cellSize,
        });
        useGameStore.getState().moveToken(updatedToken);

        wsClient.send('/token.move', {
            tokenId: token.id,
            toCol: clampedCol,
            toRow: clampedRow,
        });
    }, []);

    if (!grid) {
        return null;
    }

    return (
        <div className="battle-map">
            <Stage
                ref={stageRef}
                width={viewport.width}
                height={viewport.height}
                draggable={stageDraggable}
                onDragMove={syncStageTransformFromNode}
                onDragEnd={syncStageTransformFromNode}
                onWheel={(event) => {
                    event.evt.preventDefault();
                    zoomAtCenter(event.evt.deltaY > 0 ? -1 : 1);
                }}
                onMouseDown={(event) => {
                    if (event.target === event.target.getStage()) {
                        clearSelection();
                    }
                }}
            >
                <Layer>
                    {bgImage && (
                        <KonvaImage
                            image={bgImage}
                            x={grid.offsetX}
                            y={grid.offsetY}
                            width={gridPixelW}
                            height={gridPixelH}
                            listening={false}
                            opacity={0.96}
                        />
                    )}
                    {!bgImage && (
                        <Rect
                            x={grid.offsetX}
                            y={grid.offsetY}
                            width={gridPixelW}
                            height={gridPixelH}
                            fill="#142033"
                            listening={false}
                        />
                    )}
                    {wallSegments.map((wall) => (
                        <Line
                            key={wall.id}
                            points={wall.points}
                            stroke={wall.blocksSight ? 'rgba(184, 74, 63, 0.9)' : 'rgba(214, 169, 88, 0.78)'}
                            strokeWidth={Math.max(1.5, wall.thickness)}
                            opacity={wall.opacity}
                            lineCap="round"
                            lineJoin="round"
                            listening={false}
                        />
                    ))}
                    {fogOverlay}
                    {gridLines}
                    {renderObjects.map((obj) => (
                        <ObjectShape key={obj.id} obj={obj} cellSize={grid.cellSize} offsetX={grid.offsetX} offsetY={grid.offsetY} />
                    ))}
                    {renderTokens.map((token) => (
                        <TokenShape
                            key={token.id}
                            token={token}
                            isMyToken={token.ownerId != null && token.ownerId === myPlayerId}
                            isDm={isDm}
                            canDrag={canControlToken(token)}
                            isSelected={selectedTokenId === token.id}
                            cellSize={grid.cellSize}
                            offsetX={grid.offsetX}
                            offsetY={grid.offsetY}
                            onSelect={setSelectedTokenId}
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

