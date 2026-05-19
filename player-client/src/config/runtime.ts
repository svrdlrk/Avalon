export function resolveDefaultServerBaseUrl(): string {
    const envUrl = import.meta.env.VITE_AVALON_SERVER_URL?.trim();
    if (envUrl) {
        return envUrl;
    }

    if (typeof window !== 'undefined' && window.location?.hostname) {
        return `http://${window.location.hostname}:8080`;
    }

    return 'http://localhost:8080';
}

export const DEFAULT_SERVER_BASE_URL = resolveDefaultServerBaseUrl();
export const DEFAULT_LAUNCHER_CONTROL_URL = import.meta.env.VITE_AVALON_LAUNCHER_CONTROL_URL?.trim() || '';

function isLoopbackHost(hostname: string): boolean {
    const normalized = hostname.trim().toLowerCase();
    return normalized === 'localhost' || normalized === '127.0.0.1' || normalized === '[::1]';
}

export function normalizeServerBaseUrl(input: string | null | undefined): string {
    const fallback = resolveDefaultServerBaseUrl();
    const raw = (input ?? '').trim();
    if (!raw) return fallback;

    const candidate = /^https?:\/\//i.test(raw) ? raw : `http://${raw}`;

    try {
        const origin = new URL(candidate).origin;
        if (typeof window !== 'undefined' && window.location?.hostname) {
            const candidateHost = new URL(candidate).hostname;
            if (isLoopbackHost(candidateHost) && !isLoopbackHost(window.location.hostname)) {
                return `http://${window.location.hostname}:8080`;
            }
        }
        return origin;
    } catch {
        return candidate.replace(/\/+$/, '');
    }
}

export function suggestServerBaseUrl(input: string | null | undefined): string {
    return normalizeServerBaseUrl(input);
}
