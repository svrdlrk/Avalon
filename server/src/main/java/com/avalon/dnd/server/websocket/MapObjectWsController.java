package com.avalon.dnd.server.websocket;

import com.avalon.dnd.server.model.Player;
import com.avalon.dnd.server.service.MapObjectService;
import com.avalon.dnd.server.service.SessionService;
import com.avalon.dnd.server.service.SessionValidationService;
import com.avalon.dnd.shared.*;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class MapObjectWsController {

    private final MapObjectService service;
    private final SessionService sessionService;
    private final SessionValidationService validationService;
    private final SimpMessagingTemplate messagingTemplate;

    public MapObjectWsController(MapObjectService service,
                                 SessionService sessionService,
                                 SessionValidationService validationService,
                                 SimpMessagingTemplate messagingTemplate) {
        this.service = service;
        this.sessionService = sessionService;
        this.validationService = validationService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/map.object.create")
    public void create(MapObjectCreateRequest request,
                       @Header("playerId") String playerId,
                       @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        var session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        var obj = service.create(request, player);

        long version = session.incrementVersion();
        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                new WsMessage<>(WsEventType.MAP_OBJECT_ADDED, sessionId, version,
                        new MapObjectDto(
                                obj.getId(),
                                obj.getType(),
                                obj.getCol(),
                                obj.getRow(),
                                obj.getWidth(),
                                obj.getHeight()
                        ))
        );
    }

    @MessageMapping("/map.object.remove")
    public void remove(MapObjectRemoveEvent request,
                       @Header("playerId") String playerId,
                       @Header("sessionId") String sessionId) {

        Player player = validationService.validate(sessionId, playerId);
        var session = sessionService.getSession(sessionId);
        if (session == null) {
            throw new RuntimeException("Session not found");
        }

        String id = service.remove(request, player);
        long version = session.incrementVersion();
        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                new WsMessage<>(WsEventType.MAP_OBJECT_REMOVED, sessionId, version, id)
        );
    }
}