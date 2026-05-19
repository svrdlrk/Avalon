export type MapCommand = 'zoom-in' | 'zoom-out' | 'reset' | 'fit' | 'center-selected';

export function dispatchMapCommand(type: MapCommand, detail?: unknown): void {
    if (typeof window === 'undefined') return;
    window.dispatchEvent(new CustomEvent(`avalon-map:${type}`, { detail }));
}
