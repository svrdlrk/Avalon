import React, { useMemo, useState } from 'react';
import { wsClient } from '../net/wsClient';
import { useGameStore } from '../store/gameStore';
import { useConnectionState } from '../hooks/useConnectionState';
import ConnectionForm from './ConnectionForm';

const ConnectionPanel: React.FC = () => {
    const {
        serverUrl,
        sessionId,
        playerName,
        isDm,
        autoConnect,
        status,
        isConnected,
        canConnect,
        connectionSummary,
        setServerUrl,
        setSessionId,
        setPlayerName,
        setIsDm,
        setAutoConnect,
        connect,
        disconnect,
        copySessionId,
    } = useConnectionState();

    const [showAdvanced, setShowAdvanced] = useState(false);
    const myPlayerId = useGameStore((state) => state.myPlayerId);
    const players = useGameStore((state) => state.players);
    const visibilityShareSuggestions = useGameStore((state) => state.visibilityShareSuggestions);

    const pendingSuggestions = useMemo(() => visibilityShareSuggestions ?? [], [visibilityShareSuggestions]);

    const approveSuggestion = (suggestionId: string) => {
        wsClient.approveVisibilityShare(suggestionId);
    };

    if (isConnected) {
        return (
            <aside className="connection-dock connection-dock--compact">
                <div className="connection-dock__row">
                    <div>
                        <div className="connection-dock__badge">Connected</div>
                        <div className="connection-dock__meta">
                            {isDm ? 'DM mode' : 'Player mode'} · {connectionSummary}
                        </div>
                    </div>
                    <button
                        type="button"
                        className="hud-button hud-button--ghost"
                        onClick={() => setShowAdvanced((value) => !value)}
                        aria-expanded={showAdvanced}
                    >
                        {showAdvanced ? 'Less' : 'More'}
                    </button>
                </div>

                <div className="connection-dock__chips">
                    <span className="hud-chip">Player {playerName.trim() || '—'}</span>
                    <span className="hud-chip hud-chip--strong">ID {myPlayerId?.slice(0, 6) ?? '—'}</span>
                </div>

                {showAdvanced && (
                    <div className="connection-dock__stack">
                        <div className="connection-dock__buttons">
                            <button type="button" className="hud-button" onClick={() => void copySessionId()}>Copy ID</button>
                            <button type="button" className="hud-button hud-button--danger" onClick={disconnect}>Leave</button>
                        </div>

                        {isDm && pendingSuggestions.length > 0 && (
                            <div className="connection-dock__suggestions">
                                <div className="connection-dock__section-title">Visibility suggestions</div>
                                {pendingSuggestions.slice(0, 3).map((item) => {
                                    const names = item.playerIds
                                        .map((id) => players[id]?.name ?? id.slice(0, 6))
                                        .join(', ');

                                    return (
                                        <div key={item.suggestionId} className="connection-dock__suggestion">
                                            <div className="connection-dock__suggestion-title">{names}</div>
                                            <div className="connection-dock__suggestion-body">
                                                {item.reason ?? 'Information may become shared'}
                                            </div>
                                            <div className="connection-dock__buttons">
                                                <span className="hud-chip">
                                                    {item.trigger === 'room'
                                                        ? 'Auto: same room'
                                                        : item.trigger === 'distance'
                                                            ? 'Auto: close enough'
                                                            : item.autoSuggested
                                                                ? 'Auto-suggested'
                                                                : 'Manual'}
                                                </span>
                                                <button
                                                    type="button"
                                                    className="hud-button hud-button--accent"
                                                    onClick={() => approveSuggestion(item.suggestionId)}
                                                >
                                                    Approve
                                                </button>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                )}

            </aside>
        );
    }

    return (
        <aside className="connection-panel">
            <div className="connection-panel__frame">
                <div className="connection-panel__hero">
                    <div className="connection-panel__eyebrow">Battle access</div>
                    <h2 className="connection-panel__title">Avalon DnD</h2>
                    <p className="connection-panel__subtitle">
                        Connect to a session and keep the map responsive, compact, and readable during live play.
                    </p>
                </div>

                <ConnectionForm
                    compact
                    serverUrl={serverUrl}
                    sessionId={sessionId}
                    playerName={playerName}
                    isDm={isDm}
                    autoConnect={autoConnect}
                    canConnect={canConnect}
                    status={status}
                    primaryLabel="Join session"
                    secondaryLabel="The compact dock stays readable on small screens and keeps reconnect friction low."
                    onServerUrlChange={setServerUrl}
                    onSessionIdChange={setSessionId}
                    onPlayerNameChange={setPlayerName}
                    onIsDmChange={setIsDm}
                    onAutoConnectChange={setAutoConnect}
                    onConnect={() => void connect()}
                    showSessionAction={false}
                />
            </div>
        </aside>
    );
};

export default ConnectionPanel;
