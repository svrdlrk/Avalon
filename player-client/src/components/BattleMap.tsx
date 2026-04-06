import React, { useRef } from 'react';
import { Stage, Layer, Rect, Circle, Text } from 'react-konva';
import { useGameStore } from '../store/gameStore';
import type { TokenDto, MapObjectDto } from '../types/types';
import {wsClient} from "../net/wsClient.ts";

const BattleMap: React.FC = () => {
    const { grid, tokens, objects, myPlayerId } = useGameStore();
    const stageRef = useRef<any>(null);

    const handleDragStart = (e: any, token: TokenDto) => {
        if (token.ownerId !== myPlayerId) {
            e.target.stopDrag();
        }
    };

    const handleDragEnd = (e: any, token: TokenDto) => {
        if (!grid) return;

        const node = e.target;
        const newCol = Math.round((node.x() - grid.offsetX) / grid.cellSize);
        const newRow = Math.round((node.y() - grid.offsetY) / grid.cellSize);

        const movePayload = {
            tokenId: token.id,
            toCol: Math.max(0, Math.min(newCol, grid.cols - 1)),
            toRow: Math.max(0, Math.min(newRow, grid.rows - 1)),
        };

        wsClient.send('/token.move', movePayload);   // <-- работает через существующий wsClient

        // Возвращаем визуально до подтверждения от сервера
        node.position({
            x: grid.offsetX + token.col * grid.cellSize + grid.cellSize / 2,
            y: grid.offsetY + token.row * grid.cellSize + grid.cellSize / 2,
        });
    };

    if (!grid) {
        return (
            <div className="flex items-center justify-center h-screen text-white bg-[#1a1a1a]">
                Подключитесь к сессии...
            </div>
        );
    }

    const gridWidth = grid.offsetX + grid.cols * grid.cellSize;
    const gridHeight = grid.offsetY + grid.rows * grid.cellSize;

    return (
        <div className="bg-[#1a1a1a] overflow-auto">
            <Stage
                ref={stageRef}
                width={Math.max(1800, gridWidth)}
                height={Math.max(1200, gridHeight)}
                draggable
                onWheel={(e) => {
                    e.evt.preventDefault();
                    const stage = stageRef.current;
                    if (!stage) return;

                    const oldScale = stage.scaleX() || 1;
                    const pointer = stage.getPointerPosition()!;
                    const mousePointTo = {
                        x: (pointer.x - stage.x()) / oldScale,
                        y: (pointer.y - stage.y()) / oldScale,
                    };

                    const newScale = e.evt.deltaY > 0 ? oldScale * 0.9 : oldScale * 1.1;
                    stage.scale({ x: newScale, y: newScale });

                    const newPos = {
                        x: pointer.x - mousePointTo.x * newScale,
                        y: pointer.y - mousePointTo.y * newScale,
                    };
                    stage.position(newPos);
                    stage.batchDraw();
                }}
            >
                {/* Фон */}
                <Layer>
                    <Rect
                        x={0}
                        y={0}
                        width={gridWidth}
                        height={gridHeight}
                        fill="#1f2937"
                    />
                </Layer>

                {/* Сетка */}
                <Layer>
                    <Rect
                        x={grid.offsetX}
                        y={grid.offsetY}
                        width={grid.cols * grid.cellSize}
                        height={grid.rows * grid.cellSize}
                        stroke="#444"
                        strokeWidth={1}
                        dash={[4, 4]}
                    />
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
                            strokeWidth={3}
                            cornerRadius={6}
                        />
                    ))}
                </Layer>

                {/* Токены */}
                <Layer>
                    {Object.values(tokens).map((token: TokenDto) => {
                        const x = grid.offsetX + token.col * grid.cellSize + grid.cellSize / 2;
                        const y = grid.offsetY + token.row * grid.cellSize + grid.cellSize / 2;
                        const radius = Math.max(14, grid.cellSize / 2 - 6);
                        const isMyToken = token.ownerId === myPlayerId;

                        return (
                            <React.Fragment key={token.id}>
                                <Circle
                                    x={x}
                                    y={y}
                                    radius={radius}
                                    fill={isMyToken ? '#22c55e' : '#3b82f6'}
                                    stroke="#111"
                                    strokeWidth={4}
                                    shadowColor={isMyToken ? '#22c55e' : '#000'}
                                    shadowBlur={isMyToken ? 20 : 12}
                                    shadowOpacity={0.7}
                                    draggable={isMyToken}
                                    onDragStart={(e) => handleDragStart(e, token)}
                                    onDragEnd={(e) => handleDragEnd(e, token)}
                                />
                                <Text
                                    x={x - radius * 1.8}
                                    y={y - 10}
                                    width={radius * 3.6}
                                    align="center"
                                    text={token.name}
                                    fontSize={13}
                                    fontStyle="bold"
                                    fill="#ffffff"
                                />
                            </React.Fragment>
                        );
                    })}
                </Layer>
            </Stage>
        </div>
    );
};

export default BattleMap;