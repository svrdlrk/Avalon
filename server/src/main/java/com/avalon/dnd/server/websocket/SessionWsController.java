package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.service.MapBattleRulesService;
import com.avalon.dnd.server.service.AssetUrlNormalizer;
import com.avalon.dnd.server.service.MapObjectService;
import com.avalon.dnd.server.service.SessionConnectionRegistry;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.service.SessionValidationService;
import com.avalon.dnd.server.service.TokenService;
import com.avalon.dnd.shared.*;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import jakarta.annotation.PreDestroy;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Controller
public class SessionWsController {

    private static final long RECONNECT_GRACE_SECONDS = 15L;

    private final SessionService           sessionService;
    private final SimpMessagingTemplate    messaging;
    private final SessionValidationService validationService;
    private final SessionConnectionRegistry connectionRegistry;
    private final MapBattleRulesService    battleRulesService;
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "session-ws-cleanup");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentMap<String, ScheduledFuture<?>> pendingDisconnects = new java.util.concurrent.ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedSharedSnapshot> sharedSnapshotCache = new java.util.concurrent.ConcurrentHashMap<>();

    public SessionWsController(SessionService sessionService,
                               SimpMessagingTemplate messaging,
                               SessionValidationService validationService,
                               SessionConnectionRegistry connectionRegistry,
                               MapBattleRulesService battleRulesService) {
        this.sessionService    = sessionService;
        this.messaging         = messaging;
        this.validationService = validationService;
        this.connectionRegistry = connectionRegistry;
        this.battleRulesService = battleRulesService;
    }

    // ---- state builder ----

    public SessionStateDto buildState(GameSession session, String forPlayerId) {
        ensureVisibilityComputed(session, forPlayerId);
        return buildState(session, forPlayerId, snapshotShared(session), battleRulesService.getVisibilityForPlayer(session, forPlayerId));
    }

    private SessionStateDto buildState(GameSession session, String forPlayerId, SharedSessionSnapshot shared) {
        return buildState(session, forPlayerId, shared, battleRulesService.getVisibilityForPlayer(session, forPlayerId));
    }

    private java.util.List<TokenDto> filterTokensForViewer(SharedSessionSnapshot shared, com.avalon.dnd.shared.VisibilityStateDto viewerVisibility, String viewerPlayerId) {
        if (shared == null || shared.tokens() == null || viewerVisibility == null) {
            return java.util.List.of();
        }
        return shared.tokens().stream()
                .filter(t -> t != null && (isOwnedByViewer(t, viewerPlayerId)
                        || isAnyCellVisible(viewerVisibility, t.getCol(), t.getRow(),
                        Math.max(1, t.getGridSize()), Math.max(1, t.getGridSize()))))
                .toList();
    }

    private java.util.List<MapObjectDto> filterObjectsForViewer(SharedSessionSnapshot shared, com.avalon.dnd.shared.VisibilityStateDto viewerVisibility) {
        if (shared == null || shared.objects() == null || viewerVisibility == null) {
            return java.util.List.of();
        }
        return shared.objects().stream()
                .filter(o -> o != null && isAnyCellVisible(viewerVisibility, o.getCol(), o.getRow(),
                        Math.max(1, o.getWidth()), Math.max(1, o.getHeight())))
                .toList();
    }

    private SessionStateDto buildState(GameSession session,
                                       String forPlayerId,
                                       SharedSessionSnapshot shared,
                                       com.avalon.dnd.shared.VisibilityStateDto viewerVisibility) {
        Player currentPlayer = forPlayerId == null ? null : session.getPlayers().get(forPlayerId);
        boolean isDm = currentPlayer != null && currentPlayer.getRole() == com.avalon.dnd.server.model.Role.DM;
        java.util.List<com.avalon.dnd.shared.VisibilityShareSuggestionDto> suggestions =
                isDm ? shared.visibilityShareSuggestions() : java.util.List.of();
        java.util.List<TokenDto> tokensForViewer = isDm
                ? shared.tokens() == null ? java.util.List.of() : shared.tokens()
                : filterTokensForViewer(shared, viewerVisibility, forPlayerId);
        java.util.List<MapObjectDto> objectsForViewer = isDm
                ? shared.objects() == null ? java.util.List.of() : shared.objects()
                : filterObjectsForViewer(shared, viewerVisibility);
        return new SessionStateDto(
                forPlayerId,
                shared.grid(),
                tokensForViewer,
                shared.players(),
                objectsForViewer,
                shared.backgroundUrl(),
                shared.initiativeState(),
                viewerVisibility,
                isDm ? shared.referenceOverlayLayer() : null,
                shared.terrainLayer(),
                shared.wallLayer(),
                isDm ? shared.fogSettings() : null,
                isDm ? shared.microLocations() : java.util.List.of(),
                isDm ? shared.assetPackIds() : java.util.List.of(),
                isDm ? suggestions : java.util.List.of()
        );
    }

    public void broadcastSessionState(GameSession session) {
        if (session == null) return;
        ensureVisibilityComputed(session, null);
        SharedSessionSnapshot shared = snapshotShared(session);
        java.util.Map<String, com.avalon.dnd.shared.VisibilityStateDto> visibilityByPlayer = session.getVisibilityStatesByPlayer();
        com.avalon.dnd.shared.VisibilityStateDto merged = session.getVisibilityState();
        session.getPlayers().values().forEach(player -> {
            com.avalon.dnd.shared.VisibilityStateDto viewerVisibility = visibilityByPlayer == null ? null : visibilityByPlayer.get(player.getId());
            if (player.getRole() == com.avalon.dnd.server.model.Role.DM) {
                viewerVisibility = merged;
            }
            if (viewerVisibility == null) {
                viewerVisibility = merged;
            }
            messaging.convertAndSend(
                    privateTopic(session.getId(), player.getId()),
                    new WsMessage<>(WsEventType.SESSION_STATE,
                            session.getId(),
                            session.getVersion(),
                            buildState(session, player.getId(), shared, viewerVisibility)));
        });
    }

    private void ensureVisibilityComputed(GameSession session, String forPlayerId) {
        if (session == null) {
            return;
        }
        var states = session.getVisibilityStatesByPlayer();
        boolean needsRecompute = session.isVisibilityDirty()
                || session.getVisibilityState() == null
                || states == null
                || states.isEmpty()
                || states.size() != session.getPlayers().size()
                || (forPlayerId != null && !forPlayerId.isBlank() && !states.containsKey(forPlayerId));
        if (needsRecompute) {
            battleRulesService.computeVisibility(session);
        }
    }

    public MapLayoutUpdateDto buildMapLayout(GameSession session, String forPlayerId) {
        ensureVisibilityComputed(session, forPlayerId);
        return battleRulesService.buildMapLayout(session, forPlayerId);
    }

    public void broadcastMapLayout(GameSession session,
                                   WsEventType eventType,
                                   MapLayoutUpdateDto baseLayout,
                                   boolean includePublicTopic) {
        if (session == null || baseLayout == null) {
            return;
        }
        ensureVisibilityComputed(session, null);
        long version = session.getVersion();
        if (includePublicTopic) {
            messaging.convertAndSend(
                    "/topic/session/" + session.getId(),
                    new WsMessage<>(eventType, session.getId(), version, baseLayout));
        }
        java.util.Map<String, com.avalon.dnd.shared.VisibilityStateDto> visibilityByPlayer = session.getVisibilityStatesByPlayer();
        com.avalon.dnd.shared.VisibilityStateDto merged = session.getVisibilityState();
        for (Player player : session.getPlayers().values()) {
            com.avalon.dnd.shared.VisibilityStateDto viewerVisibility = visibilityByPlayer == null ? null : visibilityByPlayer.get(player.getId());
            if (player.getRole() == com.avalon.dnd.server.model.Role.DM) {
                viewerVisibility = merged;
            }
            if (viewerVisibility == null) {
                viewerVisibility = merged;
            }
            boolean recipientIsDm = player.getRole() == com.avalon.dnd.server.model.Role.DM;
            MapLayoutUpdateDto recipientLayout = new MapLayoutUpdateDto(
                    baseLayout.getGrid(),
                    recipientIsDm
                            ? baseLayout.getTokens()
                            : buildVisibleTokens(baseLayout.getTokens(), viewerVisibility, player.getId()),
                    recipientIsDm
                            ? baseLayout.getObjects()
                            : buildVisibleObjects(baseLayout.getObjects(), viewerVisibility),
                    baseLayout.getBackgroundUrl(),
                    viewerVisibility,
                    recipientIsDm ? baseLayout.getReferenceOverlayLayer() : null,
                    baseLayout.getTerrainLayer(),
                    baseLayout.getWallLayer(),
                    recipientIsDm ? baseLayout.getFogSettings() : null,
                    recipientIsDm ? baseLayout.getMicroLocations() : java.util.List.of(),
                    recipientIsDm ? baseLayout.getAssetPackIds() : java.util.List.of()
            );
            messaging.convertAndSend(
                    privateTopic(session.getId(), player.getId()),
                    new WsMessage<>(eventType, session.getId(), version, recipientLayout));
        }
    }

    // ---- join ----

    @MessageMapping("/session.join")
    public void join(JoinSessionRequestDto request,
                     @Header("simpSessionId") String wsSessionId) {

        if (request.getPlayerName() == null || request.getPlayerName().isBlank())
            throw new RuntimeException("Player name required");
        if (request.getJoinNonce() == null || request.getJoinNonce().isBlank())
            throw new RuntimeException("joinNonce required");

        String sessionId = normalizeSessionId(request.getSessionId());
        GameSession session = sessionService.getSession(sessionId);
        if (session == null) throw new RuntimeException("Session not found");

        Player player = sessionService.joinSession(
                sessionId, request.getPlayerName(), request.isDm(), request.getDmSecret());

        cancelPendingDisconnect(sessionId, player.getId());

        // Map WS session → game player for disconnect cleanup and authority resolution
        connectionRegistry.bind(wsSessionId, sessionId, player.getId());

        // Broadcast PLAYER_JOINED so DM refreshes its player list
        messaging.convertAndSend(
                "/topic/session/" + sessionId,
                new WsMessage<>(WsEventType.PLAYER_JOINED,
                        sessionId,
                        session.incrementVersion(),
                        new PlayerDto(player.getId(), player.getName(), player.getRole().name())));

        // Send full state to the joining player
        ensureVisibilityComputed(session, player.getId());
        SharedSessionSnapshot shared = snapshotShared(session);
        messaging.convertAndSend(
                joinTopic(sessionId, request.getJoinNonce()),
                new WsMessage<>(WsEventType.SESSION_STATE,
                        sessionId,
                        session.getVersion(),
                        buildState(session, player.getId(), shared)));
    }

    // ---- sync ----

    @MessageMapping("/session.sync")
    public void sync(@Header("sessionId") String sessionId,
                     @Header(value = "simpSessionId", required = false) String wsSessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        Player player = validationService.validateBound(normalizedSessionId, wsSessionId);
        GameSession session = sessionService.getSession(normalizedSessionId);
        ensureVisibilityComputed(session, player.getId());
        SharedSessionSnapshot shared = snapshotShared(session);
        messaging.convertAndSend(
                privateTopic(normalizedSessionId, player.getId()),
                new WsMessage<>(WsEventType.SESSION_STATE, normalizedSessionId,
                        session.getVersion(), buildState(session, player.getId(), shared)));
    }

    // ---- disconnect ----

    /**
     * Handles WebSocket disconnection.
     * The player remains in the session during a reconnect grace period,
     * and is only removed if they do not come back in time.
     */
    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String wsId = sha.getSessionId();
        if (wsId == null) return;

        SessionConnectionRegistry.PlayerBinding ref = connectionRegistry.unbind(wsId);
        if (ref == null) return;   // no record — maybe non-game connection

        scheduleDisconnectCleanup(ref);
    }

    // ---- helpers ----

    private void scheduleDisconnectCleanup(SessionConnectionRegistry.PlayerBinding ref) {
        String key = disconnectKey(ref.sessionId(), ref.playerId());
        cancelPendingDisconnect(ref.sessionId(), ref.playerId());

        ScheduledFuture<?> future = cleanupExecutor.schedule(() -> cleanupDisconnectedPlayer(ref),
                RECONNECT_GRACE_SECONDS, TimeUnit.SECONDS);
        pendingDisconnects.put(key, future);
    }

    private void cancelPendingDisconnect(String sessionId, String playerId) {
        if (sessionId == null || playerId == null) return;
        String key = disconnectKey(sessionId, playerId);
        ScheduledFuture<?> future = pendingDisconnects.remove(key);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void cleanupDisconnectedPlayer(SessionConnectionRegistry.PlayerBinding ref) {
        String key = disconnectKey(ref.sessionId(), ref.playerId());
        pendingDisconnects.remove(key);

        GameSession session = sessionService.getSession(ref.sessionId());
        if (session == null) return;

        Player leaving = session.getPlayers().get(ref.playerId());
        if (leaving == null) return;

        // If the player has already reconnected, keep them in session.
        if (connectionRegistry.isPlayerConnected(ref.sessionId(), ref.playerId())) {
            return;
        }

        session.getPlayers().remove(ref.playerId());
        session.markVisibilityDirty();

        // Unassign tokens owned by the leaving player
        List<TokenDto> released = new ArrayList<>();
        session.getTokens().values().forEach(token -> {
            if (ref.playerId().equals(token.getOwnerId())) {
                token.setOwnerId(null);
                released.add(TokenService.toDto(token));
            }
        });

        long version = session.incrementVersion();

        // Notify everyone the player left
        messaging.convertAndSend(
                "/topic/session/" + ref.sessionId(),
                new WsMessage<>(WsEventType.PLAYER_LEFT,
                        ref.sessionId(), version, ref.playerId()));

        // Notify everyone about unassigned tokens
        for (TokenDto t : released) {
            version = session.incrementVersion();
            messaging.convertAndSend(
                    "/topic/session/" + ref.sessionId(),
                    new WsMessage<>(WsEventType.TOKEN_ASSIGNED,
                            ref.sessionId(), version, t));
        }

        battleRulesService.computeVisibility(session);
        broadcastSessionState(session);
    }

    private static String disconnectKey(String sessionId, String playerId) {
        return sessionId + "::" + playerId;
    }

    private SharedSessionSnapshot snapshotShared(GameSession session) {
        if (session == null) {
            return new SharedSessionSnapshot(
                    null,
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    java.util.List.of(),
                    java.util.List.of(),
                    java.util.List.of()
            );
        }
        SharedSnapshotKey key = new SharedSnapshotKey(
                session.getVersion(),
                JsonPayloads.toNode(session.getVisibilityState()),
                JsonPayloads.toNode(session.getVisibilityStatesByPlayer()),
                JsonPayloads.toNode(session.getVisibilityShareSuggestions()),
                JsonPayloads.toNode(session.getGrid()),
                JsonPayloads.toNode(session.getTokens().values()),
                JsonPayloads.toNode(session.getPlayers().values()),
                JsonPayloads.toNode(session.getObjects().values()),
                JsonPayloads.toNode(session.getBackgroundUrl()),
                JsonPayloads.toNode(session.getInitiativeState()),
                JsonPayloads.toNode(session.getReferenceOverlayLayer()),
                JsonPayloads.toNode(session.getTerrainLayer()),
                JsonPayloads.toNode(session.getWallLayer()),
                JsonPayloads.toNode(session.getFogSettings()),
                JsonPayloads.toNode(session.getMicroLocations()),
                JsonPayloads.toNode(session.getAssetPackIds())
        );
        CachedSharedSnapshot cached = sharedSnapshotCache.get(session.getId());
        if (cached != null && cached.key().equals(key)) {
            return cached.snapshot();
        }
        SharedSessionSnapshot snapshot = buildSharedSnapshot(session);
        sharedSnapshotCache.put(session.getId(), new CachedSharedSnapshot(key, snapshot));
        return snapshot;
    }

    private SharedSessionSnapshot buildSharedSnapshot(GameSession session) {
        java.util.List<TokenDto> tokens = session.getTokens().values().stream().map(TokenService::toDto).toList();
        java.util.List<PlayerDto> players = session.getPlayers().values().stream()
                .map(p -> new PlayerDto(p.getId(), p.getName(), p.getRole().name()))
                .toList();
        java.util.List<MapObjectDto> objects = session.getObjects().values().stream().map(MapObjectService::toDto).toList();
        return new SharedSessionSnapshot(
                session.getGrid(),
                tokens,
                players,
                objects,
                AssetUrlNormalizer.normalizeMapBackground(
                        session.getBackgroundUrl(),
                        session.getReferenceOverlayLayer()
                ),
                session.getInitiativeState(),
                session.getReferenceOverlayLayer(),
                session.getTerrainLayer(),
                session.getWallLayer(),
                session.getFogSettings(),
                List.copyOf(session.getMicroLocations()),
                List.copyOf(session.getAssetPackIds()),
                List.copyOf(session.getVisibilityShareSuggestions())
        );
    }

    private record SharedSnapshotKey(
            long version,
            JsonNode visibilityState,
            JsonNode visibilityStatesByPlayer,
            JsonNode visibilityShareSuggestions,
            JsonNode grid,
            JsonNode tokens,
            JsonNode players,
            JsonNode objects,
            JsonNode backgroundUrl,
            JsonNode initiativeState,
            JsonNode referenceOverlayLayer,
            JsonNode terrainLayer,
            JsonNode wallLayer,
            JsonNode fogSettings,
            JsonNode microLocations,
            JsonNode assetPackIds
    ) {}

    private record CachedSharedSnapshot(SharedSnapshotKey key, SharedSessionSnapshot snapshot) {}

    private record SharedSessionSnapshot(GridConfig grid,
                                         java.util.List<TokenDto> tokens,
                                         java.util.List<PlayerDto> players,
                                         java.util.List<MapObjectDto> objects,
                                         String backgroundUrl,
                                         InitiativeStateDto initiativeState,
                                         JsonNode referenceOverlayLayer,
                                         JsonNode terrainLayer,
                                         JsonNode wallLayer,
                                         JsonNode fogSettings,
                                         java.util.List<com.avalon.dnd.shared.MicroLocationDto> microLocations,
                                         java.util.List<String> assetPackIds,
                                         java.util.List<com.avalon.dnd.shared.VisibilityShareSuggestionDto> visibilityShareSuggestions) {}

    private static String joinTopic(String sid, String nonce) {
        return "/topic/session/" + sid + "/join/" + nonce;
    }

    @PreDestroy
    public void shutdown() {
        pendingDisconnects.values().forEach(future -> future.cancel(false));
        pendingDisconnects.clear();
        cleanupExecutor.shutdownNow();
    }

    private java.util.List<TokenDto> buildVisibleTokens(java.util.List<TokenDto> tokens, com.avalon.dnd.shared.VisibilityStateDto visibility, String viewerPlayerId) {
        if (tokens == null || tokens.isEmpty()) {
            return java.util.List.of();
        }
        if (visibility == null) {
            return java.util.List.of();
        }
        return tokens.stream()
                .filter(t -> t != null && (isOwnedByViewer(t, viewerPlayerId)
                        || isAnyCellVisible(visibility, t.getCol(), t.getRow(),
                        Math.max(1, t.getGridSize()), Math.max(1, t.getGridSize()))))
                .toList();
    }

    private java.util.List<MapObjectDto> buildVisibleObjects(java.util.List<MapObjectDto> objects, com.avalon.dnd.shared.VisibilityStateDto visibility) {
        if (objects == null || objects.isEmpty()) {
            return java.util.List.of();
        }
        if (visibility == null) {
            return java.util.List.of();
        }
        return objects.stream()
                .filter(o -> o != null && isAnyCellVisible(visibility, o.getCol(), o.getRow(),
                        Math.max(1, o.getWidth()), Math.max(1, o.getHeight())))
                .toList();
    }

    private boolean isOwnedByViewer(TokenDto token, String viewerPlayerId) {
        return token != null && viewerPlayerId != null && !viewerPlayerId.isBlank()
                && viewerPlayerId.equals(token.getOwnerId());
    }

    private boolean isAnyCellVisible(com.avalon.dnd.shared.VisibilityStateDto visibility, int col, int row, int width, int height) {
        boolean[][] cells = visibility == null ? null : visibility.getVisibleCells();
        if (cells == null || cells.length == 0) {
            return false;
        }
        for (int r = row; r < row + height; r++) {
            if (r < 0 || r >= cells.length || cells[r] == null) {
                continue;
            }
            for (int c = col; c < col + width; c++) {
                if (c >= 0 && c < cells[r].length && cells[r][c]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String privateTopic(String sid, String playerId) {
        return "/topic/session/" + sid + "/private/" + playerId;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null) return null;
        String normalized = sessionId.trim();
        int comma = normalized.indexOf(',');
        if (comma >= 0) normalized = normalized.substring(0, comma).trim();
        return normalized;
    }
}
