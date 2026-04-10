import { Client, StompHeaders } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useGameStore } from '../store/gameStore';
import type {
    InitiativeStateDto,
    MapLayoutUpdateDto,
    MapObjectDto,
    PlayerDto,
    SessionStateDto,
    TokenDto,
    WsMessage,
} from '../types/types';

class WsClient {
    private client:    Client | null = null;
    private sessionId: string | null = null;
    private playerId:  string | null = null;
    private serverBaseUrl = 'http://localhost:8080';

    // ---------------------------------------------------------------- helpers

    /**
     * Strips leading/trailing whitespace and anything after the first comma.
     * Mirrors the server-side normalizeSessionId() so the IDs always match.
     */
    private normalizeSessionId(raw: string | null): string | null {
        if (raw == null) return null;
        let s = raw.trim();
        const comma = s.indexOf(',');
        if (comma >= 0) s = s.substring(0, comma).trim();
        return s;
    }

    private applySessionState(msg: WsMessage<SessionStateDto>, sid: string) {
        const state   = msg.payload;
        this.playerId = state.myPlayerId;
        useGameStore.getState().applyState(state, sid);
    }

    private subscribePrivateChannel(sid: string) {
        if (!this.client?.connected || !this.playerId) return;
        this.client.subscribe(
            `/topic/session/${sid}/private/${this.playerId}`,
            (frame) => {
                const msg: WsMessage<SessionStateDto> = JSON.parse(frame.body);
                if (msg.type === 'SESSION_STATE') this.applySessionState(msg, sid);
            },
        );
    }

    // ---------------------------------------------------------------- connect

    connect(
        serverUrl: string,
        sessionId: string,
        playerName: string,
        isDm: boolean,
        onConnected: () => void,
    ) {
        this.disconnect();

        // FIX: normalise once here so all subsequent send() calls use the
        // clean ID and the server-side validation never fails with
        // "Session not found" due to a trailing space or comma.
        const cleanSessionId = this.normalizeSessionId(sessionId) ?? sessionId;
        this.sessionId = cleanSessionId;
        this.playerId  = null;
        this.serverBaseUrl = this.normalizeServerUrl(serverUrl);
        const joinNonce = crypto.randomUUID();

        this.client = new Client({
            webSocketFactory: () => new SockJS(`${this.serverBaseUrl}/ws`),
            reconnectDelay: 5000,

            onConnect: () => {
                // Broadcast channel — all session events
                this.client!.subscribe(
                    `/topic/session/${cleanSessionId}`,
                    (frame) => {
                        const msg: WsMessage<unknown> = JSON.parse(frame.body);
                        this.handleEvent(msg);
                    },
                );

                // One-time join channel
                this.client!.subscribe(
                    `/topic/session/${cleanSessionId}/join/${joinNonce}`,
                    (frame) => {
                        const msg: WsMessage<SessionStateDto> = JSON.parse(frame.body);
                        if (msg.type === 'SESSION_STATE') {
                            this.applySessionState(msg, cleanSessionId);
                            this.subscribePrivateChannel(cleanSessionId);
                            onConnected();
                        }
                    },
                );

                this.client!.publish({
                    destination: '/app/session.join',
                    body: JSON.stringify({
                        sessionId: cleanSessionId,
                        playerName,
                        isDm,
                        joinNonce,
                    }),
                });
            },

            onDisconnect:  () => console.log('[ws] disconnected'),
            onStompError:  (frame) => console.error('[ws] STOMP error', frame),
        });

        this.client.activate();
    }

    // ---------------------------------------------------------------- events

    private handleEvent(msg: WsMessage<unknown>) {
        const store = useGameStore.getState();

        switch (msg.type) {
            case 'TOKEN_MOVED':
            case 'TOKEN_ADDED':
            case 'TOKEN_ASSIGNED':
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

            case 'MAP_UPDATED':
                store.applyMapLayoutUpdate(msg.payload as MapLayoutUpdateDto);
                break;

            case 'MAP_BACKGROUND_UPDATED':
                store.setBackground(msg.payload as string);
                break;

            case 'PLAYER_JOINED':
                store.addPlayer(msg.payload as PlayerDto);
                break;

            case 'PLAYER_LEFT':
                store.removePlayer(msg.payload as string);
                break;

            case 'INITIATIVE_UPDATED':
                store.setInitiative(msg.payload as InitiativeStateDto);
                break;

            case 'SESSION_STATE':
                // Handled via join / private channels only
                break;

            default:
                console.warn('[ws] unknown event:', msg.type);
        }
    }

    // ---------------------------------------------------------------- send

    send(destination: string, payload: unknown) {
        if (!this.client?.connected) {
            console.warn('[ws] not connected, dropping:', destination);
            return;
        }

        // FIX: guard against missing IDs — if either is absent the server will
        // reject the message with "Player not found"; log clearly instead of
        // silently succeeding and leaving the player unable to move tokens.
        if (!this.sessionId || !this.playerId) {
            console.warn(
                '[ws] send() called before session/player IDs are set — dropping:',
                destination,
                { sessionId: this.sessionId, playerId: this.playerId },
            );
            return;
        }

        const headers: StompHeaders = {
            sessionId: this.sessionId,
            playerId:  this.playerId,
        };
        this.client.publish({
            destination: `/app${destination}`,
            headers,
            body: JSON.stringify(payload),
        });
    }

    // ---------------------------------------------------------------- misc

    disconnect() {
        this.client?.deactivate();
        this.client    = null;
        this.sessionId = null;
        this.playerId  = null;
    }

    getPlayerId(): string | null { return this.playerId; }

    getServerBaseUrl(): string { return this.serverBaseUrl; }

    private normalizeServerUrl(serverUrl: string): string {
        try {
            return new URL(serverUrl).origin;
        } catch {
            return serverUrl.replace(/\/$/, '');
        }
    }
}

export const wsClient = new WsClient();