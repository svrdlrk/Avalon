import { useEffect, useState } from 'react';

const MAX_CACHE_SIZE = 128;
const imageCache = new Map<string, HTMLImageElement>();

function rememberImage(url: string, img: HTMLImageElement) {
    if (imageCache.has(url)) {
        imageCache.delete(url);
    }
    imageCache.set(url, img);
    if (imageCache.size > MAX_CACHE_SIZE) {
        const oldestKey = imageCache.keys().next().value as string | undefined;
        if (oldestKey) {
            imageCache.delete(oldestKey);
        }
    }
}

/**
 * Хук для асинхронной загрузки картинки в Konva.
 * Аналог use-image, но без внешней зависимости.
 */
function useImage(url: string | null): [HTMLImageElement | undefined, 'loading' | 'loaded' | 'failed'] {
    const [state, setState] = useState<{
        image: HTMLImageElement | undefined;
        status: 'loading' | 'loaded' | 'failed';
    }>({ image: undefined, status: 'loading' });

    useEffect(() => {
        let alive = true;

        if (!url) {
            setState({ image: undefined, status: 'failed' });
            return () => {
                alive = false;
            };
        }

        const cached = imageCache.get(url);
        if (cached) {
            rememberImage(url, cached);
            setState({ image: cached, status: 'loaded' });
            return () => {
                alive = false;
            };
        }

        const img = new window.Image();
        img.decoding = 'async';

        const onLoad = () => {
            if (!alive) return;
            rememberImage(url, img);
            setState({ image: img, status: 'loaded' });
        };
        const onError = () => {
            if (!alive) return;
            setState({ image: undefined, status: 'failed' });
        };

        img.addEventListener('load', onLoad);
        img.addEventListener('error', onError);
        img.src = url;

        setState({ image: undefined, status: 'loading' });

        return () => {
            alive = false;
            img.removeEventListener('load', onLoad);
            img.removeEventListener('error', onError);
        };
    }, [url]);

    return [state.image, state.status];
}

export default useImage;
