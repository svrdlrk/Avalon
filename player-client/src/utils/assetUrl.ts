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

    // Keep any absolute URI scheme intact (http, https, file, data, jar, etc.),
    // but do not misclassify Windows paths like C:\assets\image.png.
    const hasUriScheme = /^[a-zA-Z][a-zA-Z0-9+.-]*:/.test(trimmed) && !/^[a-zA-Z]:[\/]/.test(trimmed);
    if (hasUriScheme) {
        return encodeURI(trimmed);
    }

    const relative = extractAssetPath(trimmed);
    if (relative) {
        const baseUrl = serverBaseUrl.replace(/\/$/, '');
        return encodeURI(`${baseUrl}${relative.startsWith('/') ? relative : `/${relative}`}`);
    }

    if (trimmed.startsWith('/')) {
        const baseUrl = serverBaseUrl.replace(/\/$/, '');
        return encodeURI(`${baseUrl}${trimmed}`);
    }

    return encodeURI(trimmed);
}
