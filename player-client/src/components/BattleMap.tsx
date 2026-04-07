import React, { useRef, useCallback, useState } from 'react';
import { Stage, Layer, Rect, Line, Circle, Text, Group, Image as KonvaImage } from 'react-konva';
import { useGameStore } from '../store/gameStore';
import type { TokenDto, MapObjectDto } from '../types/types';
import { wsClient } from '../net/wsClient';
import useImage from '../hooks/useImage';

const SERVER_BASE = 'http://localhost:8080';

// Хук для изображения с кешем
function useTokenImage(imageUrl: string | null) {
    const fullUrl = imageUrl
        ? (imageUrl.startsWith('http') ? imageUrl : SERVER_BASE + imageUrl)
        : null;
    return useImage(fullUrl);
}

// HP-цвет
function hpColor(hp: number, maxHp: number): string {
    if (maxHp <= 0) return '#2ecc71';
    const ratio = hp / maxHp;
    if (ratio > 0.5) return '#2ecc71';
    if (ratio > 0.25) return '#f39c12';
    return '#e74c3c';
}

// Компонент одного токена (вынесен для memo)
const TokenShape: React.FC<{
    token: TokenDto;
    isMyToken: boolean;
    cellSize: number;
    offsetX: number;
    offsetY: number;
    onDragEnd: (e: any, token: TokenDto) => void;
}> = React.memo(({ token, isMyToken, cellSize, offsetX, offsetY, onDragEnd }) => {
    const gs = Math.max(1, token.gridSize ?? 1);
    const cx = offsetX + token.col * cellSize + (gs * cellSize) / 2;
    const cy = offsetY + token.row * cellSize + (gs * cellSize) / 2;
    const radius = (gs * cellSize) / 2 - 4;
    const isNpc = token.ownerId === null;

    const [tokenImage] = useTokenImage(token.imageUrl);

    const borderColor = isMyToken ? '#f1c40f' : isNpc ? '#e74c3c' : '#4a90d9';
    const fillColor = isMyToken ? '#c9a227' : isNpc ? '#c0392b' : '#2980b9';
    const fontSize = Math.max(9, Math.floor((gs * cellSize) / 7));
    const hpRatio = token.maxHp > 0 ? Math.max(0, Math.min(1, token.hp / token.maxHp)) : 1;
    const barW = gs * cellSize - 10;

    const [hovered, setHovered] = useState(false);

    return (
        <Group
            x={cx}
            y={cy}
            draggable={isMyToken}
            onDragStart={(e) => { if (!isMyToken) e.target.stopDrag(); }}
            onDragEnd={(e) => onDragEnd(e, token)}
            onMouseEnter={() => setHovered(true)}
            onMouseLeave={() => setHovered(false)}
        >
            {/* Shadow для выделения */}
            {isMyToken && (
                <Circle
                    x={0} y={0}
                    radius={radius + 3}
                    fill="rgba(241,196,15,0.25)"
                />
            )}

            {/* Основной круг */}
            <Circle
                x={0} y={0}
                radius={radius}
                fill={fillColor}
                stroke={borderColor}
                strokeWidth={isMyToken ? 2.5 : 1.5}
                shadowColor={borderColor}
                shadowBlur={isMyToken ? 14 : 6}
                shadowOpacity={0.7}
            />

            {/* Изображение существа */}
            {tokenImage && (
                <KonvaImage
                    image={tokenImage}
                    x={-radius}
                    y={-radius}
                    width={radius * 2}
                    height={radius * 2}
                    cornerRadius={radius}
                    opacity={0.95}
                />
            )}

            {/* Имя */}
            <Text
                x={-radius}
                y={tokenImage ? radius - fontSize - 4 : -fontSize / 2}
                width={radius * 2}
                align="center"
                text={token.name.length > 9 ? token.name.slice(0, 8) + '…' : token.name}
                fontSize={fontSize}
                fontStyle="bold"
                fill="#fff"
                shadowColor="rgba(0,0,0,0.95)"
                shadowBlur={4}
            />

            {/* Метка размера для больших токенов */}
            {gs > 1 && (
                <Text
                    x={radius - 20}
                    y={-radius + 2}
                    text={`${gs}×${gs}`}
                    fontSize={9}
                    fill="rgba(255,255,255,0.7)"
                />
            )}

            {/* HP-бар */}
            {token.maxHp > 0 && (
                <>
                    <Rect
                        x={-barW / 2} y={radius + 4}
                        width={barW} height={5}
                        fill="#1a1a1a" cornerRadius={2}
                    />
                    <Rect
                        x={-barW / 2} y={radius + 4}
                        width={barW * hpRatio} height={5}
                        fill={hpColor(token.hp, token.maxHp)}
                        cornerRadius={2}
                    />
                </>
            )}

            {/* Tooltip при наведении */}
            {hovered && (
                <Group x={radius + 4} y={-radius}>
                    <Rect
                        x={0} y={0}
                        width={130} height={68}
                        fill="rgba(20,20,40,0.92)"
                        stroke="#7f8c8d"
                        strokeWidth={1}
                        cornerRadius={5}
                    />
                    <Text x={6} y={6}   text={token.name} fontSize={12} fontStyle="bold" fill="#ecf0f1" width={118} />
                    <Text x={6} y={22}  text={`HP: ${token.hp} / ${token.maxHp}`} fontSize={11} fill="#2ecc71" />
                    <Text x={6} y={38}  text={`Размер: ${gs}×${gs}`} fontSize={11} fill="#bdc3c7" />
                    <Text x={6} y={52}  text={token.ownerId ? 'Игрок' : 'NPC'} fontSize={11} fill="#bdc3c7" />
                </Group>
            )}
        </Group>
    );
});

TokenShape.displayName = 'TokenShape';

// Компонент объекта карты
const ObjectShape: React.FC<{
    obj: MapObjectDto;
    cellSize: number;
    offsetX: number;
    offsetY: number;
}> = React.memo(({ obj, cellSize, offsetX, offsetY }) => {
    const gs = Math.max(1, obj.gridSize ?? 1);
    const fullUrl = obj.imageUrl
        ? (obj.imageUrl.startsWith('http') ? obj.imageUrl : SERVER_BASE + obj.imageUrl)
        : null;
    const [objImage] = useImage(fullUrl);

    const cx = offsetX + obj.col * cellSize + (gs * cellSize) / 2;
    const cy = offsetY + obj.row * cellSize + (gs * cellSize) / 2;
    const radius = (gs * cellSize) / 2 - 4;

    return (
        <Group
            x={cx}
            y={cy}
        >
            {/* Основной круг */}
            <Circle
                x={0} y={0}
                radius={radius}
                shadowOpacity={0.7}
            />

            {/* Изображение существа */}
            {objImage && (
                <KonvaImage
                    image={objImage}
                    x={-radius}
                    y={-radius}
                    width={radius * 2}
                    height={radius * 2}
                    cornerRadius={radius}
                    opacity={0.95}
                />
            )}
            {/* Метка размера для больших токенов */}
            {gs > 1 && (
                <Text
                    x={radius - 20}
                    y={-radius + 2}
                    text={`${gs}×${gs}`}
                    fontSize={9}
                    fill="rgba(255,255,255,0.7)"
                />
            )}
        </Group>
    );
});

ObjectShape.displayName = 'ObjectShape';

// ================================================================ Main component

const BattleMap: React.FC = () => {
    const { grid, tokens, objects, myPlayerId, backgroundUrl } = useGameStore();
    const stageRef = useRef<any>(null);

    const fullBgUrl = backgroundUrl
        ? (backgroundUrl.startsWith('http') ? backgroundUrl : SERVER_BASE + backgroundUrl)
        : null;
    const [bgImage] = useImage(fullBgUrl);

    const handleDragEnd = useCallback((e: any, token: TokenDto) => {
        if (!grid) return;
        const node = e.target;
        const gs = Math.max(1, token.gridSize ?? 1);
        const rawX = node.x();
        const rawY = node.y();
        const newCol = Math.round((rawX - grid.offsetX - (gs * grid.cellSize) / 2) / grid.cellSize);
        const newRow = Math.round((rawY - grid.offsetY - (gs * grid.cellSize) / 2) / grid.cellSize);
        const clampedCol = Math.max(0, Math.min(newCol, grid.cols - gs));
        const clampedRow = Math.max(0, Math.min(newRow, grid.rows - gs));

        node.position({
            x: grid.offsetX + clampedCol * grid.cellSize + (gs * grid.cellSize) / 2,
            y: grid.offsetY + clampedRow * grid.cellSize + (gs * grid.cellSize) / 2,
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

    // Линии сетки
    const gridLines: React.ReactNode[] = [];
    for (let c = 0; c <= grid.cols; c++) {
        const x = grid.offsetX + c * grid.cellSize;
        gridLines.push(
            <Line key={`v-${c}`}
                  points={[x, grid.offsetY, x, grid.offsetY + gridPixelH]}
                  stroke="rgba(255,255,255,0.12)" strokeWidth={0.5}
            />
        );
    }
    for (let r = 0; r <= grid.rows; r++) {
        const y = grid.offsetY + r * grid.cellSize;
        gridLines.push(
            <Line key={`h-${r}`}
                  points={[grid.offsetX, y, grid.offsetX + gridPixelW, y]}
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
                    const newScale = Math.max(0.2, Math.min(4, oldScale * (1 + direction * 0.1)));
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

                {/* Сетка */}
                <Layer listening={false}>{gridLines}</Layer>

                {/* Объекты */}
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

                {/* Токены */}
                <Layer>
                    {Object.values(tokens).map((token: TokenDto) => (
                        <TokenShape
                            key={token.id}
                            token={token}
                            isMyToken={token.ownerId === myPlayerId}
                            cellSize={grid.cellSize}
                            offsetX={grid.offsetX}
                            offsetY={grid.offsetY}
                            onDragEnd={handleDragEnd}
                        />
                    ))}
                </Layer>
            </Stage>
        </div>
    );
};

export default BattleMap;