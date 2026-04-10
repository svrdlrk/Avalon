package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.shared.WsEventType;
import com.avalon.dnd.shared.WsMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;


@Service
public class MapService {

    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Path uploadDir;

    public MapService(SessionService sessionService,
                      SimpMessagingTemplate messagingTemplate,
                      @Value("${upload.path:./uploads/maps}") String uploadPath) {
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
        this.uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
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
        String normalizedSessionId = normalizeSessionId(sessionId);
        GameSession session = sessionService.getSession(normalizedSessionId);
        if (session == null) throw new RuntimeException("Session not found: " + normalizedSessionId);

        String originalName = file.getOriginalFilename();
        String safeName = (originalName == null || originalName.isBlank())
                ? "map.jpg"
                : Paths.get(originalName).getFileName().toString();
        String filename = UUID.randomUUID() + "_" + safeName;
        Path filePath = uploadDir.resolve(filename);
        file.transferTo(filePath.toFile());

        String url = "/uploads/maps/" + filename;
        session.setBackgroundUrl(url);

        long version = session.incrementVersion();
        messagingTemplate.convertAndSend(
                "/topic/session/" + normalizedSessionId,
                new WsMessage<>(WsEventType.MAP_BACKGROUND_UPDATED, normalizedSessionId, version, url)
        );

        return url;
    }

    public String getBackgroundUrl(String sessionId) {
        GameSession session = sessionService.getSession(normalizeSessionId(sessionId));
        return session != null ? session.getBackgroundUrl() : null;
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null) return null;
        String normalized = sessionId.trim();
        int comma = normalized.indexOf(',');
        if (comma >= 0) normalized = normalized.substring(0, comma).trim();
        return normalized;
    }

}