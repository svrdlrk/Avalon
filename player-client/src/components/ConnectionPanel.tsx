import React, { useState } from 'react';
import { useGameStore } from '../store/gameStore';
import { useConnectionState } from '../hooks/useConnectionState';
import ConnectionForm from './ConnectionForm';

const ConnectionPanel: React.FC = () => {
    const {
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
    } = useConnectionState();

    const [isOpen, setIsOpen] = useState(false);
    const myPlayerId = useGameStore((state) => state.myPlayerId);

    if (isConnected) {
        return (
            <aside className={`connection-dock connection-dock--mini ${isOpen ? 'connection-dock--open' : ''}`}>
                <button
                    type="button"
                    className="connection-dock__trigger"
                    onClick={() => setIsOpen((value) => !value)}
                    aria-expanded={isOpen}
                    aria-label="Connection details"
                    title="Connection details"
                >
                    <span className="connection-dock__dot" aria-hidden="true" />
                    <span className="connection-dock__trigger-label">Net</span>
                </button>

                {isOpen && (
                    <div className="connection-dock__popover">
                        <div className="connection-dock__row">
                            <div>
                                <div className="connection-dock__badge">Connected</div>
                                <div className="connection-dock__meta">Player mode · {connectionSummary}</div>
                            </div>
                        </div>

                        <div className="connection-dock__chips">
                            <span className="hud-chip">Player {playerName.trim() || '—'}</span>
                            <span className="hud-chip hud-chip--strong">ID {myPlayerId?.slice(0, 6) ?? '—'}</span>
                        </div>

                        <div className="connection-dock__buttons">
                            <button type="button" className="hud-button" onClick={() => void copySessionId()}>Copy ID</button>
                            <button type="button" className="hud-button hud-button--danger" onClick={disconnect}>Leave</button>
                        </div>
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
                    autoConnect={autoConnect}
                    canConnect={canConnect}
                    status={status}
                    primaryLabel="Join session"
                    secondaryLabel="The compact dock stays readable on small screens and keeps reconnect friction low."
                    onServerUrlChange={setServerUrl}
                    onSessionIdChange={setSessionId}
                    onPlayerNameChange={setPlayerName}
                    onAutoConnectChange={setAutoConnect}
                    onConnect={() => void connect()}
                    showSessionAction={false}
                />
            </div>
        </aside>
    );
};

export default ConnectionPanel;
