import BattleMap from './components/BattleMap';
import ConnectionPanel from './components/ConnectionPanel';

function App() {
    return (
        <div className="min-h-screen bg-zinc-950 relative">
            <ConnectionPanel />
            <BattleMap />
        </div>
    );
}

export default App;