import { Client, StompHeaders } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useGameStore } from '../store/gameStore';
import type {
    MapLayoutUpdateDto,
    MapObjectDto,
    PlayerDto,
    SessionStateDto,
    TokenDto,
    WsMessage,
} from '../types/types';

class WsClient {
    private client: Client | null = null;
    private sessionId: string | null = null;
    private playerId: string | null = null;

    private applySessionState(msg: WsMessage<SessionStateDto>, sid: string) {
        const state = msg.payload;
        this.playerId = state.myPlayerId;
        // Сохраняем playerId для rejoin при обновлении страницы
        sessionStorage.setItem(`avalon_player_${sid}`, state.myPlayerId);
        useGameStore.getState().applyState(state, sid);
    }

    private subscribePrivateChannel(sid: string) {
        if (!this.client?.connected || !this.playerId) return;

        this.client.subscribe(
            `/topic/session/${sid}/private/${this.playerId}`,
            (frame) => {
                const msg: WsMessage<SessionStateDto> = JSON.parse(frame.body);
                if (msg.type === 'SESSION_STATE') {
                    this.applySessionState(msg, sid);
                }
            }
        );
    }

    connect(
        serverUrl: string,
        sessionId: string,
        playerName: string,
        isDm: boolean,
        onConnected: () => void
    ) {
        this.disconnect();
        this.sessionId = sessionId;
        this.playerId = null;
        const joinNonce = crypto.randomUUID();

        this.client = new Client({
            webSocketFactory: () => new SockJS(`${serverUrl}/ws`),
            // Автоматический реконнект при обрыве (раз в 5 сек)
            reconnectDelay: 5000,
            onConnect: () => {
                // Подписка на общий канал сессии
                this.client!.subscribe(
                    `/topic/session/${sessionId}`,
                    (frame) => {
                        const msg: WsMessage<unknown> = JSON.parse(frame.body);
                        this.handleEvent(msg);
                    }
                );

                // Подписка на одноразовый канал join — получим SESSION_STATE
                this.client!.subscribe(
                    `/topic/session/${sessionId}/join/${joinNonce}`,
                    (frame) => {
                        const msg: WsMessage<SessionStateDto> =
                            JSON.parse(frame.body);
                        if (msg.type === 'SESSION_STATE') {
                            this.applySessionState(msg, sessionId);
                            this.subscribePrivateChannel(sessionId);
                            onConnected();
                        }
                    }
                );

                this.client!.publish({
                    destination: '/app/session.join',
                    body: JSON.stringify({
                        sessionId,
                        playerName,
                        isDm,
                        joinNonce,
                    }),
                });
            },
            onDisconnect: () => {
                console.log('WebSocket disconnected');
            },
            onStompError: (frame) => {
                console.error('STOMP error', frame);
            },
        });

        this.client.activate();
    }

    private handleEvent(msg: WsMessage<unknown>) {
        const store = useGameStore.getState();

        switch (msg.type) {
            case 'SESSION_STATE':
                // SESSION_STATE приходит только через join/private каналы,
                // но на всякий случай игнорируем здесь
                break;

            case 'TOKEN_MOVED':
            case 'TOKEN_ADDED':
            case 'TOKEN_ASSIGNED':
                store.moveToken(msg.payload as TokenDto);
                break;

            // Отдельный кейс для HP — семантически это не "move"
            case 'TOKEN_HP':
                store.moveToken(msg.payload as TokenDto);
                break;

            case 'TOKEN_REMOVED':
                store.removeToken(msg.payload as string);
                break;

            case 'MAP_OBJECT_ADDED':
                store.addObject(msg.payload as MapObjectDto);
                break;

            case 'MAP_OBJECT_REMOVED':
                store.removeObject(msg.payload as string);
                break;

            // Этот кейс раньше отсутствовал — карта у игроков не обновлялась
            case 'MAP_UPDATED':
                store.applyMapLayoutUpdate(msg.payload as MapLayoutUpdateDto);
                break;

            case 'PLAYER_JOINED':
                store.addPlayer(msg.payload as PlayerDto);
                break;

            case 'PLAYER_LEFT':
                // TODO: removePlayer когда добавим
                break;

            case 'MAP_BACKGROUND_UPDATED':
                store.setBackground(msg.payload as string);
                break;
            default:
                console.warn('Unknown WS event:', msg.type);
                break;
        }
    }

    send(destination: string, payload: unknown) {
        if (!this.client?.connected) {
            console.warn('WS not connected, dropping message to', destination);
            return;
        }

        const headers: StompHeaders = {
            sessionId: this.sessionId ?? '',
            playerId: this.playerId ?? '',
        };

        this.client.publish({
            destination: `/app${destination}`,
            headers,
            body: JSON.stringify(payload),
        });
    }

    disconnect() {
        this.client?.deactivate();
        this.client = null;
    }

    getPlayerId(): string | null {
        return this.playerId;
    }
}

export const wsClient = new WsClient();