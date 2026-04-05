import { useCallback, useEffect, useRef } from 'react';
import { useGameStore } from '../store/gameStore';
import { wsClient } from '../net/wsClient';
import type { TokenDto } from '../types/types';

export default function BattleMap() {
    const canvasRef = useRef<HTMLCanvasElement>(null);
    const { grid, tokens, objects, myPlayerId } = useGameStore();
    const dragging = useRef<{ token: TokenDto; offsetX: number; offsetY: number } | null>(null);
    const mousePos = useRef<{ x: number; y: number }>({ x: 0, y: 0 });

    const render = useCallback(() => {
        const canvas = canvasRef.current;
        if (!canvas || !grid?.cellSize) return;
        const gc = canvas.getContext('2d');
        if (!gc) return;

        gc.fillStyle = '#2b2b2b';
        gc.fillRect(0, 0, canvas.width, canvas.height);

        const { cellSize, cols, rows, offsetX, offsetY } = grid;

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

        Object.values(objects).forEach(obj => {
            const x = offsetX + obj.col * cellSize;
            const y = offsetY + obj.row * cellSize;
            const w = obj.width * cellSize;
            const h = obj.height * cellSize;

            gc.fillStyle = '#8B4513';
            gc.fillRect(x, y, w, h);
            gc.strokeStyle = '#5a2d0c';
            gc.strokeRect(x, y, w, h);
        });

        Object.values(tokens).forEach(token => {
            const x = offsetX + token.col * cellSize;
            const y = offsetY + token.row * cellSize;

            const isMyToken = token.ownerId === myPlayerId;
            gc.fillStyle = isMyToken ? '#5cb85c' : '#4a90d9';
            gc.beginPath();
            gc.arc(
                x + cellSize / 2,
                y + cellSize / 2,
                cellSize / 2 - 4,
                0, Math.PI * 2
            );
            gc.fill();

            gc.fillStyle = 'white';
            gc.font = '10px sans-serif';
            gc.textAlign = 'center';
            gc.fillText(token.name, x + cellSize / 2, y + cellSize / 2 + 4);
        });
    }, [grid, tokens, objects, myPlayerId]);

    useEffect(() => {
        render();
    }, [render]);

    if (!grid?.cellSize) {
        return null;
    }

    const onTouchStart = (e: React.TouchEvent) => {
        const touch = e.touches[0];
        const canvas = canvasRef.current!;
        const rect = canvas.getBoundingClientRect();
        const x = touch.clientX - rect.left;
        const y = touch.clientY - rect.top;

        mousePos.current = { x, y };

        const { cellSize, offsetX, offsetY } = grid;
        const col = Math.floor((x - offsetX) / cellSize);
        const row = Math.floor((y - offsetY) / cellSize);

        const token = Object.values(tokens).find(
            t => t.col === col && t.row === row && t.ownerId === myPlayerId
        );

        if (token) {
            dragging.current = {
                token,
                offsetX: x - (offsetX + col * cellSize),
                offsetY: y - (offsetY + row * cellSize),
            };
        }
    };

    const onTouchMove = (e: React.TouchEvent) => {
        if (!dragging.current) return;
        e.preventDefault();
        const touch = e.touches[0];
        const canvas = canvasRef.current!;
        const rect = canvas.getBoundingClientRect();
        mousePos.current = {
            x: touch.clientX - rect.left,
            y: touch.clientY - rect.top,
        };
        render();

        const gc = canvas.getContext('2d')!;
        const { cellSize } = grid;
        const x = mousePos.current.x - dragging.current.offsetX;
        const y = mousePos.current.y - dragging.current.offsetY;

        gc.fillStyle = 'rgba(92, 184, 92, 0.7)';
        gc.beginPath();
        gc.arc(x + cellSize / 2, y + cellSize / 2, cellSize / 2 - 4, 0, Math.PI * 2);
        gc.fill();
    };

    const finishDrag = () => {
        if (!dragging.current) return;

        const { cellSize, offsetX, offsetY, cols, rows } = grid;
        const x = mousePos.current.x;
        const y = mousePos.current.y;

        let newCol = Math.floor((x - offsetX) / cellSize);
        let newRow = Math.floor((y - offsetY) / cellSize);

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

    const onTouchEnd = () => {
        finishDrag();
    };

    const onMouseDown = (e: React.MouseEvent) => {
        if (e.button !== 0) return;
        const canvas = canvasRef.current!;
        const rect = canvas.getBoundingClientRect();
        const x = e.clientX - rect.left;
        const y = e.clientY - rect.top;

        mousePos.current = { x, y };

        const { cellSize, offsetX, offsetY } = grid;
        const col = Math.floor((x - offsetX) / cellSize);
        const row = Math.floor((y - offsetY) / cellSize);

        const token = Object.values(tokens).find(
            t => t.col === col && t.row === row && t.ownerId === myPlayerId
        );

        if (token) {
            e.preventDefault();
            dragging.current = {
                token,
                offsetX: x - (offsetX + col * cellSize),
                offsetY: y - (offsetY + row * cellSize),
            };
        }
    };

    const onMouseMove = (e: React.MouseEvent) => {
        if (!dragging.current) return;
        const canvas = canvasRef.current!;
        const rect = canvas.getBoundingClientRect();
        mousePos.current = {
            x: e.clientX - rect.left,
            y: e.clientY - rect.top,
        };
        render();

        const gc = canvas.getContext('2d')!;
        const { cellSize } = grid;
        const x = mousePos.current.x - dragging.current.offsetX;
        const y = mousePos.current.y - dragging.current.offsetY;

        gc.fillStyle = 'rgba(92, 184, 92, 0.7)';
        gc.beginPath();
        gc.arc(x + cellSize / 2, y + cellSize / 2, cellSize / 2 - 4, 0, Math.PI * 2);
        gc.fill();
    };

    const onMouseUp = () => {
        finishDrag();
    };

    const onMouseLeave = () => {
        if (dragging.current) finishDrag();
    };

    const width = grid.offsetX + grid.cols * grid.cellSize;
    const height = grid.offsetY + grid.rows * grid.cellSize;

    return (
        <div style={{ overflow: 'auto', width: '100vw', height: '100vh' }}>
            <canvas
                ref={canvasRef}
                width={width}
                height={height}
                onTouchStart={onTouchStart}
                onTouchMove={onTouchMove}
                onTouchEnd={onTouchEnd}
                onMouseDown={onMouseDown}
                onMouseMove={onMouseMove}
                onMouseUp={onMouseUp}
                onMouseLeave={onMouseLeave}
                style={{ display: 'block', touchAction: 'none', cursor: 'crosshair' }}
            />
        </div>
    );
}
