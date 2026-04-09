import BattleMap from './components/BattleMap';
import ConnectionPanel from './components/ConnectionPanel';
import InitiativeBar from './components/InitiativeBar';
import { useGameStore } from './store/gameStore';

function App() {
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