import React, { useEffect, useRef, useState } from 'react';
import { useGameStore } from '../store/gameStore';
import { normalizeAssetUrl } from '../utils/assetUrl';
import { wsClient } from '../net/wsClient';
import { dispatchMapCommand } from '../utils/mapCommands';

const InitiativeBar: React.FC = () => {
    const initiative = useGameStore((state) => state.initiative);
    const tokens = useGameStore((state) => state.tokens);
    const players = useGameStore((state) => state.players);
    const myPlayerId = useGameStore((state) => state.myPlayerId);
    const setSelectedTokenId = useGameStore((state) => state.setSelectedTokenId);
    const scrollRef = useRef<HTMLDivElement>(null);
    const activeRef = useRef<HTMLButtonElement>(null);
    const [isCompactViewport, setIsCompactViewport] = useState(() => {
        if (typeof window === 'undefined') return false;
        return window.matchMedia('(max-width: 760px)').matches;
    });
    const [isCollapsed, setIsCollapsed] = useState(() => {
        if (typeof window === 'undefined') return false;
        return window.matchMedia('(max-width: 760px)').matches;
    });

    useEffect(() => {
        if (typeof window === 'undefined') return;

        const compactQuery = window.matchMedia('(max-width: 760px)');
        const update = () => {
            setIsCompactViewport(compactQuery.matches);
            if (compactQuery.matches) {
                setIsCollapsed(true);
            }
        };

        update();
        compactQuery.addEventListener('change', update);
        return () => compactQuery.removeEventListener('change', update);
    }, []);

    useEffect(() => {
        if (!activeRef.current || !initiative?.entries?.length) return;
        if (isCollapsed && isCompactViewport) return;
        activeRef.current.scrollIntoView({
            behavior: 'smooth',
            block: 'nearest',
            inline: 'center',
        });
    }, [initiative?.currentIndex, isCollapsed, isCompactViewport]);

    if (!initiative || initiative.entries.length === 0) return null;

    const currentEntry = initiative.entries[initiative.currentIndex];
    const currentToken = currentEntry ? tokens[currentEntry.tokenId] : null;

    return (
        <section className={`initiative-rail ${isCollapsed ? 'initiative-rail--collapsed' : ''}`} aria-label="Initiative order">
            <div className="initiative-rail__header">
                <div>
                    <div className="initiative-rail__eyebrow">Initiative</div>
                    <div className="initiative-rail__title">
                        {currentEntry ? `Now acting: ${currentEntry.name}` : 'Initiative active'}
                    </div>
                </div>
                <div className="initiative-rail__header-actions">
                    <div className="initiative-rail__summary">
                        {currentToken?.ownerId === myPlayerId ? 'Your turn soon' : 'Combat pacing ready'}
                    </div>
                    {currentEntry && (
                        <button
                            className="hud-button hud-button--ghost initiative-rail__focus"
                            type="button"
                            onClick={() => {
                                setSelectedTokenId(currentEntry.tokenId);
                                dispatchMapCommand('center-selected', { tokenId: currentEntry.tokenId });
                            }}
                        >
                            Focus active
                        </button>
                    )}
                    <button
                        className="hud-button hud-button--ghost initiative-rail__toggle"
                        type="button"
                        onClick={() => setIsCollapsed((value) => !value)}
                    >
                        {isCollapsed ? 'Show turns' : 'Hide turns'}
                    </button>
                </div>
            </div>

            {!isCollapsed && (
                <div ref={scrollRef} className="initiative-rail__scroll initiative-scroll">
                    {initiative.entries.map((entry, index) => {
                        const token = tokens[entry.tokenId];
                        const isMine = token?.ownerId === myPlayerId;
                        const isActive = index === initiative.currentIndex;
                        const imgUrl = normalizeAssetUrl(token?.imageUrl ?? null, wsClient.getServerBaseUrl());
                        const canSeeHp = Boolean(token?.ownerId);
                        const hpRatio = token && canSeeHp && token.maxHp > 0 ? Math.max(0, Math.min(100, (token.hp / token.maxHp) * 100)) : 100;
                        const ownerName = token?.ownerId ? players[token.ownerId]?.name : null;

                        return (
                            <button
                                key={entry.tokenId}
                                ref={isActive ? activeRef : null}
                                className={[
                                    'initiative-card',
                                    isActive ? 'initiative-card--active' : '',
                                    isMine && !isActive ? 'initiative-card--mine' : '',
                                ].filter(Boolean).join(' ')}
                                type="button"
                                title={entry.name}
                                onClick={() => setSelectedTokenId(entry.tokenId)}
                            >
                                <div className="initiative-card__avatar" aria-hidden="true">
                                    {imgUrl ? (
                                        <img
                                            src={imgUrl}
                                            alt={entry.name}
                                            className="initiative-card__image"
                                            onError={(event) => {
                                                (event.target as HTMLImageElement).style.display = 'none';
                                            }}
                                        />
                                    ) : (
                                        <span className="initiative-card__fallback">{entry.name.charAt(0).toUpperCase()}</span>
                                    )}
                                </div>

                                <div className="initiative-card__content">
                                    <div className="initiative-card__name">{entry.name}</div>
                                    <div className="initiative-card__meta">
                                        {entry.initiative}
                                        {ownerName ? ` · ${ownerName}` : ''}
                                    </div>
                                    <div className="initiative-card__track">
                                        <span
                                            className={`initiative-card__fill ${canSeeHp ? '' : 'initiative-card__fill--hidden'}`.trim()}
                                            style={{ width: `${hpRatio}%` }}
                                            aria-hidden="true"
                                        />
                                    </div>
                                </div>

                                {isActive && <span className="initiative-card__glow" aria-hidden="true" />}
                            </button>
                        );
                    })}
                </div>
            )}
        </section>
    );
};

export default InitiativeBar;
