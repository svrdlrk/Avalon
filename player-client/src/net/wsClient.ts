import { Client, StompHeaders } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { useGameStore } from '../store/gameStore';
import { DEFAULT_SERVER_BASE_URL, normalizeServerBaseUrl } from '../config/runtime';
import type {
    MapLayoutUpdateDto,
    SessionStateDto,
    WsMessage,
} from '../types/types';

class WsClient {
    private client:    Client | null = null;
    private sessionId: string | null = null;
    private playerId:  string | null = null;
    private serverBaseUrl = DEFAULT_SERVER_BASE_URL;
    private onConnectedCallback: (() => void) | null = null;
    private connectedOnce = false;

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

    /**
     * When the page is served by the Vite dev server (e.g. http://192.168.0.5:5173)
     * and the resolved Spring Boot URL is on the *same host* but a *different port*
     * (e.g. http://192.168.0.5:8080), route everything through the Vite proxy
     * instead of connecting directly to port 8080.
     *
     * This is the key fix for mobile devices on the same WiFi network: the phone
     * can reach port 5173 (Vite) but Windows Firewall often blocks port 8080 for
     * connections that don't originate from localhost. By using the page origin we
     * make every WS and image request travel through Vite's proxy, which forwards
     * to localhost:8080 on the host machine where it is always reachable.
     */
    private resolveEffectiveBaseUrl(rawServerUrl: string): string {
        if (typeof window === 'undefined') return rawServerUrl;
        try {
            const server = new URL(rawServerUrl);
            const pageHost = window.location.hostname;
            const serverHost = server.hostname;
            const pagePort  = window.location.port  || (window.location.protocol === 'https:' ? '443' : '80');
            const serverPort = server.port || (server.protocol === 'https:' ? '443' : '80');

            if (pageHost === serverHost && pagePort !== serverPort) {
                // Same host, different port → Vite dev proxy scenario.
                // Return the page origin so WS and asset URLs go to port 5173
                // and are proxied by Vite to port 8080.
                return window.location.origin;
            }
        } catch {
            // Ignore malformed URLs
        }
        return rawServerUrl;
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
                const msg: WsMessage<unknown> = JSON.parse(frame.body);
                if (msg.type === 'SESSION_STATE') {
                    this.applySessionState(msg as WsMessage<SessionStateDto>, sid);
                } else if (msg.type === 'MAP_UPDATED') {
                    useGameStore.getState().applyMapLayoutUpdate(msg.payload as MapLayoutUpdateDto);
                } else if (msg.type === 'COMMAND_REJECTED') {
                    useGameStore.getState().setCommandError(String(msg.payload ?? 'Command rejected'));
                }
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
        projectorToken?: string,
    ) {
        this.disconnect();
        this.onConnectedCallback = onConnected;
        this.connectedOnce = false;

        const cleanSessionId = this.normalizeSessionId(sessionId) ?? sessionId;
        this.sessionId = cleanSessionId;
        this.playerId  = null;

        // Normalise the URL the user entered, then decide whether we should
        // route through the Vite proxy (mobile / LAN scenario).
        const normalised = this.normalizeServerUrl(serverUrl);
        this.serverBaseUrl = this.resolveEffectiveBaseUrl(normalised);

        const joinNonce = crypto.randomUUID();

        this.client = new Client({
            // SockJS URL: points to the effective base (page origin when proxied,
            // direct Spring Boot URL otherwise).
            webSocketFactory: () => new SockJS(`${this.serverBaseUrl}/ws`),
            reconnectDelay: 5000,

            onConnect: () => {
                console.log('[ws] connected via', this.serverBaseUrl);

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
                        playerName: projectorToken ? 'Projector' : playerName,
                        isDm,
                        isObserver: Boolean(projectorToken),
                        projectorToken,
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
