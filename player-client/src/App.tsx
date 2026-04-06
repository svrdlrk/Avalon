import BattleMap from './components/BattleMap';
import ConnectionPanel from './components/ConnectionPanel';

function App() {
    return (
        <div style={{ position: 'relative', width: '100vw', height: '100vh', overflow: 'hidden' }}>
            <ConnectionPanel />
            <BattleMap />
        </div>
    );
}

export default App;