export const DEFAULT_SERVER_BASE_URL = import.meta.env.VITE_AVALON_SERVER_URL?.trim() || 'http://localhost:8080';
export const DEFAULT_LAUNCHER_CONTROL_URL = import.meta.env.VITE_AVALON_LAUNCHER_CONTROL_URL?.trim() || '';

export function normalizeServerBaseUrl(input: string | null | undefined): string {
    const fallback = DEFAULT_SERVER_BASE_URL;
    const raw = (input ?? '').trim();
    if (!raw) return fallback;

    const candidate = /^https?:\/\//i.test(raw) ? raw : `http://${raw}`;

    try {
        return new URL(candidate).origin;
    } catch {
        return candidate.replace(/\/+$/, '');
    }
}
