package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
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
    private final Path uploadDir = Paths.get("uploads");

    public MapService(SessionService sessionService) {
        this.sessionService = sessionService;
        try { Files.createDirectories(uploadDir); } catch (IOException ignored) {}
    }

    public String uploadMap(String sessionId, MultipartFile file) throws IOException {
        GameSession session = sessionService.getSession(sessionId);
        if (session == null) throw new RuntimeException("Session not found");

        String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadDir.resolve(filename);
        file.transferTo(filePath.toFile());

        String url = "/uploads/" + filename;
        session.setBackgroundUrl(url);
        return url;
    }

    public String getBackgroundUrl(String sessionId) {
        GameSession session = sessionService.getSession(sessionId);
        return session != null ? session.getBackgroundUrl() : null;
    }
}