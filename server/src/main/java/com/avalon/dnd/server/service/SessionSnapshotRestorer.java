package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.persistence.SessionSnapshot;
import org.springframework.stereotype.Component;

/**
 * Applies a serialized session snapshot back onto a live GameSession instance.
 * Keeps persistence orchestration separate from the mutation logic.
 */
@Component
public class SessionSnapshotRestorer {

    private final SessionService sessionService;

    public SessionSnapshotRestorer(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public GameSession restore(SessionSnapshot snap) {
        if (snap == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        // createSessionWithId returns existing OR creates new
        GameSession session = sessionService.createSessionWithId(snap.id);
        session.setDmSecret(snap.dmSecret);

        if (snap.grid != null) session.setGrid(snap.grid);
        session.setBackgroundUrl(
                AssetUrlNormalizer.normalizeMapBackground(
                        snap.backgroundUrl,
                        snap.referenceOverlayLayer
                )
        );
        session.setReferenceOverlayLayer(snap.referenceOverlayLayer);
        session.setTerrainLayer(snap.terrainLayer);
        session.setWallLayer(snap.wallLayer);
        session.setFogSettings(snap.fogSettings);
        session.setAssetPackIds(snap.assetPackIds);

        session.getTokens().clear();
        if (snap.tokens != null) {
            snap.tokens.forEach(ts -> {
                var t = ts.toModel();
                session.getTokens().put(t.getId(), t);
            });
        }

        session.getObjects().clear();
        if (snap.objects != null) {
            snap.objects.forEach(os -> {
                var o = os.toModel(snap.id);
                session.getObjects().put(o.getId(), o);
            });
        }

        session.getPlayers().clear();
        if (snap.players != null) {
            snap.players.forEach(ps -> {
                var p = ps.toModel();
                session.getPlayers().put(p.getId(), p);
            });
        }

        session.setInitiativeState(snap.initiative);
        session.setVisibilityState(snap.visibility);
        session.setSharedVisibilityState(snap.sharedVisibility);
        session.setVisibilityStatesByPlayer(snap.visibilityStatesByPlayer);
        session.setVisibilityShareSuggestions(snap.visibilityShareSuggestions);
        session.clearVisibilityDirty();
        session.setVersion(Math.max(session.getVersion(), snap.version) + 1);

        return session;
    }
}
