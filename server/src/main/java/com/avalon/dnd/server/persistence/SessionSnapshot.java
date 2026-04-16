package com.avalon.dnd.server.persistence;

import com.avalon.dnd.server.model.*;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.InitiativeStateDto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * POJO для сериализации/десериализации GameSession в JSON.
 * Используется только сервером, не входит в shared.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionSnapshot {

    public String             id;
    public GridConfig         grid;
    public String             backgroundUrl;
    public Object             referenceOverlayLayer;
    public Object             terrainLayer;
    public Object             wallLayer;
    public Object             fogSettings;
    public List<String>       assetPackIds;
    public long               version;
    public List<PlayerSnapshot>     players;
    public List<TokenSnapshot>      tokens;
    public List<MapObjectSnapshot>  objects;
    public InitiativeStateDto initiative;   // nullable — may be null if not active

    public SessionSnapshot() {}

    // ---- nested POJOs ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerSnapshot {
        public String id;
        public String name;
        public String sessionId;
        public String role; // "DM" or "PLAYER"

        public PlayerSnapshot() {}

        public static PlayerSnapshot from(Player p) {
            PlayerSnapshot s = new PlayerSnapshot();
            s.id = p.getId();
            s.name = p.getName();
            s.sessionId = p.getSessionId();
            s.role = p.getRole().name();
            return s;
        }

        public Player toModel() {
            return new Player(id, name, sessionId,
                    Role.valueOf(role != null ? role : "PLAYER"));
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TokenSnapshot {
        public String id;
        public String name;
        public int col;
        public int row;
        public String ownerId;
        public String sessionId;
        public int hp;
        public int maxHp;
        public int gridSize;
        public String imageUrl;
        public boolean blocksMovement;
        public boolean blocksSight;

        public TokenSnapshot() {}

        public static TokenSnapshot from(Token t) {
            TokenSnapshot s = new TokenSnapshot();
            s.id = t.getId();
            s.name = t.getName();
            s.col = t.getCol();
            s.row = t.getRow();
            s.ownerId = t.getOwnerId();
            s.sessionId = t.getSessionId();
            s.hp = t.getHp();
            s.maxHp = t.getMaxHp();
            s.gridSize = t.getGridSize();
            s.imageUrl = t.getImageUrl();
            return s;
        }

        public Token toModel() {
            Token t = new Token(id, name, col, row, ownerId, sessionId);
            t.setHp(hp);
            t.setMaxHp(maxHp);
            t.setGridSize(gridSize);
            t.setImageUrl(imageUrl);
            return t;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MapObjectSnapshot {
        public String id;
        public String type;
        public int col;
        public int row;
        public int width;
        public int height;
        public String sessionId;
        public int gridSize;
        public String imageUrl;
        public boolean blocksMovement;
        public boolean blocksSight;

        public MapObjectSnapshot() {}

        public static MapObjectSnapshot from(MapObject o) {
            MapObjectSnapshot s = new MapObjectSnapshot();
            s.id = o.getId();
            s.type = o.getType();
            s.col = o.getCol();
            s.row = o.getRow();
            s.width = o.getWidth();
            s.height = o.getHeight();
            s.gridSize = o.getGridSize();
            s.imageUrl = o.getImageUrl();
            s.blocksMovement = o.isBlocksMovement();
            s.blocksSight = o.isBlocksSight();
            return s;
        }

        public MapObject toModel(String sessionId) {
            return new MapObject(id, type, col, row, width, height, sessionId, gridSize, imageUrl, blocksMovement, blocksSight);
        }
    }

    // ---- factory ----

    public static SessionSnapshot from(com.avalon.dnd.server.model.GameSession session) {
        SessionSnapshot snap = new SessionSnapshot();
        snap.id            = session.getId();
        snap.grid          = session.getGrid();
        snap.backgroundUrl = session.getBackgroundUrl();
        snap.referenceOverlayLayer = session.getReferenceOverlayLayer();
        snap.terrainLayer = session.getTerrainLayer();
        snap.wallLayer = session.getWallLayer();
        snap.fogSettings = session.getFogSettings();
        snap.assetPackIds = session.getAssetPackIds();
        snap.version       = session.getVersion();
        snap.initiative    = session.getInitiativeState();
        snap.players = session.getPlayers().values().stream()
                .map(PlayerSnapshot::from).toList();
        snap.tokens = session.getTokens().values().stream()
                .map(TokenSnapshot::from).toList();
        snap.objects = session.getObjects().values().stream()
                .map(MapObjectSnapshot::from).toList();
        return snap;
    }
}
