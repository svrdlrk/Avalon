package com.avalon.dnd.server.controller;

import com.avalon.dnd.server.service.MapService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.IOException;

@RestController
@RequestMapping("/api/map")
public class MapController {

    private final MapService mapService;

    public MapController(MapService mapService) {
        this.mapService = mapService;
    }

    @PostMapping("/upload/{sessionId}")
    public ResponseEntity<String> upload(@PathVariable String sessionId,
                                         @RequestParam("file") MultipartFile file) {
        try {
            String url = mapService.uploadMap(sessionId, file);
            return ResponseEntity.ok(url);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body("Upload failed: " + e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.badRequest().body("Upload failed: " + e.getMessage());
        }
    }
}