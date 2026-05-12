import React, { useEffect, useMemo, useRef, useState } from 'react';
import { wsClient } from '../net/wsClient';
import { useGameStore } from '../store/gameStore';
import { DEFAULT_SERVER_BASE_URL } from '../config/runtime';

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
        maxWidth: 'min(92vw, 420px)',
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
        background: 'linear-gradient(180deg, #1a1a22 0%, #121218 100%)',
        border: '1px solid #3f3f46',
        borderRadius: '18px',
        padding: '20px',
        width: '100%',
        boxShadow: '0 12px 40px rgba(0,0,0,0.45)',
        backdropFilter: 'blur(12px)',
    },
    title: {
        margin: '0 0 8px',
        fontSize: '20px',
        fontWeight: 700,
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
    const [serverUrl, setServerUrl] = useState(DEFAULT_SERVER_BASE_URL);
    const [sessionId, setSessionId] = useState('');
    const [playerName, setPlayerName] = useState('');
    const [isDm, setIsDm] = useState(false);
    const [isConnected, setIsConnected] = useState(false);
    const [status, setStatus] = useState('');
    const autoConnectAttempted = useRef(false);

    const myPlayerId = useGameStore((s) => s.myPlayerId);
    const players = useGameStore((s) => s.players);
    const visibilityShareSuggestions = useGameStore((s) => s.visibilityShareSuggestions);

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
            serverUrl.trim() || DEFAULT_SERVER_BASE_URL,
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

    const approveSuggestion = (suggestionId: string) => {
        wsClient.approveVisibilityShare(suggestionId);
    };

    const pendingSuggestions = useMemo(() => visibilityShareSuggestions ?? [], [visibilityShareSuggestions]);

    if (isConnected && myPlayerId) {
        return (
            <div style={s.overlay}>
                <div style={{ display: 'grid', gap: '10px' }}>
                    <div style={s.connectedBar}>
                        <span style={s.dot}>● Подключено</span>
                        <span style={s.sessionHint}>
                            {sessionId.slice(0, 8)}…
                        </span>
                        <button
                            style={{
                                ...s.disconnectBtn,
                                background: '#374151',
                            }}
                            onClick={() => {
                                try {
                                    navigator.clipboard?.writeText(sessionId);
                                    setStatus('ID сессии скопирован');
                                } catch {
                                    setStatus('Не удалось скопировать ID');
                                }
                            }}
                        >
                            Скопировать ID
                        </button>
                        <button style={s.disconnectBtn} onClick={handleDisconnect}>
                            Выйти
                        </button>
                    </div>

                    {isDm && pendingSuggestions.length > 0 && (
                        <div style={{ ...s.panel, width: '380px' }}>
                            <h3 style={{ margin: '0 0 12px', fontSize: '16px', color: '#f4f4f5' }}>
                                Подсказки для общего обзора
                            </h3>
                            <div style={{ display: 'grid', gap: '10px' }}>
                                {pendingSuggestions.map((item) => {
                                    const names = item.playerIds
                                        .map((id) => players[id]?.name ?? id.slice(0, 8))
                                        .join(', ');
                                    return (
                                        <div key={item.suggestionId} style={{ border: '1px solid #3f3f46', borderRadius: '8px', padding: '10px', background: '#101014' }}>
                                            <div style={{ color: '#e4e4e7', fontSize: '14px', marginBottom: '6px' }}>
                                                {names}
                                            </div>
                                            <div style={{ color: '#a1a1aa', fontSize: '13px', marginBottom: '6px' }}>
                                                {item.reason ?? 'Информация может стать общей'}
                                            </div>
                                            <div style={{ color: '#71717a', fontSize: '12px', marginBottom: '10px' }}>
                                                {item.trigger === 'room' ? 'Авто: одна комната'
                                                    : item.trigger === 'distance' ? 'Авто: близко'
                                                    : item.autoSuggested ? 'Авто-подсказка' : 'Ручная подсказка'}
                                            </div>
                                            <button
                                                style={{
                                                    ...s.connectBtn,
                                                    padding: '8px 10px',
                                                    fontSize: '14px',
                                                }}
                                                onClick={() => approveSuggestion(item.suggestionId)}
                                            >
                                                Подтвердить общий обзор
                                            </button>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )}
                </div>
                </div>
        );
    }

    const canConnect = sessionId.trim().length > 0 && playerName.trim().length > 0;

    return (
        <div style={s.overlay}>
            <div style={s.panel}>
                <h2 style={s.title}>⚔ Avalon DnD</h2>
                <p style={{ margin: '0 0 16px', color: '#a1a1aa', fontSize: '13px', lineHeight: 1.4 }}>
                    Подключись к сессии, чтобы войти в игру и синхронизироваться с сервером.
                </p>

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
