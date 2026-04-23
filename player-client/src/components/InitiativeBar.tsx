import React, { useEffect, useRef } from 'react';
import { useGameStore } from '../store/gameStore';
import { wsClient } from '../net/wsClient';
import { normalizeAssetUrl } from '../utils/assetUrl';

/**
 * InitiativeBar — горизонтальная панель инициативы вверху экрана.
 * - Авто-скролл до активного токена при смене хода.
 * - Число инициативы не показывается (только имя + аватар).
 */
const InitiativeBar: React.FC = () => {
    const { initiative, tokens, myPlayerId } = useGameStore();
    const scrollRef   = useRef<HTMLDivElement>(null);
    const activeRef   = useRef<HTMLDivElement>(null);

    // Auto-scroll to active entry whenever currentIndex changes
    useEffect(() => {
        if (!activeRef.current || !scrollRef.current) return;
        activeRef.current.scrollIntoView({
            behavior: 'smooth',
            block: 'nearest',
            inline: 'center',
        });
    }, [initiative?.currentIndex]);

    if (!initiative || initiative.entries.length === 0) return null;

    return (
        <div style={styles.bar}>
            <span style={styles.label}>⚔ Ход</span>

            <div ref={scrollRef} style={styles.scroll}>
                {initiative.entries.map((entry, idx) => {
                    const token    = tokens[entry.tokenId];
                    const isMine   = token?.ownerId === myPlayerId;
                    const isActive = idx === initiative.currentIndex;
                    const imgUrl   = normalizeAssetUrl(token?.imageUrl ?? null, wsClient.getServerBaseUrl());

                    return (
                        <div
                            key={entry.tokenId + '-' + idx}
                            ref={isActive ? activeRef : null}
                            style={{
                                ...styles.entry,
                                ...(isActive ? styles.entryActive : {}),
                                ...(isMine && !isActive ? styles.entryMine : {}),
                            }}
                            title={entry.name}
                        >
                            {/* Avatar */}
                            <div style={{
                                ...styles.avatar,
                                border: isActive
                                    ? '2px solid #f1c40f'
                                    : isMine
                                        ? '2px solid #4a90d9'
                                        : '2px solid #555',
                            }}>
                                {imgUrl ? (
                                    <img
                                        src={imgUrl}
                                        alt={entry.name}
                                        style={styles.avatarImg}
                                        onError={(e) => {
                                            (e.target as HTMLImageElement).style.display = 'none';
                                        }}
                                    />
                                ) : (
                                    <div style={{
                                        ...styles.avatarFallback,
                                        background: isActive ? '#c9a227'
                                            : isMine ? '#2980b9' : '#5a3e28',
                                    }}>
                                        {entry.name.charAt(0).toUpperCase()}
                                    </div>
                                )}

                                {/* Animated ring on active token */}
                                {isActive && <div style={styles.activePulse} />}
                            </div>

                            {/* Name only — no initiative number */}
                            <div style={{
                                ...styles.entryName,
                                color: isActive ? '#f1c40f' : isMine ? '#93c5fd' : '#e5e7eb',
                                fontWeight: isActive ? 700 : 400,
                            }}>
                                {entry.name.length > 7
                                    ? entry.name.slice(0, 6) + '…'
                                    : entry.name}
                            </div>

                            {/* HP bar for own tokens */}
                            {token && token.maxHp > 0 && isMine && (
                                <div style={styles.hpBarWrap}>
                                    <div style={{
                                        ...styles.hpBarFill,
                                        width: `${Math.max(0, Math.min(100, (token.hp / token.maxHp) * 100))}%`,
                                        background: token.hp / token.maxHp > 0.5 ? '#2ecc71'
                                            : token.hp / token.maxHp > 0.25 ? '#f39c12' : '#e74c3c',
                                    }} />
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>

            {/* Current turn summary */}
            {initiative.entries[initiative.currentIndex] && (
                <div style={styles.currentTurn}>
                    Ход: <strong>{initiative.entries[initiative.currentIndex].name}</strong>
                </div>
            )}
        </div>
    );
};

// ================================================================ styles

const styles: Record<string, React.CSSProperties> = {
    bar: {
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        zIndex: 100,
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        background: 'linear-gradient(to bottom, rgba(8,8,18,0.98), rgba(10,10,20,0.88))',
        borderBottom: '1px solid rgba(255,255,255,0.08)',
        padding: '6px 12px',
        backdropFilter: 'blur(10px)',
        boxShadow: '0 2px 20px rgba(0,0,0,0.7)',
        height: '76px',
        boxSizing: 'border-box',
    },
    label: {
        color: '#c9a227',
        fontSize: '11px',
        fontWeight: 700,
        whiteSpace: 'nowrap',
        letterSpacing: '0.06em',
        minWidth: '36px',
        textTransform: 'uppercase',
    },
    scroll: {
        display: 'flex',
        alignItems: 'center',
        gap: '4px',
        overflowX: 'auto',
        flex: 1,
        paddingBottom: '2px',
        // Hide scrollbar on webkit
        WebkitOverflowScrolling: 'touch',
        scrollbarWidth: 'none',
        msOverflowStyle: 'none',
    } as React.CSSProperties,
    entry: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '2px',
        minWidth: '50px',
        maxWidth: '50px',
        padding: '2px 3px',
        borderRadius: '8px',
        background: 'rgba(255,255,255,0.03)',
        transition: 'transform 0.2s ease, background 0.2s ease, box-shadow 0.2s ease',
        cursor: 'default',
        flexShrink: 0,
    },
    entryActive: {
        background: 'rgba(241,196,15,0.12)',
        transform: 'scale(1.1)',
        boxShadow: '0 0 12px rgba(241,196,15,0.35)',
    },
    entryMine: {
        background: 'rgba(74,144,217,0.1)',
    },
    avatar: {
        width: '36px',
        height: '36px',
        borderRadius: '50%',
        overflow: 'visible',
        position: 'relative',
        flexShrink: 0,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
    },
    avatarImg: {
        width: '36px',
        height: '36px',
        objectFit: 'cover',
        borderRadius: '50%',
    },
    avatarFallback: {
        width: '36px',
        height: '36px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        color: '#fff',
        fontSize: '15px',
        fontWeight: 700,
        borderRadius: '50%',
    },
    activePulse: {
        position: 'absolute',
        inset: '-4px',
        borderRadius: '50%',
        border: '2px solid #f1c40f',
        animation: 'initiativePulse 1.4s ease-in-out infinite',
        pointerEvents: 'none',
    },
    entryName: {
        fontSize: '9px',
        textAlign: 'center',
        lineHeight: 1.2,
        maxWidth: '48px',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
    },
    hpBarWrap: {
        width: '42px',
        height: '3px',
        background: 'rgba(0,0,0,0.5)',
        borderRadius: '2px',
        overflow: 'hidden',
    },
    hpBarFill: {
        height: '100%',
        borderRadius: '2px',
        transition: 'width 0.4s ease',
    },
    currentTurn: {
        color: '#d1d5db',
        fontSize: '11px',
        whiteSpace: 'nowrap',
        minWidth: '110px',
        textAlign: 'right',
        paddingRight: '4px',
    },
};

export default InitiativeBar;