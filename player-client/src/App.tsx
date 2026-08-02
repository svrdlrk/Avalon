import { useCallback, useEffect, useRef, useState } from 'react';
import BattleMap from './components/BattleMap';
import ConnectionPanel from './components/ConnectionPanel';
import InitiativeBar from './components/InitiativeBar';
import ErrorBoundary from './components/ErrorBoundary';
import JoinSessionScreen from './components/JoinSessionScreen';
import { DEFAULT_LAUNCHER_CONTROL_URL, DEFAULT_SERVER_BASE_URL, suggestServerBaseUrl } from './config/runtime';
import { useGameStore } from './store/gameStore';
import { wsClient } from './net/wsClient';

const launcherControlUrl = DEFAULT_LAUNCHER_CONTROL_URL;

function notifyLauncher(endpoint: 'client-closed' | 'client-heartbeat') {
    if (!launcherControlUrl) return;

    const url = `${launcherControlUrl.replace(/\/+$/, '')}/launcher/${endpoint}?client=player`;
    const body = new Blob([], { type: 'text/plain' });

    if (endpoint === 'client-closed' && navigator.sendBeacon) {
        navigator.sendBeacon(url, body);
        return;
    }

    fetch(url, {
        method: 'POST',
        keepalive: true,
        mode: 'cors',
        credentials: 'omit',
    }).catch(() => undefined);
}

function dispatchMapCenterSelected() {
    window.dispatchEvent(new Event('avalon-map:center-selected'));
}

function App() {
    const [chromeCollapsed, setChromeCollapsed] = useState(() => typeof window !== 'undefined' ? window.matchMedia('(max-width: 760px)').matches : false);
    const [isNarrowViewport, setIsNarrowViewport] = useState(() => typeof window !== 'undefined' ? window.matchMedia('(max-width: 960px)').matches : false);
    const [hudNotice, setHudNotice] = useState<string | null>(null);
    const hudNoticeTimer = useRef<number | null>(null);

    useEffect(() => {
        const onBeforeUnload = () => notifyLauncher('client-closed');
        const onPageHide = () => notifyLauncher('client-closed');

        window.addEventListener('beforeunload', onBeforeUnload);
        window.addEventListener('pagehide', onPageHide);

        notifyLauncher('client-heartbeat');
        const heartbeat = window.setInterval(() => notifyLauncher('client-heartbeat'), 5000);

        return () => {
            window.removeEventListener('beforeunload', onBeforeUnload);
            window.removeEventListener('pagehide', onPageHide);
            window.clearInterval(heartbeat);
        };
    }, []);

    useEffect(() => {
        if (typeof window === 'undefined') return;

        const compactQuery = window.matchMedia('(max-width: 760px)');
        const narrowQuery = window.matchMedia('(max-width: 960px)');
        const update = () => {
            setChromeCollapsed(compactQuery.matches);
            setIsNarrowViewport(narrowQuery.matches);
        };

        update();
        compactQuery.addEventListener('change', update);
        narrowQuery.addEventListener('change', update);

        return () => {
            compactQuery.removeEventListener('change', update);
            narrowQuery.removeEventListener('change', update);
        };
    }, []);

    const sessionId = useGameStore((state) => state.sessionId);
    const myPlayerId = useGameStore((state) => state.myPlayerId);
    const selectedTokenId = useGameStore((state) => state.selectedTokenId);
    const tokens = useGameStore((state) => state.tokens);
    const players = useGameStore((state) => state.players);
    const initiative = useGameStore((state) => state.initiative);
    const viewerRole = useGameStore((state) => state.viewerRole);
    const commandError = useGameStore((state) => state.commandError);
    const clearSelection = useGameStore((state) => state.clearSelection);

    const selectedToken = selectedTokenId ? tokens[selectedTokenId] : null;
    const selectedOwner = selectedToken?.ownerId ? players[selectedToken.ownerId] : null;
    const playerCount = Object.values(players).filter((player) => player.role === 'PLAYER').length;
    const currentTurn = initiative?.entries?.[initiative.currentIndex] ?? null;
    const selectedTokenIsPlayerOwned = Boolean(selectedToken?.ownerId);
    const selectedTokenHpText = selectedToken
        ? selectedTokenIsPlayerOwned
            ? `${selectedToken.hp}/${selectedToken.maxHp || '—'}`
            : '??/??'
        : '—';

    const isConnected = Boolean(sessionId && myPlayerId);

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const projectorToken = params.get('projectorToken');
        const projectorSessionId = params.get('sessionId') ?? params.get('session');
        if (!projectorToken || !projectorSessionId || isConnected) return;
        wsClient.connect(
            suggestServerBaseUrl(params.get('serverUrl') ?? params.get('server') ?? DEFAULT_SERVER_BASE_URL),
            projectorSessionId,
            'Projector',
            false,
            () => undefined,
            projectorToken,
        );
    }, [isConnected]);

    const pushHudNotice = useCallback((message: string) => {
        setHudNotice(message);
        if (hudNoticeTimer.current != null) {
            window.clearTimeout(hudNoticeTimer.current);
        }
        hudNoticeTimer.current = window.setTimeout(() => {
            setHudNotice(null);
            hudNoticeTimer.current = null;
        }, 1800);
    }, []);

    const copySelectedTokenDetails = useCallback(async () => {
        if (!selectedToken) {
            pushHudNotice('No token selected');
            return;
        }

        const ownerLabel = selectedOwner?.name ?? (selectedToken.ownerId ?? 'NPC');
        const payload = [
            `Token: ${selectedToken.name}`,
            `Owner: ${ownerLabel}`,
            `HP: ${selectedTokenIsPlayerOwned ? `${selectedToken.hp}/${selectedToken.maxHp || '—'}` : '??/??'}`,
            `Grid: ${selectedToken.col}, ${selectedToken.row}`,
            `Size: ${Math.max(1, selectedToken.gridSize ?? 1)}×${Math.max(1, selectedToken.gridSize ?? 1)}`,
        ].join('\n');

        try {
            await navigator.clipboard.writeText(payload);
            pushHudNotice('Token details copied');
        } catch {
            pushHudNotice('Copy failed');
        }
    }, [pushHudNotice, selectedOwner?.name, selectedToken, selectedTokenIsPlayerOwned]);

    useEffect(() => () => {
        if (hudNoticeTimer.current != null) {
            window.clearTimeout(hudNoticeTimer.current);
        }
    }, []);

    useEffect(() => {
        if (typeof window === 'undefined') return;

        const onKeyDown = (event: KeyboardEvent) => {
            if (event.key !== 'Escape') return;
            clearSelection();
        };

        window.addEventListener('keydown', onKeyDown);
        return () => window.removeEventListener('keydown', onKeyDown);
    }, [clearSelection]);

    const handleCloseBattleSheet = useCallback(() => {
        clearSelection();
    }, [clearSelection]);

    if (!isConnected) {
        return (
            <ErrorBoundary>
                <JoinSessionScreen />
            </ErrorBoundary>
        );
    }

    return (
        <ErrorBoundary>
            <div className={`app-shell ${viewerRole === 'OBSERVER' ? 'app-shell--projector' : ''} ${chromeCollapsed ? 'app-shell--immersive' : ''} ${isNarrowViewport ? 'app-shell--compact' : ''}`}>
                <div className="app-shell__ambient app-shell__ambient--a" aria-hidden="true" />
                <div className="app-shell__ambient app-shell__ambient--b" aria-hidden="true" />

                <header className="battle-chrome" aria-label="Battle HUD">
                    <div className="battle-chrome__brand">
                        <div className="battle-chrome__sigil">A</div>
                        <div className="battle-chrome__text">
                            <div className="battle-chrome__title">Avalon DnD</div>
                            <div className="battle-chrome__subtitle">{viewerRole === 'OBSERVER' ? 'Projector view · read only' : 'Player view · tactical HUD'}</div>
                        </div>
                    </div>

                    <div className="battle-chrome__center">
                        <div className="hud-chip">Players {playerCount}</div>
                        {currentTurn && <div className="hud-chip hud-chip--turn">Turn · {currentTurn.name}</div>}
                        {hudNotice && <div className="hud-chip hud-chip--notice">{hudNotice}</div>}
                        {commandError && <div className="hud-chip hud-chip--notice">{commandError}</div>}
                    </div>

                    <div className="battle-chrome__actions">
                        <div className="hud-chip hud-chip--strong battle-chrome__hint">Tap token · drag to move · pinch to zoom</div>
                        <button className="hud-button hud-button--ghost" onClick={() => setChromeCollapsed((value) => !value)} title="Toggle chrome">
                            {chromeCollapsed ? 'Show HUD' : 'Cinematic mode'}
                        </button>
                    </div>
                </header>

                <main className="app-shell__stage">
                    <BattleMap />
                </main>

                <InitiativeBar />

                {selectedToken && viewerRole !== 'OBSERVER' && (
                    <aside className={`battle-sheet ${isNarrowViewport ? 'battle-sheet--mobile' : ''}`} aria-label="Selected token">
                        <div className="battle-sheet__card battle-sheet__card--selected">
                            <div className="battle-sheet__topline">
                                <div>
                                    <div className="battle-sheet__eyebrow">Selected token</div>
                                    <h2 className="battle-sheet__title">{selectedToken.name}</h2>
                                    <p className="battle-sheet__meta">
                                        {selectedOwner?.name ?? 'NPC'} · Grid {selectedToken.col}, {selectedToken.row}
                                    </p>
                                </div>
                                <button className="hud-button hud-button--ghost battle-sheet__close" type="button" onClick={handleCloseBattleSheet}>
                                    Hide
                                </button>
                            </div>

                            <div className="battle-sheet__stats">
                                <div className="stat-pill">
                                    <span className="stat-pill__label">HP</span>
                                    <span className="stat-pill__value">{selectedTokenHpText}</span>
                                </div>
                                <div className="stat-pill">
                                    <span className="stat-pill__label">Size</span>
                                    <span className="stat-pill__value">{Math.max(1, selectedToken.gridSize ?? 1)}×{Math.max(1, selectedToken.gridSize ?? 1)}</span>
                                </div>
                                <div className="stat-pill">
                                    <span className="stat-pill__label">Owner</span>
                                    <span className="stat-pill__value">{selectedOwner?.name ?? 'NPC'}</span>
                                </div>
                            </div>

                            <div className="battle-sheet__actions">
                                <button className="primary-action" onClick={dispatchMapCenterSelected}>
                                    Center on token
                                </button>
                                <button className="secondary-action" onClick={copySelectedTokenDetails}>
                                    Copy details
                                </button>
                            </div>
                        </div>
                    </aside>
                )}

                <ConnectionPanel />
            </div>
        </ErrorBoundary>
    );
}

export default App;
