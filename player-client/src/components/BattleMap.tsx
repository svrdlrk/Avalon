import { useCallback, useEffect, useRef } from 'react';
import { useGameStore } from '../store/gameStore';
import { wsClient } from '../net/wsClient';
import type { TokenDto } from '../types/types';

export default function BattleMap() {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const { grid, tokens, objects, myPlayerId } = useGameStore();
    const dragging = useRef<{
        token: TokenDto;
        offsetX: number;
        offsetY: number;
    } | null>(null);
    const mousePos = useRef<{ x: number; y: number }>({ x: 0, y: 0 });

    const render = useCallback(() => {
        const canvas = canvasRef.current;
        if (!canvas || !grid?.cellSize) return;
        const gc = canvas.getContext('2d');
        if (!gc) return;

        const { cellSize, cols, rows, offsetX, offsetY } = grid;

        // Фон
        gc.fillStyle = '#2b2b2b';
        gc.fillRect(0, 0, canvas.width, canvas.height);

        // Сетка
        gc.strokeStyle = '#444444';
        gc.lineWidth = 0.5;
        for (let c = 0; c <= cols; c++) {
            const x = offsetX + c * cellSize;
            gc.beginPath();
            gc.moveTo(x, offsetY);
            gc.lineTo(x, offsetY + rows * cellSize);
            gc.stroke();
        }
        for (let r = 0; r <= rows; r++) {
            const y = offsetY + r * cellSize;
            gc.beginPath();
            gc.moveTo(offsetX, y);
            gc.lineTo(offsetX + cols * cellSize, y);
            gc.stroke();
        }

        // Объекты (стены и т.д.)
        Object.values(objects).forEach((obj) => {
            const x = offsetX + obj.col * cellSize;
            const y = offsetY + obj.row * cellSize;
            const w = obj.width * cellSize;
            const h = obj.height * cellSize;
            gc.fillStyle = '#8B4513';
            gc.fillRect(x, y, w, h);
            gc.strokeStyle = '#5a2d0c';
            gc.lineWidth = 1;
            gc.strokeRect(x, y, w, h);
        });

        // Токены с HP-барами
        Object.values(tokens).forEach((token) => {
            const x = offsetX + token.col * cellSize;
            const y = offsetY + token.row * cellSize;
            const cx = x + cellSize / 2;
            const cy = y + cellSize / 2;
            const r = cellSize / 2 - 4;

            const isMyToken = token.ownerId === myPlayerId;
            const isNpc = token.ownerId === null;

            // Тень для выделения
            gc.shadowColor = isMyToken ? '#5cb85c' : 'transparent';
            gc.shadowBlur = isMyToken ? 8 : 0;

            // Круг токена
            gc.fillStyle = isMyToken ? '#5cb85c' : isNpc ? '#c9a227' : '#4a90d9';
            gc.beginPath();
            gc.arc(cx, cy, r, 0, Math.PI * 2);
            gc.fill();

            gc.shadowBlur = 0;

            // Обводка
            gc.strokeStyle = 'rgba(0,0,0,0.5)';
            gc.lineWidth = 1;
            gc.stroke();

            // Имя токена
            gc.fillStyle = 'white';
            gc.font = `bold ${Math.max(9, cellSize / 6)}px sans-serif`;
            gc.textAlign = 'center';
            gc.textBaseline = 'middle';
            gc.fillText(
                token.name.length > 6 ? token.name.slice(0, 5) + '…' : token.name,
                cx,
                cy
            );

            // HP-бар (только если есть данные о HP)
            if (token.maxHp > 0) {
                const barW = cellSize - 8;
                const barH = 4;
                const barX = x + 4;
                const barY = y + cellSize - 8;
                const hpRatio = Math.max(0, token.hp / token.maxHp);

                // Фон бара
                gc.fillStyle = '#333';
                gc.fillRect(barX, barY, barW, barH);

                // Заполнение — цвет зависит от % HP
                const hpColor =
                    hpRatio > 0.5 ? '#4caf50' : hpRatio > 0.25 ? '#ff9800' : '#f44336';
                gc.fillStyle = hpColor;
                gc.fillRect(barX, barY, barW * hpRatio, barH);
            }
        });

        gc.textBaseline = 'alphabetic';
    }, [grid, tokens, objects, myPlayerId]);

    // Перерисовка при любом изменении состояния
    useEffect(() => {
        render();
    }, [render]);

    // Ресайз канваса при изменении сетки (баг: раньше не обновлялся)
    useEffect(() => {
        const canvas = canvasRef.current;
        if (!canvas || !grid?.cellSize) return;
        const newW = grid.offsetX + grid.cols * grid.cellSize;
        const newH = grid.offsetY + grid.rows * grid.cellSize;
        if (canvas.width !== newW || canvas.height !== newH) {
            canvas.width = newW;
            canvas.height = newH;
            render();
        }
    }, [grid, render]);

    if (!grid?.cellSize) return null;

    // --- Утилиты для drag ---
    const getCanvasPos = (clientX: number, clientY: number) => {
        const rect = canvasRef.current!.getBoundingClientRect();
        return { x: clientX - rect.left, y: clientY - rect.top };
    };

    const getCell = (x: number, y: number) => ({
        col: Math.floor((x - grid.offsetX) / grid.cellSize),
        row: Math.floor((y - grid.offsetY) / grid.cellSize),
    });

    const findMyToken = (col: number, row: number) =>
        Object.values(tokens).find(
            (t) => t.col === col && t.row === row && t.ownerId === myPlayerId
        );

    const startDrag = (clientX: number, clientY: number) => {
        const { x, y } = getCanvasPos(clientX, clientY);
        mousePos.current = { x, y };
        const { col, row } = getCell(x, y);
        const token = findMyToken(col, row);
        if (token) {
            dragging.current = {
                token,
                offsetX: x - (grid.offsetX + col * grid.cellSize),
                offsetY: y - (grid.offsetY + row * grid.cellSize),
            };
        }
    };

    const moveDrag = (clientX: number, clientY: number) => {
        if (!dragging.current) return;
        const { x, y } = getCanvasPos(clientX, clientY);
        mousePos.current = { x, y };
        render();

        // Рисуем "призрак" перетаскиваемого токена
        const canvas = canvasRef.current!;
        const gc = canvas.getContext('2d')!;
        const { cellSize } = grid;
        const px = x - dragging.current.offsetX;
        const py = y - dragging.current.offsetY;
        gc.fillStyle = 'rgba(92, 184, 92, 0.6)';
        gc.beginPath();
        gc.arc(px + cellSize / 2, py + cellSize / 2, cellSize / 2 - 4, 0, Math.PI * 2);
        gc.fill();
    };

    const finishDrag = () => {
        if (!dragging.current) return;
        const { x, y } = mousePos.current;
        const { cols, rows } = grid;

        let { col: newCol, row: newRow } = getCell(x, y);
        newCol = Math.max(0, Math.min(newCol, cols - 1));
        newRow = Math.max(0, Math.min(newRow, rows - 1));

        wsClient.send('/token.move', {
            tokenId: dragging.current.token.id,
            toCol: newCol,
            toRow: newRow,
        });

        dragging.current = null;
        render();
    };

    // --- Обработчики мыши ---
    const onMouseDown = (e: React.MouseEvent) => {
        if (e.button !== 0) return;
        e.preventDefault();
        startDrag(e.clientX, e.clientY);
    };
    const onMouseMove = (e: React.MouseEvent) => moveDrag(e.clientX, e.clientY);
    const onMouseUp = () => finishDrag();
    const onMouseLeave = () => { if (dragging.current) finishDrag(); };

    // --- Обработчики тача ---
    const onTouchStart = (e: React.TouchEvent) => {
        const t = e.touches[0];
        startDrag(t.clientX, t.clientY);
    };
    const onTouchMove = (e: React.TouchEvent) => {
        e.preventDefault();
        const t = e.touches[0];
        moveDrag(t.clientX, t.clientY);
    };
    const onTouchEnd = () => finishDrag();

    const width = grid.offsetX + grid.cols * grid.cellSize;
    const height = grid.offsetY + grid.rows * grid.cellSize;

    return (
        <div style={{ overflow: 'auto', width: '100vw', height: '100vh', background: '#1a1a1a' }}>
            <canvas
                ref={canvasRef}
                width={width}
                height={height}
                onMouseDown={onMouseDown}
                onMouseMove={onMouseMove}
                onMouseUp={onMouseUp}
                onMouseLeave={onMouseLeave}
                onTouchStart={onTouchStart}
                onTouchMove={onTouchMove}
                onTouchEnd={onTouchEnd}
                style={{
                    display: 'block',
                    touchAction: 'none',
                    cursor: dragging.current ? 'grabbing' : 'default',
                }}
            />
        </div>
    );
}