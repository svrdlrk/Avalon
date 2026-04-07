import { useState, useEffect } from 'react';

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
        if (!url) {
            setState({ image: undefined, status: 'failed' });
            return;
        }

        const img = new window.Image();
        img.crossOrigin = 'anonymous';

        const onLoad = () => setState({ image: img, status: 'loaded' });
        const onError = () => setState({ image: undefined, status: 'failed' });

        img.addEventListener('load', onLoad);
        img.addEventListener('error', onError);
        img.src = url;

        setState({ image: undefined, status: 'loading' });

        return () => {
            img.removeEventListener('load', onLoad);
            img.removeEventListener('error', onError);
        };
    }, [url]);

    return [state.image, state.status];
}

export default useImage;