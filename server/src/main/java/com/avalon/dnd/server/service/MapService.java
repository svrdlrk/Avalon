package com.avalon.dnd.server.service;

import com.avalon.dnd.server.model.GameSession;
import com.avalon.dnd.server.model.MapEditorProjectImportDto;
import com.avalon.dnd.shared.WsEventType;
import com.avalon.dnd.shared.WsMessage;
import com.avalon.dnd.server.websocket.SessionWsController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class MapService {

    private static final long MAX_MAP_UPLOAD_BYTES = 50L * 1024L * 1024L;

    private final SessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final SessionWsController sessionWsController;
    private final MapBattleRulesService battleRulesService;
    private final MapWorkspaceImportService mapWorkspaceImportService;
    private final ObjectMapper objectMapper;
    private final Path uploadDir;

    public MapService(SessionService sessionService,
                      SimpMessagingTemplate messagingTemplate,
                      SessionWsController sessionWsController,
                      MapBattleRulesService battleRulesService,
                      MapWorkspaceImportService mapWorkspaceImportService,
                      ObjectMapper objectMapper,
                      @Value("${upload.path:./uploads/maps/finished}") String uploadPath) {
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
        this.sessionWsController = sessionWsController;
        this.battleRulesService = battleRulesService;
        this.mapWorkspaceImportService = mapWorkspaceImportService;
        this.objectMapper = objectMapper;
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
        synchronized (session) {
            importMatchingWorkspace(session, normalizedSessionId, originalName, url);
            session.setBackgroundUrl(url);
            session.incrementVersion();
            battleRulesService.computeVisibility(session);
        }

        sessionWsController.broadcastMapLayout(
                session,
                WsEventType.MAP_UPDATED,
                battleRulesService.buildMapLayout(session, null),
                false
        );
        sessionWsController.broadcastSessionState(session);

        return url;
    }

    public String getBackgroundUrl(String sessionId) {
        GameSession session = sessionService.getSession(normalizeSessionId(sessionId));
        return session != null ? session.getBackgroundUrl() : null;
    }

    private boolean importMatchingWorkspace(GameSession session, String sessionId, String originalName, String uploadedUrl) {
        Path workspace = findMatchingWorkspace(originalName);
        if (workspace == null) {
            return false;
        }
        try {
            MapEditorProjectImportDto dto = objectMapper.readValue(workspace.toFile(), MapEditorProjectImportDto.class);
            mapWorkspaceImportService.apply(session, sessionId, dto);
            session.setBackgroundUrl(uploadedUrl);
            return session.getWallLayer() != null && session.getWallLayer().path("paths").isArray();
        } catch (Exception ignored) {
            return false;
        }
    }

    private Path findMatchingWorkspace(String originalName) {
        String baseName = stripExtension(originalName);
        if (baseName == null || baseName.isBlank()) {
            return null;
        }
        String normalizedBase = normalizeKey(baseName);
        String normalizedFileName = normalizeKey(originalName);
        List<Path> fallbackMatches = new ArrayList<>();

        for (Path root : resolveProjectRoots()) {
            Path finishedRoot = root.resolve("uploads/maps/finished").toAbsolutePath().normalize();
            if (!Files.isDirectory(finishedRoot)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(finishedRoot, 3)) {
                for (Path mapFile : walk
                        .filter(Files::isRegularFile)
                        .filter(path -> "map.json".equalsIgnoreCase(path.getFileName().toString()))
                        .toList()) {
                    Path folder = mapFile.getParent();
                    String folderName = folder == null ? "" : normalizeKey(folder.getFileName().toString());
                    if (folderName.equals(normalizedBase)) {
                        return mapFile;
                    }
                    if (workspaceBackgroundMatches(mapFile, normalizedFileName)) {
                        fallbackMatches.add(mapFile);
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return fallbackMatches.size() == 1 ? fallbackMatches.get(0) : null;
    }

    private boolean workspaceBackgroundMatches(Path mapFile, String normalizedFileName) {
        if (normalizedFileName == null || normalizedFileName.isBlank()) {
            return false;
        }
        try {
            MapEditorProjectImportDto dto = objectMapper.readValue(mapFile.toFile(), MapEditorProjectImportDto.class);
            String background = MapWorkspaceImportService.extractBackgroundUrl(
                    dto.getBackgroundLayer(),
                    dto.getBackgroundUrl(),
                    dto.getReferenceOverlayLayer()
            );
            return normalizeKey(lastSegment(background)).equals(normalizedFileName);
        } catch (Exception ignored) {
            return false;
        }
    }

    private String normalizeSessionId(String sessionId) {
        if (sessionId == null) return null;
        String normalized = sessionId.trim();
        int comma = normalized.indexOf(',');
        if (comma >= 0) normalized = normalized.substring(0, comma).trim();
        return normalized;
    }

    private List<Path> resolveProjectRoots() {
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        addProjectRoot(roots, System.getProperty("avalon.project.root"));
        addProjectRoot(roots, System.getenv("AVALON_PROJECT_ROOT"));
        addProjectRoot(roots, Path.of("").toAbsolutePath().normalize().toString());
        return new ArrayList<>(roots);
    }

    private void addProjectRoot(LinkedHashSet<Path> roots, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            Path found = findProjectRoot(Path.of(raw).toAbsolutePath().normalize());
            if (found != null) {
                roots.add(found);
            }
        } catch (Exception ignored) {
        }
    }

    private Path findProjectRoot(Path start) {
        for (Path current = start; current != null; current = current.getParent()) {
            if ((Files.exists(current.resolve("settings.gradle")) || Files.exists(current.resolve("gradlew.bat")))
                    && Files.isDirectory(current.resolve("uploads/maps/finished"))) {
                return current;
            }
        }
        return null;
    }

    private static String stripExtension(String filename) {
        String last = lastSegment(filename);
        if (last == null) {
            return null;
        }
        int dot = last.lastIndexOf('.');
        return dot > 0 ? last.substring(0, dot) : last;
    }

    private static String lastSegment(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String normalizeKey(String value) {
        return value == null ? "" : value.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
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
