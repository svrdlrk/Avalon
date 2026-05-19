import React, { useMemo } from 'react';
import { useGameStore } from '../store/gameStore';
import { dispatchMapCommand } from '../utils/mapCommands';

type Props = {
    chromeCollapsed: boolean;
    collapsed: boolean;
    compactViewport: boolean;
    onToggleChrome: () => void;
    onToggleCollapsed: () => void;
};

const BattleCommandDock: React.FC<Props> = ({ chromeCollapsed, collapsed, compactViewport, onToggleChrome, onToggleCollapsed }) => {
    const selectedTokenId = useGameStore((state) => state.selectedTokenId);
    const tokens = useGameStore((state) => state.tokens);
    const initiative = useGameStore((state) => state.initiative);
    const myPlayerId = useGameStore((state) => state.myPlayerId);
    const players = useGameStore((state) => state.players);

    const selectedToken = selectedTokenId ? tokens[selectedTokenId] : null;
    const currentTurn = initiative?.entries?.[initiative.currentIndex] ?? null;
    const currentTurnToken = currentTurn ? tokens[currentTurn.tokenId] : null;
    const currentTurnOwner = currentTurnToken?.ownerId ? players[currentTurnToken.ownerId] : null;

    const selectedSummary = useMemo(() => {
        if (!selectedToken) return 'No token selected';
        const owner = selectedToken.ownerId ? players[selectedToken.ownerId]?.name ?? 'Unknown' : 'NPC';
        return `${selectedToken.name} · ${owner}`;
    }, [players, selectedToken]);

    const canCenter = Boolean(selectedTokenId);

    return (
        <aside className={`battle-command-dock ${collapsed ? 'battle-command-dock--collapsed' : ''} ${compactViewport ? 'battle-command-dock--compact' : ''}`} aria-label="Map commands">
            <div className="battle-command-dock__header">
                <div>
                    <div className="battle-command-dock__eyebrow">Map commands</div>
                    <div className="battle-command-dock__title">{collapsed ? 'Quick tools' : 'Quick control deck'}</div>
                </div>
                <div className="battle-command-dock__header-actions">
                    <button className="hud-button hud-button--ghost" onClick={onToggleCollapsed} type="button">
                        {collapsed ? 'Expand' : 'Compact'}
                    </button>
                    <button className="hud-button hud-button--ghost" onClick={onToggleChrome} type="button">
                        {chromeCollapsed ? 'Show HUD' : 'Hide HUD'}
                    </button>
                </div>
            </div>

            <div className="battle-command-dock__summary">
                <span className="hud-chip hud-chip--strong">{selectedSummary}</span>
                {currentTurn && (
                    <span className="hud-chip hud-chip--turn">
                        Turn · {currentTurn.name}{currentTurnOwner ? ` · ${currentTurnOwner.name}` : ''}
                    </span>
                )}
                {myPlayerId && <span className="hud-chip">You · {players[myPlayerId]?.name ?? 'Player'}</span>}
            </div>

            <div className={`battle-command-dock__buttons ${collapsed ? 'battle-command-dock__buttons--compact' : ''}`}>
                <button className="hud-button" type="button" onClick={() => dispatchMapCommand('zoom-out')}>Zoom −</button>
                <button className="hud-button" type="button" onClick={() => dispatchMapCommand('zoom-in')}>Zoom +</button>
                <button className="hud-button" type="button" onClick={() => dispatchMapCommand('fit')}>Fit</button>
                {!collapsed && <button className="hud-button" type="button" onClick={() => dispatchMapCommand('reset')}>Reset</button>}
                <button
                    className={`hud-button ${canCenter ? 'hud-button--accent' : 'hud-button--ghost'}`}
                    type="button"
                    onClick={() => dispatchMapCommand('center-selected', selectedToken ? { tokenId: selectedToken.id } : undefined)}
                    disabled={!canCenter}
                >
                    Center token
                </button>
            </div>

            {!collapsed && !compactViewport && (
                <div className="battle-command-dock__hint">
                    Hotkeys: <kbd>+</kbd> <kbd>−</kbd> <kbd>F</kbd> <kbd>0</kbd>
                </div>
            )}
        </aside>
    );
};

export default BattleCommandDock;
