import { useState } from 'react';
import JoinScreen from './components/JoinScreen';
import BattleMap from './components/BattleMap';

export default function App() {
    const [joined, setJoined] = useState(false);

    return joined
        ? <BattleMap />
        : <JoinScreen onJoined={() => setJoined(true)} />;
}