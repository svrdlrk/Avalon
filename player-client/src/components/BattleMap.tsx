import React, { useRef, useCallback, useState, useMemo, useEffect } from 'react';
import {
    Stage, Layer, Rect, Line, Circle, Text, Group, Image as KonvaImage,
} from 'react-konva';
import { useGameStore } from '../store/gameStore';
import type { TokenDto, MapObjectDto, VisibilityStateDto } from '../types/types';
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


function getNum(v: any, def = 0): number {
    const n = typeof v === 'number' ? v : Number(v);
    return Number.isFinite(n) ? n : def;
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

function cloneToken(token: TokenDto): TokenDto {
    return { ...token };
}

function cloneObject(obj: MapObjectDto): MapObjectDto {
    return { ...obj };
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

type TokenShapeProps = {
    token: TokenDto;
    isMyToken: boolean;
    isDm: boolean;
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

const TokenShape: React.FC<TokenShapeProps> = React.memo(({ token, isMyToken, isDm, isSelected, cellSize, offsetX, offsetY, onSelect, onDragStart, onDragEnd }) => {
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
    const myPlayerId = useGameStore((s) => s.myPlayerId);
    const selectedTokenId = useGameStore((s) => s.selectedTokenId);
    const setSelectedTokenId = useGameStore((s) => s.setSelectedTokenId);
    const handleSelectToken = useCallback((tokenId: string) => {
        setSelectedTokenId(tokenId);
    }, [setSelectedTokenId]);
    const gridRef = useRef(grid);
    const tokensRef = useRef(tokens);
    const objectsRef = useRef(objects);

    useEffect(() => {
        gridRef.current = grid;
    }, [grid]);

    useEffect(() => {
        tokensRef.current = tokens;
    }, [tokens]);

    useEffect(() => {
        objectsRef.current = objects;
    }, [objects]);
    const clearSelection = useGameStore((s) => s.clearSelection);
    const backgroundUrl = useGameStore((s) => s.backgroundUrl);
    const players = useGameStore((s) => s.players);
    const terrainLayer = useGameStore((s) => s.terrainLayer);
    const wallLayer = useGameStore((s) => s.wallLayer);
    const fogSettings = useGameStore((s) => s.fogSettings);
    const visibility = useGameStore((s) => s.visibility);
    const stageRef = useRef<Konva.Stage>(null);
    const [stageTransform, setStageTransform] = useState<StageTransform>({ x: 0, y: 0, scale: 1 });
    const stageTransformFrameRef = useRef<number | null>(null);
    const stageTransformPendingRef = useRef<StageTransform | null>(null);
    const lastAutoFitSceneRef = useRef<string | null>(null);

    const [viewport, setViewport] = useState(() => ({
        width: typeof window !== 'undefined' ? window.innerWidth : 1280,
        height: typeof window !== 'undefined' ? window.innerHeight : 720,
    }));

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

    // FIX: use a React state for stageDraggable so JSX prop stays in sync
    const [stageDraggable, setStageDraggable] = useState(true);

    const commitStageTransform = useCallback((next: StageTransform) => {
        stageTransformPendingRef.current = next;
        if (stageTransformFrameRef.current != null) {
            return;
        }
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

    const myPlayer = myPlayerId ? players[myPlayerId] : null;
    const isDm     = myPlayer?.role === 'DM';

    const fullBgUrl = normalizeAssetUrl(backgroundUrl, wsClient.getServerBaseUrl());
    const [bgImage] = useImage(fullBgUrl);
    const serverVisibility: VisibilityStateDto | null = !isDm ? (visibility ?? null) : null;
    const visibleCells = useMemo(() => {
        if (serverVisibility?.visibleCells) return serverVisibility.visibleCells;
        if (!grid) return null;
        if (isDm) {
            return Array.from({ length: grid.rows }, () => Array<boolean>(grid.cols).fill(true));
        }
        return null;
    }, [serverVisibility, grid, isDm]);

    const visibleBounds = useMemo(() => computeVisibleBounds(stageTransform, viewport), [stageTransform, viewport]);

    const fogEnabled = !isDm;
    const retainExploredCells = useMemo(() => {
        const fog = fogSettings && typeof fogSettings === 'object' ? (fogSettings as Record<string, any>) : null;
        return fog ? Boolean(fog.retainExploredCells ?? true) : true;
    }, [fogSettings]);

    const fogMemoryRef = useRef<{
        explored: Set<string>;
        tokens: Record<string, TokenDto>;
        objects: Record<string, MapObjectDto>;
    }>({ explored: new Set<string>(), tokens: {}, objects: {} });

    useEffect(() => {
        if (serverVisibility) return;
        fogMemoryRef.current = { explored: new Set<string>(), tokens: {}, objects: {} };
    }, [backgroundUrl, grid?.cols, grid?.rows, terrainLayer, wallLayer, fogSettings, serverVisibility]);

    useEffect(() => {
        if (serverVisibility) return;
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
    }, [fogEnabled, grid, visibleCells, tokens, objects, retainExploredCells, serverVisibility]);

    const fogExplored = serverVisibility ? new Set(serverVisibility.exploredCells ?? []) : new Set<string>();

    const renderObjects = useMemo(() => {
        const allObjects = Object.values(objects);
        const intersectVisible = (obj: MapObjectDto) => {
            const w = Math.max(1, obj.width ?? 1) * grid.cellSize;
            const h = Math.max(1, obj.height ?? 1) * grid.cellSize;
            const x = grid.offsetX + obj.col * grid.cellSize;
            const y = grid.offsetY + obj.row * grid.cellSize;
            return rectIntersects(visibleBounds, itemBounds(x, y, w, h));
        };
        if (isDm) return allObjects.filter(intersectVisible);
        if (!visibleCells || !serverVisibility) return [];
        if (serverVisibility) {
            return allObjects.filter((obj) => {
                const w = Math.max(1, obj.width ?? 1);
                const h = Math.max(1, obj.height ?? 1);
                return intersectVisible(obj) && isAnyCellVisible(visibleCells, obj.col, obj.row, w, h);
            });
        }
        if (!retainExploredCells) {
            return allObjects.filter((obj) => {
                const w = Math.max(1, obj.width ?? 1);
                const h = Math.max(1, obj.height ?? 1);
                return intersectVisible(obj) && isAnyCellVisible(visibleCells, obj.col, obj.row, w, h);
            });
        }
        const memory = fogMemoryRef.current.objects;
        const result: MapObjectDto[] = [];
        allObjects.forEach((obj) => {
            const w = Math.max(1, obj.width ?? 1);
            const h = Math.max(1, obj.height ?? 1);
            const onScreen = intersectVisible(obj);
            if (onScreen && isAnyCellVisible(visibleCells, obj.col, obj.row, w, h)) {
                result.push(obj);
                return;
            }
            const snap = memory[obj.id];
            if (snap && isAnyCellExplored(fogExplored, snap.col, snap.row, w, h)) {
                if (onScreen || isAnyCellVisible(visibleCells, snap.col, snap.row, w, h)) {
                    result.push(snap);
                }
            }
        });
        return result;
    }, [objects, visibleCells, fogExplored, retainExploredCells, isDm, serverVisibility, grid.cellSize, grid.offsetX, grid.offsetY, grid.cols, grid.rows, visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom]);

    const renderTokens = useMemo(() => {
        const sceneTokens = Object.values(tokens);
        const intersectVisible = (token: TokenDto) => {
            const gs = Math.max(1, token.gridSize ?? 1);
            const size = gs * grid.cellSize;
            const x = grid.offsetX + token.col * grid.cellSize;
            const y = grid.offsetY + token.row * grid.cellSize;
            return rectIntersects(visibleBounds, itemBounds(x, y, size, size));
        };
        if (isDm) return sceneTokens.filter(intersectVisible);
        if (!visibleCells || !serverVisibility) return [];

        const isOwnedByMe = (token: TokenDto) => token.ownerId != null && token.ownerId === myPlayerId;

        if (serverVisibility) {
            return sceneTokens.filter((token) => {
                const gs = Math.max(1, token.gridSize ?? 1);
                return isOwnedByMe(token) || isAnyCellVisible(visibleCells, token.col, token.row, gs, gs);
            });
        }
        if (!retainExploredCells) {
            return sceneTokens.filter((token) => {
                const gs = Math.max(1, token.gridSize ?? 1);
                return isOwnedByMe(token) || isAnyCellVisible(visibleCells, token.col, token.row, gs, gs);
            });
        }
        const memory = fogMemoryRef.current.tokens;
        const result: TokenDto[] = [];
        sceneTokens.forEach((token) => {
            const gs = Math.max(1, token.gridSize ?? 1);
            if (isOwnedByMe(token) || isAnyCellVisible(visibleCells, token.col, token.row, gs, gs)) {
                result.push(token);
                return;
            }
            const snap = memory[token.id];
            if (snap && isAnyCellExplored(fogExplored, snap.col, snap.row, gs, gs)) {
                result.push(snap);
            }
        });
        return result;
    }, [tokens, visibleCells, fogEnabled, fogExplored, retainExploredCells, isDm, serverVisibility, myPlayerId, grid.cellSize, grid.offsetX, grid.offsetY, grid.cols, grid.rows, visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom]);

    // FIX: set React state, not imperative Konva call, so re-renders respect it
    const handleTokenDragStart = useCallback(() => {
        setStageDraggable(false);
    }, []);

    const handleDragEnd = useCallback((e: any, token: TokenDto) => {
        setStageDraggable(true);

        const gridValue = gridRef.current;
        if (!gridValue) return;
        const node = e.target;
        const gs   = Math.max(1, token.gridSize ?? 1);

        // node.x() / node.y() = top-left of the group after drag
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

        if (collidesWithToken || collidesWithObject) {
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

        // Snap both the Konva node and the React store so the player view
        // updates immediately while the server broadcast catches up.
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


    const clampScale = (value: number) => Math.max(0.22, Math.min(4, value));

    const applyStageTransform = useCallback((scale: number, x: number, y: number) => {
        const stage = stageRef.current;
        if (!stage) return;

        const nextScale = clampScale(scale);
        stage.scale({ x: nextScale, y: nextScale });
        stage.position({ x, y });
        stage.batchDraw();
        commitStageTransform({ x, y, scale: nextScale });
    }, [commitStageTransform]);

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
    }, [applyStageTransform, viewport.height, viewport.width]);

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
    }, [applyStageTransform, grid, viewport.height, viewport.width]);

    useEffect(() => {
        if (!grid || viewport.width <= 0 || viewport.height <= 0) return;
        const sceneKey = [
            backgroundUrl ?? '',
            grid.cols,
            grid.rows,
            grid.cellSize,
            grid.offsetX,
            grid.offsetY,
            terrainLayer ? 'terrain' : 'no-terrain',
            wallLayer ? 'walls' : 'no-walls',
        ].join('|');
        if (lastAutoFitSceneRef.current === sceneKey) return;
        lastAutoFitSceneRef.current = sceneKey;
        const raf = window.requestAnimationFrame(() => fitView());
        return () => window.cancelAnimationFrame(raf);
    }, [backgroundUrl, fitView, grid, terrainLayer, wallLayer, viewport.height, viewport.width]);

    const centerSelectedToken = useCallback(() => {
        if (!grid || !selectedTokenId) return;
        const stage = stageRef.current;
        const token = tokens[selectedTokenId];
        if (!stage || !token) return;

        const scale = clampScale(stage.scaleX() || 1);
        const tokenSize = Math.max(1, token.gridSize ?? 1);
        const centerX = (grid.offsetX + (token.col + tokenSize / 2) * grid.cellSize) * scale;
        const centerY = (grid.offsetY + (token.row + tokenSize / 2) * grid.cellSize) * scale;
        const x = viewport.width / 2 - centerX;
        const y = viewport.height / 2 - centerY;
        applyStageTransform(scale, x, y);
    }, [applyStageTransform, grid, selectedTokenId, tokens, viewport.height, viewport.width]);

    useEffect(() => {
        const onCommand = (event: Event) => {
            const custom = event as CustomEvent;
            const type = String(custom.type);
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
    const stageW     = viewport.width;
    const stageH     = viewport.height;

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
                <Line key={`v-${c}`}
                      points={[lx, lineTop, lx, lineBottom]}
                      stroke="rgba(255,255,255,0.12)" strokeWidth={0.5} />,
            );
        }
        for (let r = startRow; r <= endRow; r++) {
            const ly = grid.offsetY + r * grid.cellSize;
            lines.push(
                <Line key={`h-${r}`}
                      points={[lineLeft, ly, lineRight, ly]}
                      stroke="rgba(255,255,255,0.12)" strokeWidth={0.5} />,
            );
        }
        return lines;
    }, [grid.cols, grid.rows, grid.cellSize, grid.offsetX, grid.offsetY, gridPixelH, gridPixelW, visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom]);

    const visibleTerrainCells = useMemo(() => {
        const cells = Array.isArray((terrainLayer as any)?.cells) ? (terrainLayer as any).cells : [];
        return cells.filter((cell: any) => {
            const col = Math.floor(getNum(cell?.col));
            const row = Math.floor(getNum(cell?.row));
            const w = Math.max(1, Math.floor(getNum(cell?.width, 1)));
            const h = Math.max(1, Math.floor(getNum(cell?.height, 1)));
            const x = grid.offsetX + col * grid.cellSize;
            const y = grid.offsetY + row * grid.cellSize;
            return rectIntersects(
                visibleBounds,
                itemBounds(x, y, w * grid.cellSize, h * grid.cellSize),
            );
        });
    }, [terrainLayer, grid.cellSize, grid.offsetX, grid.offsetY, grid.cols, grid.rows, visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom]);

    const visibleWallPaths = useMemo(() => {
        const paths = Array.isArray((wallLayer as any)?.paths) ? (wallLayer as any).paths : [];
        return paths.filter((path: any) => {
            const points = Array.isArray(path?.points) ? path.points : [];
            if (points.length < 2) return false;
            let minX = Number.POSITIVE_INFINITY;
            let minY = Number.POSITIVE_INFINITY;
            let maxX = Number.NEGATIVE_INFINITY;
            let maxY = Number.NEGATIVE_INFINITY;
            for (const pt of points) {
                const x = getNum(pt?.x);
                const y = getNum(pt?.y);
                if (x < minX) minX = x;
                if (y < minY) minY = y;
                if (x > maxX) maxX = x;
                if (y > maxY) maxY = y;
            }
            const thickness = Math.max(1.5, getNum(path?.thickness, 2.5));
            return rectIntersects(
                visibleBounds,
                itemBounds(minX - thickness, minY - thickness, (maxX - minX) + thickness * 2, (maxY - minY) + thickness * 2),
            );
        });
    }, [wallLayer, visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom]);

    return (
        <div className="battle-map" style={{ background: 'transparent', overflow: 'hidden', width: '100vw', height: '100dvh', touchAction: 'none' }}>
            <Stage
                ref={stageRef}
                width={stageW}
                height={stageH}
                onMouseDown={(e) => {
                    if (e.target === e.currentTarget) clearSelection();
                }}
                onTouchStart={(e) => {
                    if (e.target === e.currentTarget) clearSelection();
                }}
                // FIX: controlled via React state — not overwritten on re-render
                draggable={stageDraggable}
                onDragMove={syncStageTransformFromNode}
                onDragEnd={syncStageTransformFromNode}
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
                    commitStageTransform({
                        x: stage.x(),
                        y: stage.y(),
                        scale: stage.scaleX() || 1,
                    });
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
                    {visibleTerrainCells.map((cell: any, idx: number) => {
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
                    {visibleWallPaths.map((path: any, idx: number) => {
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
                            isSelected={token.id === selectedTokenId}
                            cellSize={grid.cellSize}
                            offsetX={grid.offsetX}
                            offsetY={grid.offsetY}
                            onSelect={handleSelectToken}
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
