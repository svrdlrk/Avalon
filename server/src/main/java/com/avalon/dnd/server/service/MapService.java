package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.shared.WsEventType;
import com.avalon.dnd.shared.WsMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class MapService {

    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Path uploadDir = Paths.get("uploads");

    public MapService(SessionService sessionService,
                      SimpMessagingTemplate messagingTemplate) {
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException ignored) {
        }
    }

    /**
     * Сохраняет файл карты, обновляет сессию и рассылает MAP_BACKGROUND_UPDATED
     * всем подключённым клиентам (включая player-client).
     */
    public String uploadMap(String sessionId, MultipartFile file) throws IOException {
        GameSession session = sessionService.getSession(sessionId);
        if (session == null) throw new RuntimeException("Session not found: " + sessionId);

        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadDir.resolve(filename);
        file.transferTo(filePath.toFile());

        String url = "/uploads/" + filename;
        session.setBackgroundUrl(url);

        // FIX: broadcast MAP_BACKGROUND_UPDATED — player-client и dm-client получат новый фон
        long version = session.incrementVersion();
        messagingTemplate.convertAndSend(
                "/topic/session/" + sessionId,
                new WsMessage<>(WsEventType.MAP_BACKGROUND_UPDATED, sessionId, version, url)
        );

        return url;
    }

    public String getBackgroundUrl(String sessionId) {
        GameSession session = sessionService.getSession(sessionId);
        return session != null ? session.getBackgroundUrl() : null;
    }
}