import { useState } from 'react';
import { wsClient } from '../net/wsClient';

interface Props {
    onJoined: () => void;
}

export default function JoinScreen({ onJoined }: Props) {
    const [serverUrl, setServerUrl] = useState('http://localhost:8080');
    const [sessionId, setSessionId] = useState('');
    const [playerName, setPlayerName] = useState('');
    const [status, setStatus] = useState('');
    const [isDm, setIsDm] = useState(false);

    const handleJoin = () => {
        if (!sessionId || !playerName) {
            setStatus('Заполни все поля');
            return;
        }
        setStatus('Подключение...');
        wsClient.connect(serverUrl, sessionId, playerName, false, () => {
            onJoined();
        });
    };

    return (
        <div style={styles.container}>
            <h2 style={styles.title}>Avalon DnD</h2>

            <label style={styles.label}>Сервер</label>
            <input
                style={styles.input}
                value={serverUrl}
                onChange={e => setServerUrl(e.target.value)}
            />

            <label style={styles.label}>ID сессии</label>
            <input
                style={styles.input}
                value={sessionId}
                onChange={e => setSessionId(e.target.value)}
                placeholder="Введи ID от DM"
            />

            <label style={styles.label}>Твоё имя</label>
            <input
                style={styles.input}
                value={playerName}
                onChange={e => setPlayerName(e.target.value)}
                placeholder="Имя персонажа"
            />

            <label>
                <input
                    type="checkbox"
                    checked={isDm}
                    onChange={(e) => setIsDm(e.target.checked)}
                />
                Join as DM
            </label>

            <button style={styles.button} onClick={handleJoin}>
                Войти в сессию
            </button>

            <p style={styles.hint}>
                Локально: запусти Spring Boot (порт 8080), в папке{' '}
                <code>player-client</code> — <code>npm run dev</code> (обычно :5173).
                Укажи тот же адрес сервера, что видит браузер (часто{' '}
                <code>127.0.0.1</code> вместо <code>localhost</code>, если один из клиентов
                в WSL/Docker).
            </p>

            {status && <p style={styles.status}>{status}</p>}
        </div>
    );
}

const styles: Record<string, React.CSSProperties> = {
    container: {
        display: 'flex',
        flexDirection: 'column',
        padding: '24px',
        maxWidth: '400px',
        margin: '40px auto',
        gap: '8px',
    },
    title: {
        fontSize: '24px',
        marginBottom: '16px',
        textAlign: 'center',
    },
    label: {
        fontSize: '14px',
        color: '#666',
    },
    input: {
        padding: '10px',
        fontSize: '16px',
        borderRadius: '6px',
        border: '1px solid #ccc',
        marginBottom: '8px',
    },
    button: {
        padding: '12px',
        fontSize: '16px',
        backgroundColor: '#4a90d9',
        color: 'white',
        border: 'none',
        borderRadius: '6px',
        cursor: 'pointer',
        marginTop: '8px',
    },
    status: {
        textAlign: 'center',
        color: '#666',
    },
    hint: {
        fontSize: '12px',
        color: '#888',
        lineHeight: 1.45,
        marginTop: '12px',
    },
};