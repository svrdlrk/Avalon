import React, { useState } from 'react';
import { wsClient } from '../net/wsClient';
import { useGameStore } from '../store/gameStore';

const ConnectionPanel: React.FC = () => {
    const [sessionId, setSessionId] = useState('');
    const [playerName, setPlayerName] = useState('');
    const [isDm, setIsDm] = useState(false);
    const [isConnected, setIsConnected] = useState(false);
    const { myPlayerId } = useGameStore();

    const handleConnect = () => {
        if (!sessionId || !playerName) return;

        wsClient.connect(
            'http://localhost:8080',   // можно потом вынести в env
            sessionId.trim(),
            playerName.trim(),
            isDm,                     // игрок (не DM)
            () => {
                setIsConnected(true);
                console.log('✅ Подключено к сессии', sessionId);
            }
        );
    };

    const handleDisconnect = () => {
        wsClient.disconnect();
        setIsConnected(false);
    };

    if (isConnected && myPlayerId) {
        return (
            <div className="absolute top-4 left-4 bg-zinc-900 p-3 rounded border border-zinc-700 z-50 flex items-center gap-3">
                <div className="text-green-400">● Подключено</div>
                <div className="text-sm text-zinc-400">
                    Сессия: <span className="font-mono">{sessionId}</span>
                </div>
                <button
                    onClick={handleDisconnect}
                    className="px-3 py-1 bg-red-600 hover:bg-red-700 rounded text-sm"
                >
                    Отключиться
                </button>
            </div>
        );
    }

    return (
        <div className="absolute top-4 left-4 bg-zinc-900 p-6 rounded border border-zinc-700 z-50 w-80">
            <h2 className="text-lg font-bold mb-4">Подключение к бою</h2>

            <input
                type="text"
                placeholder="ID сессии"
                value={sessionId}
                onChange={(e) => setSessionId(e.target.value)}
                className="w-full mb-3 px-4 py-2 bg-zinc-800 border border-zinc-700 rounded focus:outline-none focus:border-blue-500"
            />

            <input
                type="text"
                placeholder="Ваше имя"
                value={playerName}
                onChange={(e) => setPlayerName(e.target.value)}
                className="w-full mb-4 px-4 py-2 bg-zinc-800 border border-zinc-700 rounded focus:outline-none focus:border-blue-500"
            />

            <input
                type="checkbox"
                checked={isDm}
                onChange={(e) => setIsDm(e.target.checked)}
            />
            <span>Join as DM</span>

            <button
                onClick={handleConnect}
                disabled={!sessionId || !playerName}
                className="w-full py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-zinc-700 rounded font-medium disabled:cursor-not-allowed"
            >
                Присоединиться
            </button>
        </div>
    );
};

export default ConnectionPanel;