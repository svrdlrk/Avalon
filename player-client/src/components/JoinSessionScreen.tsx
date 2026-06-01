import React, { useMemo, useState } from 'react';
import { useGameStore } from '../store/gameStore';
import { useConnectionState } from '../hooks/useConnectionState';
import ConnectionForm from './ConnectionForm';

type LandingTab = 'battle' | 'settings';

const JoinSessionScreen: React.FC = () => {
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
        copySessionId,
    } = useConnectionState();

    const [activeTab, setActiveTab] = useState<LandingTab>('battle');
    const [hudVisible, setHudVisible] = useState(true);

    const players = useGameStore((state) => state.players);
    const playerCount = useMemo(() => Object.values(players).filter((player) => player.role === 'PLAYER').length, [players]);

    return (
        <div className="player-shell player-shell--landing">
            <div className="player-shell__ambient player-shell__ambient--a" aria-hidden="true" />
            <div className="player-shell__ambient player-shell__ambient--b" aria-hidden="true" />

            <div className="player-home">
                <header className="player-home__topbar">
                    <div className="player-home__brand">
                        <div className="player-home__sigil" aria-hidden="true">⚔</div>
                        <div>
                            <div className="player-home__brand-name">AVALON DnD</div>
                            <div className="player-home__brand-subtitle">Compact tactical access for live sessions</div>
                        </div>
                    </div>
                    <button
                        type="button"
                        className="player-home__icon-button"
                        onClick={() => setHudVisible((value) => !value)}
                        aria-pressed={hudVisible}
                        aria-label="Toggle HUD hints"
                    >
                        ?
                    </button>
                </header>

                {activeTab === 'battle' && (
                    <main className="player-home__stack">
                        <section className="player-card player-card--hero">
                            <div className="player-card__eyebrow">Battle access</div>
                            <div className="player-hero">
                                <div className="player-hero__copy">
                                    <h1 className="player-hero__title">Join a Session</h1>
                                    <p className="player-hero__text">
                                        Connect to a session and keep the battle surface clear. Hidden map controls are not exposed to the player client.
                                    </p>
                                </div>
                                <div className="player-hero__art" aria-hidden="true">
                                    <span className="player-hero__art-door" />
                                    <span className="player-hero__art-tower player-hero__art-tower--left" />
                                    <span className="player-hero__art-tower player-hero__art-tower--right" />
                                    <span className="player-hero__art-spark player-hero__art-spark--a" />
                                    <span className="player-hero__art-spark player-hero__art-spark--b" />
                                    <span className="player-hero__art-spark player-hero__art-spark--c" />
                                </div>
                            </div>
                            {hudVisible && (
                                <p className="player-card__support">
                                    A slim interface keeps your game surface clear. Use the HUD only for joining and quick inspection.
                                </p>
                            )}
                            <div className="player-chip-row">
                                <span className="player-pill">Session {connectionSummary}</span>
                                <span className="player-pill">Players {playerCount}</span>
                            </div>
                        </section>

                        <section className="player-card player-form-card">
                            <div className="player-card__label-row">
                                <span className="player-card__label">Server</span>
                                <span className="player-pill player-pill--success">Ready</span>
                            </div>
                            <ConnectionForm
                                serverUrl={serverUrl}
                                sessionId={sessionId}
                                playerName={playerName}
                                autoConnect={autoConnect}
                                canConnect={canConnect}
                                status={status ?? (isConnected ? 'Connected' : 'Not connected')}
                                primaryLabel="Connect to Session"
                                secondaryLabel="The form stays compact so the battle HUD remains the focus."
                                onServerUrlChange={setServerUrl}
                                onSessionIdChange={setSessionId}
                                onPlayerNameChange={setPlayerName}
                                onAutoConnectChange={setAutoConnect}
                                onConnect={() => void connect()}
                                onSessionAction={() => copySessionId()}
                                showSessionAction
                                sessionActionLabel="⌁"
                            />
                            <div className="player-status-row">
                                <div className="player-status-row__left">
                                    <span className="player-status-dot" aria-hidden="true" />
                                    <span>Players {playerCount}</span>
                                </div>
                                <div className="player-status-row__right">{connectionSummary}</div>
                            </div>
                        </section>

                        <section className="player-card player-commands-card">
                            <div className="player-card__header">
                                <div>
                                    <div className="player-card__eyebrow">Battle guidance</div>
                                    <h2 className="player-card__title">Quick access</h2>
                                </div>
                                <button type="button" className="player-chip-button" onClick={() => setHudVisible((value) => !value)}>
                                    {hudVisible ? 'Hide hints' : 'Show hints'}
                                </button>
                            </div>

                            <div className="player-chip-row">
                                <span className="player-pill">Tap to select</span>
                                <span className="player-pill">Drag to move</span>
                                <span className="player-pill">Pinch to zoom</span>
                            </div>

                            <p className="player-card__support player-card__support--compact player-card__support--muted">
                                Token inspector appears only when you select a token in battle.
                            </p>
                        </section>
                    </main>
                )}

                {activeTab === 'settings' && (
                    <main className="player-home__stack">
                        <section className="player-card player-form-card">
                            <div className="player-card__eyebrow">Session settings</div>
                            <div className="player-settings-grid">
                                <label className="player-toggle-row">
                                    <span>
                                        <span className="player-input__label">Auto connect</span>
                                        <span className="player-toggle-row__meta">Remember this session for next time.</span>
                                    </span>
                                    <input
                                        type="checkbox"
                                        checked={autoConnect}
                                        onChange={(e) => setAutoConnect(e.target.checked)}
                                    />
                                </label>
                            </div>
                            <button type="button" className="player-secondary-button player-secondary-button--wide" onClick={() => setActiveTab('battle')}>
                                Back to battle access
                            </button>
                        </section>
                    </main>
                )}

                <nav className="player-bottom-nav" aria-label="Player navigation">
                    <button type="button" className={`player-bottom-nav__item ${activeTab === 'battle' ? 'is-active' : ''}`} onClick={() => setActiveTab('battle')}>
                        <span className="player-bottom-nav__icon">⌂</span>
                        <span>Battle</span>
                    </button>
                    <button type="button" className={`player-bottom-nav__item ${activeTab === 'settings' ? 'is-active' : ''}`} onClick={() => setActiveTab('settings')}>
                        <span className="player-bottom-nav__icon">⚙</span>
                        <span>Settings</span>
                    </button>
                </nav>
            </div>
        </div>
    );
};

export default JoinSessionScreen;
