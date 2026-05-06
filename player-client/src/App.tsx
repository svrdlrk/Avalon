import { useEffect } from 'react';
import BattleMap from './components/BattleMap';
import ConnectionPanel from './components/ConnectionPanel';
import InitiativeBar from './components/InitiativeBar';
import { useGameStore } from './store/gameStore';


const launcherControlUrl = import.meta.env.VITE_AVALON_LAUNCHER_CONTROL_URL as string | undefined;

function notifyLauncher(endpoint: 'client-closed' | 'client-heartbeat') {
    if (!launcherControlUrl) {
        return;
    }

    const url = `${launcherControlUrl.replace(/\/+$/, '')}/launcher/${endpoint}?client=player`;
    const body = new Blob([], { type: 'text/plain' });

    if (endpoint === 'client-closed' && navigator.sendBeacon) {
        navigator.sendBeacon(url, body);
        return;
    }

    fetch(url, {
        method: 'POST',
        keepalive: true,
        mode: 'cors',
        credentials: 'omit',
    }).catch(() => undefined);
}

function App() {
    useEffect(() => {
        const onBeforeUnload = () => notifyLauncher('client-closed');
        const onPageHide = () => notifyLauncher('client-closed');

        window.addEventListener('beforeunload', onBeforeUnload);
        window.addEventListener('pagehide', onPageHide);

        notifyLauncher('client-heartbeat');
        const heartbeat = window.setInterval(() => notifyLauncher('client-heartbeat'), 5000);

        return () => {
            window.removeEventListener('beforeunload', onBeforeUnload);
            window.removeEventListener('pagehide', onPageHide);
            window.clearInterval(heartbeat);
        };
    }, []);

    const initiative = useGameStore((s) => s.initiative);
    const hasInitiative = initiative && initiative.entries.length > 0;

    return (
        <div style={{ position: 'relative', width: '100vw', height: '100vh', overflow: 'hidden' }}>
            {/* Initiative bar — fixed at top, only shown when active */}
            <InitiativeBar />

            {/* Battle map — push down if initiative bar is visible */}
            <div style={{
                position: 'absolute',
                inset: 0,
                top: hasInitiative ? '80px' : 0,
                transition: 'top 0.2s',
            }}>
                <BattleMap />
            </div>

            {/* Connection panel — always on top */}
            <ConnectionPanel />
        </div>
    );
}

export default App;