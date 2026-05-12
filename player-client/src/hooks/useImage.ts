import { useEffect, useState } from 'react';

const imageCache = new Map<string, HTMLImageElement>();

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
            setState({ image: cached, status: 'loaded' });
            return () => {
                alive = false;
            };
        }

        const img = new window.Image();
        img.crossOrigin = 'anonymous';

        const onLoad = () => {
            if (!alive) return;
            imageCache.set(url, img);
            setState({ image: img, status: 'loaded' });
        };
        const onError = () => {
            if (!alive) return;
            setState({ image: undefined, status: 'failed' });
        };

        img.addEventListener('load', onLoad);
        img.addEventListener('error', onError);
        img.src = url;

        setState((prev) => (prev.status === 'loading' && prev.image == null ? prev : { image: undefined, status: 'loading' }));

        return () => {
            alive = false;
            img.removeEventListener('load', onLoad);
            img.removeEventListener('error', onError);
        };
    }, [url]);

    return [state.image, state.status];
}

export default useImage;
