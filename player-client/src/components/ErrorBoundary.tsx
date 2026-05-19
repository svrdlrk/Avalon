import React from 'react';

type Props = {
    children: React.ReactNode;
};

type State = {
    hasError: boolean;
};

export default class ErrorBoundary extends React.Component<Props, State> {
    constructor(props: Props) {
        super(props);
        this.state = { hasError: false };
    }

    static getDerivedStateFromError(): State {
        return { hasError: true };
    }

    componentDidCatch(error: unknown, info: React.ErrorInfo) {
        console.error('[ui] unhandled render error', error, info);
    }

    render() {
        if (this.state.hasError) {
            return (
                <div style={{
                    minHeight: '100dvh',
                    display: 'grid',
                    placeItems: 'center',
                    padding: '24px',
                    background: 'radial-gradient(circle at top, rgba(124,58,237,0.18), transparent 30%), linear-gradient(180deg, #09101c 0%, #070b14 100%)',
                    color: '#f8fafc',
                    textAlign: 'center',
                }}>
                    <div style={{
                        maxWidth: '520px',
                        padding: '22px',
                        borderRadius: '20px',
                        border: '1px solid rgba(148,163,184,0.18)',
                        background: 'rgba(15,23,42,0.82)',
                        boxShadow: '0 18px 50px rgba(0,0,0,0.34)',
                        backdropFilter: 'blur(16px)',
                    }}>
                        <div style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            width: '52px',
                            height: '52px',
                            marginBottom: '16px',
                            borderRadius: '16px',
                            background: 'rgba(239,68,68,0.14)',
                            border: '1px solid rgba(239,68,68,0.22)',
                            color: '#fecaca',
                            fontSize: '24px',
                            fontWeight: 900,
                        }}>!</div>
                        <h1 style={{ margin: '0 0 10px', fontSize: '22px', letterSpacing: '-0.02em' }}>
                            Интерфейс столкнулся с критической ошибкой
                        </h1>
                        <p style={{ margin: 0, color: '#cbd5e1', lineHeight: 1.55 }}>
                            Обнови страницу или переподключись к сессии. Если ошибка повторяется, нужно проверить
                            состояние подключения или конфликт данных на стороне клиента.
                        </p>
                    </div>
                </div>
            );
        }

        return this.props.children;
    }
}
