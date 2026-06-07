import { Client, StompHeaders } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useGameStore } from '../store/gameStore';
import { DEFAULT_SERVER_BASE_URL, normalizeServerBaseUrl } from '../config/runtime';
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
    private serverBaseUrl = DEFAULT_SERVER_BASE_URL;
    private onConnectedCallback: (() => void) | null = null;
    private connectedOnce = false;
    private privateSubscriptionKey: string | null = null;
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

    private createJoinNonce(): string {
        if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
            return crypto.randomUUID();
        }
        const bytes = new Uint8Array(16);
        if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
            crypto.getRandomValues(bytes);
        } else {
            for (let i = 0; i < bytes.length; i++) {
                bytes[i] = Math.floor(Math.random() * 256);
            }
        }
        bytes[6] = (bytes[6] & 0x0f) | 0x40;
        bytes[8] = (bytes[8] & 0x3f) | 0x80;
        const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0'));
        return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex.slice(8, 10).join('')}-${hex.slice(10).join('')}`;
    }

    private applySessionState(msg: WsMessage<SessionStateDto>, sid: string) {
        const state   = msg.payload;
        this.playerId = state.myPlayerId;
        useGameStore.getState().applyState(state, sid);
    }

    private handleSessionMessage(msg: WsMessage<unknown>, sid: string) {
        const store = useGameStore.getState();
        switch (msg.type) {
            case 'SESSION_STATE':
                this.applySessionState(msg as WsMessage<SessionStateDto>, sid);
                this.subscribePrivateChannel(sid);
                break;
            case 'MAP_UPDATED':
                store.applyMapLayoutUpdate(msg.payload as MapLayoutUpdateDto);
                break;
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
            case 'PLAYER_JOINED':
                store.addPlayer(msg.payload as PlayerDto);
                break;
            case 'PLAYER_LEFT':
                store.removePlayer(msg.payload as string);
                break;
            case 'MAP_BACKGROUND_UPDATED':
                store.setBackground(msg.payload as string | null);
                break;
            case 'INITIATIVE_UPDATED':
                store.setInitiative(msg.payload as InitiativeStateDto);
                break;
            default:
                break;
        }
    }

    private subscribePrivateChannel(sid: string) {
        if (!this.client?.connected || !this.playerId) return;
        const key = `${sid}:${this.playerId}`;
        if (this.privateSubscriptionKey === key) return;
        this.privateSubscriptionKey = key;
        this.client.subscribe(
            `/topic/session/${sid}/private/${this.playerId}`,
            (frame) => {
                const msg: WsMessage<unknown> = JSON.parse(frame.body);
                this.handleSessionMessage(msg, sid);
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
        this.onConnectedCallback = onConnected;
        this.connectedOnce = false;
        // FIX: normalise once here so all subsequent send() calls use the
        // clean ID and the server-side validation never fails with
        // "Session not found" due to a trailing space or comma.
        const cleanSessionId = this.normalizeSessionId(sessionId) ?? sessionId;
        this.sessionId = cleanSessionId;
        this.playerId  = null;
        this.serverBaseUrl = this.normalizeServerUrl(serverUrl);
        this.privateSubscriptionKey = null;
        const joinNonce = this.createJoinNonce();

        this.client = new Client({
            webSocketFactory: () => new SockJS(`${this.serverBaseUrl}/ws`),
            reconnectDelay: 5000,

            onConnect: () => {
                console.log("CONNECTED");
                this.client!.subscribe(
                    `/topic/session/${cleanSessionId}`,
                    (frame) => {
                        const msg: WsMessage<unknown> = JSON.parse(frame.body);
                        this.handleSessionMessage(msg, cleanSessionId);
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

                            if (!this.connectedOnce) {
                                this.connectedOnce = true;
                                this.onConnectedCallback?.();
                            }
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

    // ---------------------------------------------------------------- send

    approveVisibilityShare(suggestionId: string) {
        this.send('/visibility.share.approve', { suggestionId });
    }

    send(destination: string, payload: unknown) {
        if (!this.client?.connected) {
            console.warn('[ws] not connected, dropping:', destination);
            return;
        }

        if (!this.sessionId) {
            console.warn(
                '[ws] send() called before session ID is set — dropping:',
                destination,
                { sessionId: this.sessionId },
            );
            return;
        }

        const headers: StompHeaders = {
            sessionId: this.sessionId,
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
        this.client = null;
        this.sessionId = null;
        this.playerId = null;
        this.connectedOnce = false;
        this.privateSubscriptionKey = null;
        this.onConnectedCallback = null;
    }

    getPlayerId(): string | null { return this.playerId; }

    getServerBaseUrl(): string { return this.serverBaseUrl; }

    getDefaultServerBaseUrl(): string { return DEFAULT_SERVER_BASE_URL; }

    private normalizeServerUrl(serverUrl: string): string {
        return normalizeServerBaseUrl(serverUrl);
    }
}

export const wsClient = new WsClient();
