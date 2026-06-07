import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DEFAULT_SERVER_BASE_URL, suggestServerBaseUrl } from '../config/runtime';
import { wsClient } from '../net/wsClient';
import { useGameStore } from '../store/gameStore';

const STORAGE_KEYS = {
    serverUrl: 'avalon.connection.serverUrl',
    sessionId: 'avalon.connection.sessionId',
    playerName: 'avalon.connection.playerName',
    autoConnect: 'avalon.connection.autoConnect',
};

const DEFAULT_GRID = { cellSize: 64, cols: 20, rows: 20, offsetX: 0, offsetY: 0 };

function safeTrim(value: string): string {
    return value.trim();
}

function readSavedValue(key: string, fallback = ''): string {
    if (typeof window === 'undefined' || typeof localStorage === 'undefined') {
        return fallback;
    }
    try {
        return localStorage.getItem(key) ?? fallback;
    } catch {
        return fallback;
    }
}

function readInitialConnectionState() {
    const savedServerUrl = readSavedValue(STORAGE_KEYS.serverUrl, DEFAULT_SERVER_BASE_URL);
    const savedSessionId = readSavedValue(STORAGE_KEYS.sessionId);
    const savedPlayerName = readSavedValue(STORAGE_KEYS.playerName);
    const savedAutoConnect = readSavedValue(STORAGE_KEYS.autoConnect, 'false');
    const params = typeof window !== 'undefined' ? new URLSearchParams(window.location.search) : null;
    const queryServerUrl = params?.get('serverUrl') ?? params?.get('server') ?? '';
    const querySessionId = params?.get('sessionId') ?? params?.get('session') ?? '';

    return {
        serverUrl: suggestServerBaseUrl(queryServerUrl || savedServerUrl),
        sessionId: querySessionId || savedSessionId,
        playerName: savedPlayerName,
        autoConnect: savedAutoConnect === 'true',
    };
}

function persistConnectionState(serverUrl: string, sessionId: string, playerName: string, autoConnect: boolean) {
    if (typeof window === 'undefined' || typeof localStorage === 'undefined') {
        return;
    }
    try {
        localStorage.setItem(STORAGE_KEYS.serverUrl, serverUrl);
        localStorage.setItem(STORAGE_KEYS.sessionId, sessionId);
        localStorage.setItem(STORAGE_KEYS.playerName, playerName);
        localStorage.removeItem('avalon.connection.isDm');
        localStorage.setItem(STORAGE_KEYS.autoConnect, String(autoConnect));
    } catch {
        // Ignore storage failures.
    }
}

function resetGameStore() {
    useGameStore.setState({
        sessionId: null,
        myPlayerId: null,
        selectedTokenId: null,
        grid: DEFAULT_GRID,
        tokens: {},
        objects: {},
        players: {},
        backgroundUrl: null,
        initiative: null,
        visibility: null,
        terrainLayer: null,
        wallLayer: null,
    });
}

export interface UseConnectionStateResult {
    serverUrl: string;
    sessionId: string;
    playerName: string;
    autoConnect: boolean;
    status: string | null;
    isConnected: boolean;
    canConnect: boolean;
    connectionSummary: string;
    setServerUrl: (value: string) => void;
    setSessionId: (value: string) => void;
    setPlayerName: (value: string) => void;
    setAutoConnect: (value: boolean) => void;
    connect: () => boolean;
    disconnect: () => void;
    copySessionId: () => Promise<boolean>;
}

export function useConnectionState(): UseConnectionStateResult {
    const initial = useMemo(readInitialConnectionState, []);
    const [serverUrl, setServerUrl] = useState(() => initial.serverUrl);
    const [sessionId, setSessionId] = useState(() => initial.sessionId);
    const [playerName, setPlayerName] = useState(() => initial.playerName);
    const [autoConnect, setAutoConnect] = useState(() => initial.autoConnect);
    const [status, setStatus] = useState<string | null>(null);
    const autoConnectAttempted = useRef(false);
    const storeSessionId = useGameStore((state) => state.sessionId);
    const storePlayerId = useGameStore((state) => state.myPlayerId);
    const isConnected = storeSessionId != null && storePlayerId != null;

    useEffect(() => {
        persistConnectionState(serverUrl, sessionId, playerName, autoConnect);
    }, [serverUrl, sessionId, playerName, autoConnect]);

    const canConnect = safeTrim(sessionId).length > 0 && safeTrim(playerName).length > 0;

    const connectionSummary = useMemo(() => {
        const trimmed = safeTrim(sessionId);
        if (!trimmed) {
            return 'Not connected';
        }
        return `${trimmed.slice(0, 8)}${trimmed.length > 8 ? '…' : ''}`;
    }, [sessionId]);

    const connect = useCallback(() => {
        if (!canConnect) {
            setStatus('Enter session ID and name');
            return false;
        }

        const normalizedServer = suggestServerBaseUrl(serverUrl) || DEFAULT_SERVER_BASE_URL;
        const normalizedSessionId = safeTrim(sessionId);
        const normalizedPlayerName = safeTrim(playerName);

        setServerUrl(normalizedServer);
        setSessionId(normalizedSessionId);
        setPlayerName(normalizedPlayerName);
        persistConnectionState(normalizedServer, normalizedSessionId, normalizedPlayerName, autoConnect);
        setStatus('Connecting…');

        wsClient.connect(
            normalizedServer,
            normalizedSessionId,
            normalizedPlayerName,
            false,
            () => {
                setStatus(null);
            },
        );
        return true;
    }, [autoConnect, canConnect, serverUrl, sessionId, playerName]);

    useEffect(() => {
        if (!autoConnect) {
            autoConnectAttempted.current = false;
            return;
        }

        if (autoConnectAttempted.current || isConnected || !canConnect) {
            return;
        }

        autoConnectAttempted.current = true;
        const timeoutId = window.setTimeout(() => {
            connect();
        }, 0);

        return () => window.clearTimeout(timeoutId);
    }, [autoConnect, canConnect, connect, isConnected]);

    const disconnect = useCallback(() => {
        wsClient.disconnect();
        resetGameStore();
        setStatus('Disconnected');
        setAutoConnect(false);
    }, []);

    const copySessionId = useCallback(async () => {
        const value = safeTrim(sessionId);
        if (!value) {
            setStatus('No session ID to copy');
            return false;
        }

        try {
            if (!navigator.clipboard?.writeText) {
                throw new Error('Clipboard unavailable');
            }
            await navigator.clipboard.writeText(value);
            setStatus('Session ID copied');
            return true;
        } catch {
            setStatus('Copy failed');
            return false;
        }
    }, [sessionId]);

    return {
        serverUrl,
        sessionId,
        playerName,
        autoConnect,
        status,
        isConnected,
        canConnect,
        connectionSummary,
        setServerUrl,
        setSessionId,
        setPlayerName,
        setAutoConnect,
        connect,
        disconnect,
        copySessionId,
    };
}
