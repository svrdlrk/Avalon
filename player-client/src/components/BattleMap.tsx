import React, { useRef, useCallback, useState } from 'react';
import { Stage, Layer, Rect, Line, Circle, Text, Group, Image as KonvaImage } from 'react-konva';
import { useGameStore } from '../store/gameStore';
import type { TokenDto, MapObjectDto } from '../types/types';
import { wsClient } from '../net/wsClient';
import useImage from '../hooks/useImage';
import type Konva from 'konva';

const SERVER_BASE = 'http://localhost:8080';

function useTokenImage(imageUrl: string | null) {
    const fullUrl = imageUrl
        ? (imageUrl.startsWith('http') ? imageUrl : SERVER_BASE + imageUrl)
        : null;
    return useImage(fullUrl);
}

function hpColor(hp: number, maxHp: number): string {
    if (maxHp <= 0) return '#2ecc71';
    const ratio = hp / maxHp;
    if (ratio > 0.5) return '#2ecc71';
    if (ratio > 0.25) return '#f39c12';
    return '#e74c3c';
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

    // Позиция левого верхнего угла токена (Group позиционируется по top-left)
    const x = offsetX + token.col * cellSize;
    const y = offsetY + token.row * cellSize;
    const size = gs * cellSize;
    const cx = size / 2;
    const cy = size / 2;
    const radius = size / 2 - 4;

    const isNpc = token.ownerId === null;
    const canDrag = isMyToken || isDm;

    const [tokenImage] = useTokenImage(token.imageUrl);
    const [hovered, setHovered] = useState(false);

    const borderColor = isMyToken ? '#f1c40f' : isNpc ? '#e74c3c' : '#4a90d9';
    const fillColor = isMyToken ? '#c9a227' : isNpc ? '#c0392b' : '#2980b9';
    const fontSize = Math.max(9, Math.floor(size / 7));
    const hpRatio = token.maxHp > 0 ? Math.max(0, Math.min(1, token.hp / token.maxHp)) : 1;
    const barW = size - 10;

    // HP показываем только: игрокам для своих токенов, или DM для всех
    const showHp = token.maxHp > 0 && (isDm || isMyToken);

    return (
        <Group
            x={x}
            y={y}
            draggable={canDrag}
            onDragStart={(e) => {
                if (!canDrag) {
                    e.target.stopDrag();
                    return;
                }
                onDragStart();
            }}
            onDragEnd={(e) => {
                onDragEnd(e, token);
            }}
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
        >
            {/* Highlight for own token */}
            {isMyToken && (
                <Circle
                    x={cx} y={cy}
                    radius={radius + 3}
                    fill="rgba(241,196,15,0.25)"
                />
            )}

            {/* Main circle */}
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

            {/* Token image */}
            {tokenImage && (
                <KonvaImage
                    image={tokenImage}
                    x={cx - radius}
                    y={cy - radius}
                    width={radius * 2}
                    height={radius * 2}
                    cornerRadius={radius}
                    opacity={0.95}
                    listening={false}
                />
            )}

            {/* Name label */}
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

            {/* Size label for big tokens */}
            {gs > 1 && (
                <Text
                    x={size - 22}
                    y={4}
                    text={`${gs}×${gs}`}
                    fontSize={9}
                    fill="rgba(255,255,255,0.7)"
                    listening={false}
                />
            )}

            {/* HP bar */}
            {showHp && (
                <>
                    <Rect
                        x={5} y={size + 4}
                        width={barW} height={5}
                        fill="#1a1a1a" cornerRadius={2}
                        listening={false}
                    />
                    <Rect
                        x={5} y={size + 4}
                        width={barW * hpRatio} height={5}
                        fill={hpColor(token.hp, token.maxHp)}
                        cornerRadius={2}
                        listening={false}
                    />
                </>
            )}

            {/* Tooltip on hover */}
            {hovered && (
                <Group x={size + 4} y={0}>
                    <Rect
                        x={0} y={0}
                        width={140} height={72}
                        fill="rgba(20,20,40,0.95)"
                        stroke="#7f8c8d"
                        strokeWidth={1}
                        cornerRadius={5}
                        listening={false}
                    />
                    <Text x={6} y={8}   text={token.name} fontSize={12} fontStyle="bold" fill="#ecf0f1" width={128} listening={false} />
                    <Text x={6} y={24}  text={`HP: ${token.hp} / ${token.maxHp}`} fontSize={11} fill="#2ecc71" listening={false} />
                    <Text x={6} y={40}  text={`Размер: ${gs}×${gs}`} fontSize={11} fill="#bdc3c7" listening={false} />
                    <Text x={6} y={56}  text={token.ownerId ? 'Игрок' : 'NPC'} fontSize={11} fill="#bdc3c7" listening={false} />
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

    const fullUrl = obj.imageUrl
        ? (obj.imageUrl.startsWith('http') ? obj.imageUrl : SERVER_BASE + obj.imageUrl)
        : null;
    const [objImage] = useImage(fullUrl);

    const px = offsetX + obj.col * cellSize;
    const py = offsetY + obj.row * cellSize;
    const pw = w * cellSize;
    const ph = h * cellSize;

    return (
        <Group x={px} y={py} listening={false}>
            {objImage ? (
                <>
                    <KonvaImage
                        image={objImage}
                        x={0} y={0}
                        width={pw} height={ph}
                        opacity={0.95}
                    />
                    {/* Subtle dark overlay */}
                    <Rect
                        x={0} y={0}
                        width={pw} height={ph}
                        fill="rgba(0,0,0,0.15)"
                    />
                </>
            ) : (
                /* Fallback colored rect */
                <Rect
                    x={0} y={0}
                    width={pw} height={ph}
                    fill="#5d4037"
                    stroke="#4e342e"
                    strokeWidth={1}
                />
            )}
            {/* Border */}
            <Rect
                x={0} y={0}
                width={pw} height={ph}
                fill="transparent"
                stroke="#5a2d0c"
                strokeWidth={1}
            />
            {/* Type label */}
            <Text
                x={2} y={2}
                text={obj.type}
                fontSize={9}
                fill="rgba(255,255,255,0.6)"
            />
        </Group>
    );
});

ObjectShape.displayName = 'ObjectShape';

// ================================================================ BattleMap

const BattleMap: React.FC = () => {
    const { grid, tokens, objects, myPlayerId, backgroundUrl } = useGameStore();
    const stageRef = useRef<Konva.Stage>(null);
    const isDraggingToken = useRef(false);

    // Determine if current player is DM
    const { players } = useGameStore();
    const myPlayer = myPlayerId ? players[myPlayerId] : null;
    const isDm = myPlayer?.role === 'DM';

    const fullBgUrl = backgroundUrl
        ? (backgroundUrl.startsWith('http') ? backgroundUrl : SERVER_BASE + backgroundUrl)
        : null;
    const [bgImage] = useImage(fullBgUrl);

    const handleTokenDragStart = useCallback(() => {
        isDraggingToken.current = true;
        // Disable stage dragging while dragging a token
        if (stageRef.current) {
            stageRef.current.draggable(false);
        }
    }, []);

    const handleDragEnd = useCallback((e: any, token: TokenDto) => {
        isDraggingToken.current = false;
        // Re-enable stage dragging
        if (stageRef.current) {
            stageRef.current.draggable(true);
        }

        if (!grid) return;
        const node = e.target;
        const gs = Math.max(1, token.gridSize ?? 1);

        // node.x() / node.y() is the top-left corner of the Group
        const rawX = node.x();
        const rawY = node.y();

        // Convert pixel position back to grid cell (top-left of token)
        const newCol = Math.round((rawX - grid.offsetX) / grid.cellSize);
        const newRow = Math.round((rawY - grid.offsetY) / grid.cellSize);
        const clampedCol = Math.max(0, Math.min(newCol, grid.cols - gs));
        const clampedRow = Math.max(0, Math.min(newRow, grid.rows - gs));

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
    }, [grid]);

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
    const stageW = Math.max(window.innerWidth, grid.offsetX + gridPixelW + 100);
    const stageH = Math.max(window.innerHeight, grid.offsetY + gridPixelH + 100);

    // Grid lines
    const gridLines: React.ReactNode[] = [];
    for (let c = 0; c <= grid.cols; c++) {
        const lx = grid.offsetX + c * grid.cellSize;
        gridLines.push(
            <Line key={`v-${c}`}
                  points={[lx, grid.offsetY, lx, grid.offsetY + gridPixelH]}
                  stroke="rgba(255,255,255,0.12)" strokeWidth={0.5}
            />
        );
    }
    for (let r = 0; r <= grid.rows; r++) {
        const ly = grid.offsetY + r * grid.cellSize;
        gridLines.push(
            <Line key={`h-${r}`}
                  points={[grid.offsetX, ly, grid.offsetX + gridPixelW, ly]}
                  stroke="rgba(255,255,255,0.12)" strokeWidth={0.5}
            />
        );
    }

    return (
        <div style={{ background: '#0f1117', overflow: 'hidden', width: '100vw', height: '100vh' }}>
            <Stage
                ref={stageRef}
                width={stageW}
                height={stageH}
                draggable={true}
                onWheel={(e) => {
                    e.evt.preventDefault();
                    const stage = stageRef.current;
                    if (!stage) return;
                    const oldScale = stage.scaleX() || 1;
                    const pointer = stage.getPointerPosition();
                    if (!pointer) return;
                    const mousePointTo = {
                        x: (pointer.x - stage.x()) / oldScale,
                        y: (pointer.y - stage.y()) / oldScale,
                    };
                    const direction = e.evt.deltaY > 0 ? -1 : 1;
                    const newScale = Math.max(0.2, Math.min(4, oldScale * (1 + direction * 0.1)));
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

                {/* Grid lines */}
                <Layer listening={false}>{gridLines}</Layer>

                {/* Objects */}
                <Layer>
                    {Object.values(objects).map((obj: MapObjectDto) => (
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
                    {Object.values(tokens).map((token: TokenDto) => (
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