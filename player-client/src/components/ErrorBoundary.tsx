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
                    minHeight: '100vh',
                    display: 'grid',
                    placeItems: 'center',
                    background: '#0b0b10',
                    color: '#f4f4f5',
                    padding: '24px',
                    textAlign: 'center',
                }}>
                    <div>
                        <h1 style={{ marginBottom: '12px' }}>Что-то пошло не так</h1>
                        <p style={{ margin: 0, color: '#a1a1aa' }}>
                            Интерфейс столкнулся с критической ошибкой. Обновите страницу или переподключитесь к сессии.
                        </p>
                    </div>
                </div>
            );
        }

        return this.props.children;
    }
}
