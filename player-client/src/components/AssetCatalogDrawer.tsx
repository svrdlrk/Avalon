import React, { useEffect, useMemo, useState } from 'react';
import { normalizeAssetUrl } from '../utils/assetUrl';

type AssetItem = {
    id?: string;
    name?: string;
    category?: string;
    kind?: string;
    imageUrl?: string | null;
    gridSize?: number;
    width?: number;
    height?: number;
    defaultWidth?: number;
    defaultHeight?: number;
    dayVision?: number;
    nightVision?: number;
};

type CatalogResponse = {
    tokens?: AssetItem[];
    objects?: AssetItem[];
};

function isRenderableAsset(asset: AssetItem): boolean {
    const image = (asset.imageUrl ?? '').trim();
    if (!image) return false;
    const lower = image.toLowerCase().replace(/\\/g, '/');
    if (lower.endsWith('/')) return false;
    return lower.endsWith('.png') || lower.endsWith('.jpg') || lower.endsWith('.jpeg') || lower.endsWith('.gif') || lower.endsWith('.webp') || lower.endsWith('.bmp') || lower.endsWith('.svg') || lower.includes('.');
}

const panel: React.CSSProperties = {
    position: 'fixed',
    inset: '0',
    zIndex: 80,
    background: 'rgba(0, 0, 0, 0.72)',
    display: 'flex',
    alignItems: 'flex-end',
    justifyContent: 'center',
    padding: '12px',
    boxSizing: 'border-box',
};

const sheet: React.CSSProperties = {
    width: 'min(1100px, 100%)',
    height: 'min(88vh, 900px)',
    background: '#101014',
    border: '1px solid #2b2b35',
    borderRadius: '18px',
    boxShadow: '0 18px 48px rgba(0,0,0,0.45)',
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
};

const header: React.CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: '12px',
    padding: '14px 16px',
    borderBottom: '1px solid #2b2b35',
    background: '#15151b',
};

const content: React.CSSProperties = {
    flex: 1,
    minHeight: 0,
    display: 'flex',
    flexDirection: 'column',
    gap: '12px',
    padding: '14px 16px 16px',
};

const tabsRow: React.CSSProperties = {
    display: 'flex',
    gap: '8px',
    flexWrap: 'wrap',
};

const tabBtn: React.CSSProperties = {
    border: '1px solid #2f2f3a',
    background: '#1b1b22',
    color: '#d4d4d8',
    borderRadius: '999px',
    padding: '8px 12px',
    fontSize: '13px',
    cursor: 'pointer',
};

const tabBtnActive: React.CSSProperties = {
    background: '#2563eb',
    borderColor: '#2563eb',
    color: '#fff',
};

const searchRow: React.CSSProperties = {
    display: 'flex',
    gap: '10px',
    flexWrap: 'wrap',
    alignItems: 'center',
};

const inputStyle: React.CSSProperties = {
    flex: '1 1 220px',
    minWidth: 0,
    padding: '10px 12px',
    borderRadius: '10px',
    border: '1px solid #2f2f3a',
    background: '#17171e',
    color: '#f4f4f5',
    fontSize: '14px',
    boxSizing: 'border-box',
};

const grid: React.CSSProperties = {
    flex: 1,
    minHeight: 0,
    overflow: 'auto',
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
    gap: '12px',
    paddingRight: '2px',
};

const card: React.CSSProperties = {
    border: '1px solid #2b2b35',
    borderRadius: '16px',
    background: '#15151b',
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
    minHeight: '240px',
};

const cardMedia: React.CSSProperties = {
    height: '140px',
    background: '#0d0d11',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    overflow: 'hidden',
};

const cardImg: React.CSSProperties = {
    width: '100%',
    height: '100%',
    objectFit: 'cover',
};

const cardBody: React.CSSProperties = {
    display: 'grid',
    gap: '6px',
    padding: '12px',
};

function sizeLabel(asset: AssetItem): string {
    const gridSize = asset.gridSize ?? 0;
    if (gridSize > 0) return `${gridSize}×${gridSize}`;
    const w = asset.width ?? asset.defaultWidth ?? 1;
    const h = asset.height ?? asset.defaultHeight ?? 1;
    return `${w}×${h}`;
}

function assetKindLabel(asset: AssetItem): string {
    if (asset.kind) return asset.kind;
    if (asset.category?.toLowerCase().includes('token')) return 'TOKEN';
    return 'OBJECT';
}

function AssetCard({ asset, serverBaseUrl }: { asset: AssetItem; serverBaseUrl: string }) {
    const src = normalizeAssetUrl(asset.imageUrl, serverBaseUrl);
    return (
        <div style={card}>
            <div style={cardMedia}>
                {src ? (
                    <img src={src} alt={asset.name ?? asset.id ?? 'asset'} style={cardImg} />
                ) : (
                    <div style={{ color: '#71717a', fontSize: '13px' }}>No preview</div>
                )}
            </div>
            <div style={cardBody}>
                <div style={{ color: '#f4f4f5', fontSize: '14px', fontWeight: 600, lineHeight: 1.2 }}>
                    {asset.name ?? asset.id ?? 'Unnamed asset'}
                </div>
                <div style={{ color: '#a1a1aa', fontSize: '12px' }}>
                    {assetKindLabel(asset)} • {asset.category ?? 'Uncategorized'}
                </div>
                <div style={{ color: '#a1a1aa', fontSize: '12px', fontFamily: 'monospace' }}>
                    {asset.id ?? '—'}
                </div>
                <div style={{ color: '#d4d4d8', fontSize: '12px' }}>
                    Size: {sizeLabel(asset)}
                </div>
            </div>
        </div>
    );
}

export default function AssetCatalogDrawer({
    open,
    onClose,
    serverBaseUrl,
}: {
    open: boolean;
    onClose: () => void;
    serverBaseUrl: string;
}) {
    const [catalog, setCatalog] = useState<CatalogResponse>({ tokens: [], objects: [] });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [tab, setTab] = useState<'tokens' | 'objects'>('tokens');
    const [query, setQuery] = useState('');

    useEffect(() => {
        if (!open) return;
        const controller = new AbortController();
        setLoading(true);
        setError(null);

        const base = serverBaseUrl.replace(/\/+$/, '');
        fetch(`${base}/api/assets/catalog`, { signal: controller.signal })
            .then(async (res) => {
                if (!res.ok) throw new Error(`HTTP ${res.status}`);
                return res.json();
            })
            .then((data: CatalogResponse) => {
                setCatalog({
                    tokens: Array.isArray(data?.tokens) ? data.tokens : [],
                    objects: Array.isArray(data?.objects) ? data.objects : [],
                });
            })
            .catch((err: unknown) => {
                if ((err as { name?: string })?.name === 'AbortError') return;
                setError(err instanceof Error ? err.message : 'Failed to load asset catalog');
            })
            .finally(() => setLoading(false));

        return () => controller.abort();
    }, [open, serverBaseUrl]);

    const items = useMemo(() => {
        const list = tab === 'tokens' ? (catalog.tokens ?? []) : (catalog.objects ?? []);
        const q = query.trim().toLowerCase();
        if (!q) return list;
        return list.filter((asset) => {
            const haystack = [asset.id, asset.name, asset.category, asset.kind, asset.imageUrl, sizeLabel(asset)]
                .filter(Boolean)
                .join(' ')
                .toLowerCase();
            return haystack.includes(q) && isRenderableAsset(asset);
        });
    }, [catalog, query, tab]);

    if (!open) return null;

    return (
        <div style={panel} onClick={onClose} role="presentation">
            <div style={sheet} onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true" aria-label="Asset catalog">
                <div style={header}>
                    <div>
                        <div style={{ color: '#f4f4f5', fontSize: '16px', fontWeight: 700 }}>Asset catalog</div>
                        <div style={{ color: '#a1a1aa', fontSize: '12px' }}>
                            Normalized asset catalog from the server
                        </div>
                    </div>
                    <button
                        onClick={onClose}
                        style={{
                            border: '1px solid #2f2f3a',
                            background: '#1b1b22',
                            color: '#f4f4f5',
                            borderRadius: '10px',
                            padding: '8px 12px',
                            cursor: 'pointer',
                        }}
                    >
                        Close
                    </button>
                </div>

                <div style={content}>
                    <div style={tabsRow}>
                        <button
                            style={{ ...tabBtn, ...(tab === 'tokens' ? tabBtnActive : {}) }}
                            onClick={() => setTab('tokens')}
                        >
                            Tokens ({catalog.tokens?.length ?? 0})
                        </button>
                        <button
                            style={{ ...tabBtn, ...(tab === 'objects' ? tabBtnActive : {}) }}
                            onClick={() => setTab('objects')}
                        >
                            Objects ({catalog.objects?.length ?? 0})
                        </button>
                    </div>

                    <div style={searchRow}>
                        <input
                            style={inputStyle}
                            type="text"
                            placeholder="Search by name, category, id..."
                            value={query}
                            onChange={(e) => setQuery(e.target.value)}
                        />
                        <div style={{ color: '#a1a1aa', fontSize: '13px' }}>
                            {loading ? 'Loading...' : `${items.length} item(s)`}
                        </div>
                    </div>

                    {error && (
                        <div style={{ color: '#fca5a5', fontSize: '13px' }}>
                            {error}
                        </div>
                    )}

                    <div style={grid}>
                        {!loading && !error && items.map((asset, idx) => (
                            <AssetCard key={`${asset.id ?? asset.name ?? idx}`} asset={asset} serverBaseUrl={serverBaseUrl} />
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}
