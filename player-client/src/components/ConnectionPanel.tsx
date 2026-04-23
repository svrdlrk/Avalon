import React, { useEffect, useRef, useState } from 'react';
import { wsClient } from '../net/wsClient';
import { useGameStore } from '../store/gameStore';

const STORAGE_KEYS = {
    serverUrl: 'avalon.connection.serverUrl',
    sessionId: 'avalon.connection.sessionId',
    playerName: 'avalon.connection.playerName',
    isDm: 'avalon.connection.isDm',
    autoConnect: 'avalon.connection.autoConnect',
};

const s: Record<string, React.CSSProperties> = {
    overlay: {
        position: 'fixed',
        top: '16px',
        left: '16px',
        zIndex: 50,
    },
    connectedBar: {
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        background: '#18181b',
        border: '1px solid #3f3f46',
        borderRadius: '8px',
        padding: '10px 16px',
        fontSize: '14px',
    },
    dot: {
        color: '#22c55e',
        fontWeight: 700,
    },
    sessionHint: {
        color: '#a1a1aa',
        fontFamily: 'monospace',
    },
    disconnectBtn: {
        padding: '4px 12px',
        background: '#dc2626',
        color: '#fff',
        border: 'none',
        borderRadius: '6px',
        cursor: 'pointer',
        fontSize: '13px',
    },
    panel: {
        background: '#18181b',
        border: '1px solid #3f3f46',
        borderRadius: '10px',
        padding: '24px',
        width: '320px',
        boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
    },
    title: {
        margin: '0 0 20px',
        fontSize: '18px',
        fontWeight: 600,
        color: '#f4f4f5',
    },
    input: {
        display: 'block',
        width: '100%',
        marginBottom: '12px',
        padding: '10px 14px',
        background: '#27272a',
        border: '1px solid #3f3f46',
        borderRadius: '7px',
        color: '#f4f4f5',
        fontSize: '15px',
        outline: 'none',
        boxSizing: 'border-box',
    },
    checkRow: {
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        marginBottom: '16px',
        color: '#a1a1aa',
        fontSize: '14px',
        cursor: 'pointer',
    },
    connectBtn: {
        display: 'block',
        width: '100%',
        padding: '12px',
        background: '#2563eb',
        color: '#fff',
        border: 'none',
        borderRadius: '7px',
        fontSize: '16px',
        fontWeight: 600,
        cursor: 'pointer',
    },
    connectBtnDisabled: {
        background: '#3f3f46',
        cursor: 'not-allowed',
    },
};

const ConnectionPanel: React.FC = () => {
    const [serverUrl, setServerUrl] = useState('http://localhost:8080');
    const [sessionId, setSessionId] = useState('');
    const [playerName, setPlayerName] = useState('');
    const [isDm, setIsDm] = useState(false);
    const [isConnected, setIsConnected] = useState(false);
    const [status, setStatus] = useState('');
    const autoConnectAttempted = useRef(false);

    const { myPlayerId } = useGameStore();

    useEffect(() => {
        try {
            const savedServerUrl = localStorage.getItem(STORAGE_KEYS.serverUrl);
            const savedSessionId = localStorage.getItem(STORAGE_KEYS.sessionId);
            const savedPlayerName = localStorage.getItem(STORAGE_KEYS.playerName);
            const savedIsDm = localStorage.getItem(STORAGE_KEYS.isDm);
            if (savedServerUrl) setServerUrl(savedServerUrl);
            if (savedSessionId) setSessionId(savedSessionId);
            if (savedPlayerName) setPlayerName(savedPlayerName);
            if (savedIsDm != null) setIsDm(savedIsDm === 'true');

            const shouldAutoConnect = localStorage.getItem(STORAGE_KEYS.autoConnect) === 'true';
            if (shouldAutoConnect && savedServerUrl && savedSessionId && savedPlayerName && !autoConnectAttempted.current) {
                autoConnectAttempted.current = true;
                setTimeout(() => {
                    wsClient.connect(
                        savedServerUrl,
                        savedSessionId,
                        savedPlayerName,
                        savedIsDm === 'true',
                        () => {
                            setIsConnected(true);
                            setStatus('');
                        },
                    );
                }, 0);
            }
        } catch {
            // ignore storage errors
        }
    }, []);

    useEffect(() => {
        try {
            localStorage.setItem(STORAGE_KEYS.serverUrl, serverUrl);
            localStorage.setItem(STORAGE_KEYS.sessionId, sessionId);
            localStorage.setItem(STORAGE_KEYS.playerName, playerName);
            localStorage.setItem(STORAGE_KEYS.isDm, String(isDm));
        } catch {
            // ignore storage errors
        }
    }, [serverUrl, sessionId, playerName, isDm]);

    const handleConnect = () => {
        if (!sessionId.trim() || !playerName.trim()) {
            setStatus('Заполни все поля');
            return;
        }
        setStatus('Подключение...');
        try {
            localStorage.setItem(STORAGE_KEYS.autoConnect, 'true');
        } catch {
            // ignore storage errors
        }
        wsClient.connect(
            serverUrl.trim(),
            sessionId.trim(),
            playerName.trim(),
            isDm,
            () => {
                setIsConnected(true);
                setStatus('');
            }
        );
    };

    const handleDisconnect = () => {
        wsClient.disconnect();
        setIsConnected(false);
        try {
            localStorage.setItem(STORAGE_KEYS.autoConnect, 'false');
        } catch {
            // ignore storage errors
        }
    };

    if (isConnected && myPlayerId) {
        return (
            <div style={s.overlay}>
                <div style={s.connectedBar}>
                    <span style={s.dot}>● Подключено</span>
                    <span style={s.sessionHint}>
                        {sessionId.slice(0, 8)}…
                    </span>
                    <button style={s.disconnectBtn} onClick={handleDisconnect}>
                        Выйти
                    </button>
                </div>
            </div>
        );
    }

    const canConnect = sessionId.trim().length > 0 && playerName.trim().length > 0;

    return (
        <div style={s.overlay}>
            <div style={s.panel}>
                <h2 style={s.title}>⚔ Avalon DnD</h2>

                <input
                    style={s.input}
                    type="text"
                    placeholder="Адрес сервера"
                    value={serverUrl}
                    onChange={e => setServerUrl(e.target.value)}
                />

                <input
                    style={s.input}
                    type="text"
                    placeholder="ID сессии (от DM)"
                    value={sessionId}
                    onChange={e => setSessionId(e.target.value)}
                />

                <input
                    style={s.input}
                    type="text"
                    placeholder="Твоё имя"
                    value={playerName}
                    onChange={e => setPlayerName(e.target.value)}
                    onKeyDown={e => e.key === 'Enter' && canConnect && handleConnect()}
                />

                <label style={s.checkRow}>
                    <input
                        type="checkbox"
                        checked={isDm}
                        onChange={e => setIsDm(e.target.checked)}
                    />
                    Войти как DM
                </label>

                <button
                    style={{
                        ...s.connectBtn,
                        ...(canConnect ? {} : s.connectBtnDisabled),
                    }}
                    disabled={!canConnect}
                    onClick={handleConnect}
                >
                    Присоединиться
                </button>

                {status && (
                    <p style={{ marginTop: '12px', color: '#a1a1aa', fontSize: '14px', textAlign: 'center' }}>
                        {status}
                    </p>
                )}
            </div>
        </div>
    );
};

export default ConnectionPanel;
