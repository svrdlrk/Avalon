import { Client, StompHeaders } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useGameStore } from '../store/gameStore';
import type {
    MapObjectDto,
    PlayerDto,
    SessionStateDto,
    TokenDto,
    WsMessage
} from '../types/types';

class WsClient {
    private client: Client | null = null;
    private sessionId: string | null = null;
    private playerId: string | null = null;

    private applySessionState(msg: WsMessage<SessionStateDto>, sid: string) {
        const state = msg.payload;
        this.playerId = state.myPlayerId;
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
            onConnect: () => {
                this.client!.subscribe(
                    `/topic/session/${sessionId}`,
                    (frame) => {
                        const msg: WsMessage<unknown> = JSON.parse(frame.body);
                        this.handleEvent(msg);
                    }
                );

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
                break;

            case 'TOKEN_MOVED':
            case 'TOKEN_ADDED':
            case 'TOKEN_ASSIGNED':
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

            case 'PLAYER_JOINED':
                store.addPlayer(msg.payload as PlayerDto);
                break;

            default:
                break;
        }
    }

    send(destination: string, payload: unknown) {
        if (!this.client?.connected) return;

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
}

export const wsClient = new WsClient();
