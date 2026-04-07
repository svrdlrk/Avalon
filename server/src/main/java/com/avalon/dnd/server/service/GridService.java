package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.MapObject;
import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.model.Role;
import com.avalon.dnd.server.model.Token;
import com.avalon.dnd.shared.GridConfig;
import com.avalon.dnd.shared.MapLayoutUpdateDto;
import com.avalon.dnd.shared.TokenDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class GridService {

    private final SessionService sessionService;

    public GridService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public MapLayoutUpdateDto updateGrid(Player player, GridConfig requested) {

        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can change grid");
        }

        GameSession session = sessionService.getSession(player.getSessionId());
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        GridConfig g = normalize(requested);
        session.setGrid(g);

        int maxCol = g.getCols() - 1;
        int maxRow = g.getRows() - 1;

        for (Token t : session.getTokens().values()) {
            t.setCol(clamp(t.getCol(), 0, maxCol));
            t.setRow(clamp(t.getRow(), 0, maxRow));
        }

        for (MapObject o : session.getObjects().values()) {
            int col = clamp(o.getCol(), 0, maxCol);
            int row = clamp(o.getRow(), 0, maxRow);
            int w = Math.max(1, o.getWidth());
            int h = Math.max(1, o.getHeight());
            if (col + w > g.getCols()) {
                w = Math.max(1, g.getCols() - col);
            }
            if (row + h > g.getRows()) {
                h = Math.max(1, g.getRows() - row);
            }
            o.setCol(col);
            o.setRow(row);
            o.setWidth(w);
            o.setHeight(h);
        }

        List<TokenDto> tokenDtos = new ArrayList<>();
        for (Token t : session.getTokens().values()) {
            tokenDtos.add(toDto(t));
        }

        List<com.avalon.dnd.shared.MapObjectDto> objectDtos = new ArrayList<>();
        for (MapObject o : session.getObjects().values()) {
            objectDtos.add(new com.avalon.dnd.shared.MapObjectDto(
                    o.getId(), o.getType(), o.getCol(), o.getRow(),
                    o.getWidth(), o.getHeight()));
        }

        // ИСПРАВЛЕНО: теперь передаём 4 аргумента (backgroundUrl сохраняется)
        return new MapLayoutUpdateDto(
                g,
                tokenDtos,
                objectDtos,
                session.getBackgroundUrl()   // ← вот что не хватало
        );
    }

    private static TokenDto toDto(Token t) {
        return new TokenDto(
                t.getId(), t.getName(), t.getCol(), t.getRow(),
                t.getOwnerId(), t.getHp(), t.getMaxHp());
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static GridConfig normalize(GridConfig in) {
        int cell = clamp(in.getCellSize(), 24, 128);
        int cols = clamp(in.getCols(), 4, 60);
        int rows = clamp(in.getRows(), 4, 60);
        int ox = clamp(in.getOffsetX(), 0, 800);
        int oy = clamp(in.getOffsetY(), 0, 800);

        GridConfig out = new GridConfig();
        out.setCellSize(cell);
        out.setCols(cols);
        out.setRows(rows);
        out.setOffsetX(ox);
        out.setOffsetY(oy);
        return out;
    }
}
