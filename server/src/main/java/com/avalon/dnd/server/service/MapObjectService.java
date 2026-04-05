package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.*;
import com.avalon.dnd.shared.*;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MapObjectService {

    private final SessionService sessionService;

    public MapObjectService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public MapObject create(MapObjectCreateRequest request, Player player) {

        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can add objects");
        }

        var session = sessionService.getSession(player.getSessionId());
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        String id = UUID.randomUUID().toString();

        MapObject obj = new MapObject(
                id,
                request.getType(),
                request.getCol(),
                request.getRow(),
                request.getWidth(),
                request.getHeight(),
                player.getSessionId()
        );

        session.getObjects().put(id, obj);

        return obj;
    }

    public String remove(MapObjectRemoveEvent event, Player player) {

        if (player.getRole() != Role.DM) {
            throw new RuntimeException("Only DM can remove objects");
        }

        var session = sessionService.getSession(player.getSessionId());
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        MapObject removed = session.getObjects().remove(event.getObjectId());

        if (removed == null) {
            throw new RuntimeException("Object not found");
        }

        return event.getObjectId();
    }
}