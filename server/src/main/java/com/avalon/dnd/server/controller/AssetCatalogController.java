package com.avalon.dnd.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.*;

import java.io.InputStream;

/**
 * Отдаёт каталог существ и объектов из assets/catalog.json
 * (файл лежит в src/main/resources/assets/catalog.json или classpath).
 * В dev-режиме можно просто скопировать catalog.json в resources/assets/.
 */
@RestController
@RequestMapping("/api/assets")
public class AssetCatalogController {

    private final ObjectMapper mapper = new ObjectMapper();

    @GetMapping("/catalog")
    public JsonNode getCatalog() {
        try {
            java.nio.file.Path fsPath = java.nio.file.Paths.get("uploads/assets/catalog.json");
            if (java.nio.file.Files.exists(fsPath)) {
                try (InputStream is = java.nio.file.Files.newInputStream(fsPath)) {
                    return mapper.readTree(is);
                }
            }

            ClassPathResource res = new ClassPathResource("assets/catalog.json");
            try (InputStream is = res.getInputStream()) {
                return mapper.readTree(is);
            }
        } catch (Exception e) {
            return mapper.createObjectNode()
                    .set("tokens", mapper.createArrayNode())
                    .deepCopy();
        }
    }
}