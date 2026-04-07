import React, { useRef, useCallback } from 'react';
import { Stage, Layer, Rect, Line, Circle, Text, Group, Image as KonvaImage } from 'react-konva';
import { useGameStore } from '../store/gameStore';
import type { TokenDto, MapObjectDto } from '../types/types';
import { wsClient } from '../net/wsClient';
import useImage from '../hooks/useImage';

const BattleMap: React.FC = () => {
    const { grid, tokens, objects, myPlayerId, backgroundUrl } = useGameStore();
    const stageRef = useRef<any>(null);

    // FIX: загружаем фон через хук (async)
    const serverBase = 'http://localhost:8080';
    const fullBgUrl = backgroundUrl
        ? (backgroundUrl.startsWith('http') ? backgroundUrl : serverBase + backgroundUrl)
        : null;
    const [bgImage] = useImage(fullBgUrl);

    const hpColor = (hp: number, maxHp: number): string => {
        if (maxHp <= 0) return '#4caf50';
        const ratio = hp / maxHp;
        if (ratio > 0.5) return '#4caf50';
        if (ratio > 0.25) return '#ff9800';
        return '#f44336';
    };

    const handleDragEnd = useCallback((e: any, token: TokenDto) => {
        if (!grid) return;
        const node = e.target;
        const rawX = node.x();
        const rawY = node.y();
        const newCol = Math.round((rawX - grid.offsetX - grid.cellSize / 2) / grid.cellSize);
        const newRow = Math.round((rawY - grid.offsetY - grid.cellSize / 2) / grid.cellSize);
        const clampedCol = Math.max(0, Math.min(newCol, grid.cols - 1));
        const clampedRow = Math.max(0, Math.min(newRow, grid.rows - 1));
        node.position({
            x: grid.offsetX + clampedCol * grid.cellSize + grid.cellSize / 2,
            y: grid.offsetY + clampedRow * grid.cellSize + grid.cellSize / 2,
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
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100vh',
                color: '#9ca3af',
                fontSize: '18px',
                background: '#0f1117',
            }}>
                Подключитесь к сессии...
            </div>
        );
    }

    const gridPixelW = grid.cols * grid.cellSize;
    const gridPixelH = grid.rows * grid.cellSize;
    const stageW = Math.max(window.innerWidth, grid.offsetX + gridPixelW + 100);
    const stageH = Math.max(window.innerHeight, grid.offsetY + gridPixelH + 100);

    const gridLines: React.ReactNode[] = [];
    for (let c = 0; c <= grid.cols; c++) {
        const x = grid.offsetX + c * grid.cellSize;
        gridLines.push(
            <Line key={`v-${c}`}
                  points={[x, grid.offsetY, x, grid.offsetY + gridPixelH]}
                  stroke="rgba(255,255,255,0.15)"
                  strokeWidth={0.5}
            />
        );
    }
    for (let r = 0; r <= grid.rows; r++) {
        const y = grid.offsetY + r * grid.cellSize;
        gridLines.push(
            <Line key={`h-${r}`}
                  points={[grid.offsetX, y, grid.offsetX + gridPixelW, y]}
                  stroke="rgba(255,255,255,0.15)"
                  strokeWidth={0.5}
            />
        );
    }

    return (
        <div style={{ background: '#0f1117', overflow: 'hidden', width: '100vw', height: '100vh' }}>
            <Stage
                ref={stageRef}
                width={stageW}
                height={stageH}
                draggable
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
                    const newScale = Math.max(0.25, Math.min(4, oldScale * (1 + direction * 0.1)));
                    stage.scale({ x: newScale, y: newScale });
                    stage.position({
                        x: pointer.x - mousePointTo.x * newScale,
                        y: pointer.y - mousePointTo.y * newScale,
                    });
                    stage.batchDraw();
                }}
            >
                {/* Фон */}
                <Layer>
                    <Rect x={0} y={0} width={stageW} height={stageH} fill="#0f1117" />
                    {/* FIX: рисуем загруженную картинку карты */}
                    {bgImage ? (
                        <KonvaImage
                            image={bgImage}
                            x={grid.offsetX}
                            y={grid.offsetY}
                            width={gridPixelW}
                            height={gridPixelH}
                        />
                    ) : (
                        <Rect
                            x={grid.offsetX}
                            y={grid.offsetY}
                            width={gridPixelW}
                            height={gridPixelH}
                            fill="#1a2035"
                        />
                    )}
                </Layer>

                {/* Линии сетки */}
                <Layer listening={false}>
                    {gridLines}
                </Layer>

                {/* Объекты */}
                <Layer>
                    {Object.values(objects).map((obj: MapObjectDto) => (
                        <Rect
                            key={obj.id}
                            x={grid.offsetX + obj.col * grid.cellSize}
                            y={grid.offsetY + obj.row * grid.cellSize}
                            width={obj.width * grid.cellSize}
                            height={obj.height * grid.cellSize}
                            fill="#8B4513"
                            stroke="#5a2d0c"
                            strokeWidth={2}
                            cornerRadius={3}
                        />
                    ))}
                </Layer>

                {/* Токены */}
                <Layer>
                    {Object.values(tokens).map((token: TokenDto) => {
                        const cx = grid.offsetX + token.col * grid.cellSize + grid.cellSize / 2;
                        const cy = grid.offsetY + token.row * grid.cellSize + grid.cellSize / 2;
                        const radius = Math.max(12, grid.cellSize / 2 - 5);
                        const isMyToken = token.ownerId === myPlayerId;
                        const isNpc = token.ownerId === null;
                        const fillColor = isMyToken ? '#22c55e' : isNpc ? '#c9a227' : '#3b82f6';
                        const hpRatio = token.maxHp > 0
                            ? Math.max(0, Math.min(1, token.hp / token.maxHp))
                            : 1;
                        const barW = radius * 2;
                        const fontSize = Math.max(9, Math.floor(grid.cellSize / 6));

                        return (
                            <Group
                                key={token.id}
                                x={cx}
                                y={cy}
                                draggable={isMyToken}
                                onDragStart={(e) => {
                                    if (!isMyToken) e.target.stopDrag();
                                }}
                                onDragEnd={(e) => handleDragEnd(e, token)}
                            >
                                <Circle
                                    x={0} y={0}
                                    radius={radius}
                                    fill={fillColor}
                                    stroke={isMyToken ? '#fff' : 'rgba(255,255,255,0.25)'}
                                    strokeWidth={isMyToken ? 2.5 : 1}
                                    shadowColor={fillColor}
                                    shadowBlur={isMyToken ? 16 : 8}
                                    shadowOpacity={0.65}
                                />
                                <Text
                                    x={-radius}
                                    y={-fontSize / 2 - 1}
                                    width={radius * 2}
                                    align="center"
                                    text={token.name.length > 7
                                        ? token.name.slice(0, 6) + '…'
                                        : token.name}
                                    fontSize={fontSize}
                                    fontStyle="bold"
                                    fill="#fff"
                                    shadowColor="rgba(0,0,0,0.9)"
                                    shadowBlur={3}
                                />
                                {token.maxHp > 0 && (
                                    <>
                                        <Rect
                                            x={-radius} y={radius + 4}
                                            width={barW} height={5}
                                            fill="#222" cornerRadius={2}
                                        />
                                        <Rect
                                            x={-radius} y={radius + 4}
                                            width={barW * hpRatio} height={5}
                                            fill={hpColor(token.hp, token.maxHp)}
                                            cornerRadius={2}
                                        />
                                    </>
                                )}
                            </Group>
                        );
                    })}
                </Layer>
            </Stage>
        </div>
    );
};

export default BattleMap;