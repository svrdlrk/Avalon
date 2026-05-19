function currentBrowserOrigin(): string | null {
    if (typeof window === 'undefined' || !window.location?.hostname) return null;
    const host = window.location.hostname.trim();
    if (!host) return null;
    const port = window.location.port ? `:${window.location.port}` : '';
    return `${window.location.protocol}//${host}${port}`;
}

function rewriteLoopbackBase(serverBaseUrl: string): string {
    const trimmed = serverBaseUrl.trim().replace(/\/+$/, '');
    const browserOrigin = currentBrowserOrigin();
    if (!browserOrigin) return trimmed;

    try {
        const server = new URL(trimmed);
        const browser = new URL(browserOrigin);
        const loopbackHosts = new Set(['localhost', '127.0.0.1', '[::1]']);
        if (loopbackHosts.has(server.hostname.toLowerCase()) && !loopbackHosts.has(browser.hostname.toLowerCase())) {
            const port = server.port || '8080';
            return `${browser.protocol}//${browser.hostname}:${port}`;
        }
    } catch {
        // ignore and keep original value
    }

    return trimmed;
}

export function extractAssetPath(raw: string): string | null {
    if (!raw) return null;

    const normalized = raw.trim().replace(/\\/g, '/');
    if (!normalized) return null;

    const lowered = normalized.toLowerCase();
    const markers = ['/uploads/', 'uploads/', '/assets/', 'assets/'];

    for (const marker of markers) {
        const idx = lowered.indexOf(marker);
        if (idx >= 0) {
            const slice = normalized.substring(idx);
            return slice.startsWith('/') ? slice : `/${slice}`;
        }
    }

    const bangIdx = normalized.indexOf('!/');
    if (bangIdx >= 0) {
        const tail = normalized.substring(bangIdx + 2);
        const tailLower = tail.toLowerCase();
        for (const marker of markers) {
            const idx = tailLower.indexOf(marker);
            if (idx >= 0) {
                const slice = tail.substring(idx);
                return slice.startsWith('/') ? slice : `/${slice}`;
            }
        }
    }

    return null;
}

export function normalizeAssetUrl(imageUrl: string | null | undefined, serverBaseUrl: string): string | null {
    if (!imageUrl) return null;

    const trimmed = imageUrl.trim();
    if (!trimmed) return null;

    const baseUrl = rewriteLoopbackBase(serverBaseUrl);

    // Keep HTTP/data URLs intact, but try to convert local file URIs and
    // absolute filesystem paths into web paths served from /uploads/**.
    const hasUriScheme = /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(trimmed) && !/^[a-zA-Z]:[\/]/.test(trimmed);
    if (hasUriScheme) {
        const relative = extractAssetPath(trimmed);
        if (relative) {
            return encodeURI(`${baseUrl}${relative.startsWith('/') ? relative : `/${relative}`}`);
        }
        return encodeURI(trimmed);
    }

    const relative = extractAssetPath(trimmed);
    if (relative) {
        return encodeURI(`${baseUrl}${relative.startsWith('/') ? relative : `/${relative}`}`);
    }

    const cleaned = trimmed.replace(/\\/g, '/');
    const lower = cleaned.toLowerCase();

    if (lower.startsWith('/maps/') || lower.startsWith('maps/')) {
        const noSlash = cleaned.replace(/^\/+/, '');
        return encodeURI(`${baseUrl}/uploads/${noSlash}`);
    }

    if (cleaned.startsWith('/')) {
        return encodeURI(`${baseUrl}${cleaned}`);
    }

    const noSlash = cleaned.replace(/^\/+/, '');
    return encodeURI(`${baseUrl}/uploads/assets/${noSlash}`);
}
