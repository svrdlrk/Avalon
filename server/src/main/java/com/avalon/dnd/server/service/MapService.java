package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.shared.WsEventType;
import com.avalon.dnd.shared.WsMessage;
import com.avalon.dnd.server.websocket.SessionWsController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

@Service
public class MapService {

    private static final long MAX_MAP_UPLOAD_BYTES = 50L * 1024L * 1024L;

    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionWsController sessionWsController;
    private final Path uploadDir;

    public MapService(SessionService sessionService,
                      SimpMessagingTemplate messagingTemplate,
                      SessionWsController sessionWsController,
                      @Value("${upload.path:./uploads/maps/finished}") String uploadPath) {
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
        this.sessionWsController = sessionWsController;
        this.uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException ignored) {
        }
    }

    public String uploadMap(String sessionId, MultipartFile file) throws IOException {
        String normalizedSessionId = normalizeSessionId(sessionId);
        GameSession session = sessionService.getSession(normalizedSessionId);
        if (session == null) throw new RuntimeException("Session not found: " + normalizedSessionId);
        validateUpload(file);

        String originalName = file.getOriginalFilename();
        String safeName = (originalName == null || originalName.isBlank())
                ? "map.jpg"
                : Paths.get(originalName).getFileName().toString();

        String filename = UUID.randomUUID() + "_" + safeName;
        Path filePath = uploadDir.resolve(filename).normalize();
        if (!filePath.startsWith(uploadDir)) {
            throw new RuntimeException("Invalid upload filename");
        }
        file.transferTo(filePath.toFile());

        String url = "/uploads/maps/finished/" + filename;
        session.setBackgroundUrl(url);

        long version = session.incrementVersion();
        messagingTemplate.convertAndSend(
                "/topic/session/" + normalizedSessionId,
                new WsMessage<>(WsEventType.MAP_BACKGROUND_UPDATED, normalizedSessionId, version, url)
        );
        sessionWsController.broadcastSessionState(session);

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

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }
        if (file.getSize() > MAX_MAP_UPLOAD_BYTES) {
            throw new RuntimeException("File is too large");
        }

        String originalName = file.getOriginalFilename();
        String lowerName = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        boolean allowedExtension = lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".webp");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        boolean imageContentType = contentType.startsWith("image/");
        if (!allowedExtension || !imageContentType) {
            throw new RuntimeException("Only PNG, JPEG and WebP map images are allowed");
        }

        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(12);
            if (!looksLikeSupportedImage(header)) {
                throw new RuntimeException("Uploaded file does not match its declared image type");
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to inspect uploaded file", e);
        }
    }

    private static boolean looksLikeSupportedImage(byte[] header) {
        if (header == null || header.length < 4) {
            return false;
        }

        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return true;
        }

        if (header.length >= 4
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return true;
        }

        if (header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P') {
            return true;
        }

        return false;
    }
}
