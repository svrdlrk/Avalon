import React from 'react';

export interface ConnectionFormProps {
    serverUrl: string;
    sessionId: string;
    playerName: string;
    autoConnect: boolean;
    canConnect: boolean;
    status?: string | null;
    serverPlaceholder?: string;
    sessionPlaceholder?: string;
    namePlaceholder?: string;
    primaryLabel?: string;
    secondaryLabel?: string;
    compact?: boolean;
    showSessionAction?: boolean;
    sessionActionLabel?: string;
    sessionActionAriaLabel?: string;
    onServerUrlChange: (value: string) => void;
    onSessionIdChange: (value: string) => void;
    onPlayerNameChange: (value: string) => void;
    onAutoConnectChange: (value: boolean) => void;
    onConnect: () => void;
    onSessionAction?: () => Promise<boolean>;
}

const ConnectionForm: React.FC<ConnectionFormProps> = ({
    serverUrl,
    sessionId,
    playerName,
    autoConnect,
    canConnect,
    status,
    serverPlaceholder = 'http://localhost:8080',
    sessionPlaceholder = 'Session code',
    namePlaceholder = 'Your name',
    primaryLabel = 'Join session',
    secondaryLabel = 'Remember this session for next time.',
    compact = false,
    showSessionAction = false,
    sessionActionLabel = '⌁',
    sessionActionAriaLabel = 'Copy session ID',
    onServerUrlChange,
    onSessionIdChange,
    onPlayerNameChange,
    onAutoConnectChange,
    onConnect,
    onSessionAction,
}) => {
    return (
        <div className={`connection-form ${compact ? 'connection-form--compact' : ''}`}>
            <div className="connection-form__fields">
                <label className="field">
                    <span className="field__label">Server URL</span>
                    <input
                        className="field__input"
                        type="text"
                        placeholder={serverPlaceholder}
                        value={serverUrl}
                        onChange={(e) => onServerUrlChange(e.target.value)}
                        inputMode="url"
                    />
                </label>

                <label className="field">
                    <span className="field__label">Session ID</span>
                    <div className={`field__input-wrap ${showSessionAction ? 'field__input-wrap--action' : ''}`}>
                        <input
                            className="field__input"
                            type="text"
                            placeholder={sessionPlaceholder}
                            value={sessionId}
                            onChange={(e) => onSessionIdChange(e.target.value)}
                            inputMode="text"
                            onKeyDown={(e) => e.key === 'Enter' && canConnect && onConnect()}
                        />
                        {showSessionAction && onSessionAction && (
                            <button type="button" className="field__action" onClick={() => void onSessionAction()} aria-label={sessionActionAriaLabel}>
                                {sessionActionLabel}
                            </button>
                        )}
                    </div>
                </label>

                <label className="field">
                    <span className="field__label">Name</span>
                    <input
                        className="field__input"
                        type="text"
                        placeholder={namePlaceholder}
                        value={playerName}
                        onChange={(e) => onPlayerNameChange(e.target.value)}
                        inputMode="text"
                        onKeyDown={(e) => e.key === 'Enter' && canConnect && onConnect()}
                    />
                </label>

                <label className="field field--toggle">
                    <span>
                        <span className="field__label">Auto connect</span>
                        <span className="field__meta">Remember this session for next time.</span>
                    </span>
                    <input type="checkbox" checked={autoConnect} onChange={(e) => onAutoConnectChange(e.target.checked)} />
                </label>
            </div>

            <button
                type="button"
                className={`primary-action primary-action--wide ${canConnect ? '' : 'primary-action--disabled'}`}
                disabled={!canConnect}
                onClick={onConnect}
            >
                {primaryLabel}
            </button>

            {!compact && (
                <div className="connection-form__chips">
                    <span className="hud-chip">Remembers server and name</span>
                    <span className="hud-chip">Mobile-friendly layout</span>
                </div>
            )}

            <div className="connection-form__status" role="status" aria-live="polite">
                {status ?? 'Not connected'}
            </div>
            {secondaryLabel && !compact && <p className="connection-form__hint">{secondaryLabel}</p>}
        </div>
    );
};

export default ConnectionForm;
