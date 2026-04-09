package com.avalon.dnd.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Раздаёт статические ресурсы:
 *  /uploads/**  — загруженные пользователем карты (файловая система)
 *  /assets/**   — встроенные токены и объекты  (classpath:assets/)
 */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Value("${upload.path:./uploads}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Загруженные карты из файловой системы
        Path uploadDir = Paths.get(uploadPath).toAbsolutePath();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadDir + "/")
                .setCachePeriod(3600);

        // Встроенные ассеты токенов и объектов из classpath
        // Файлы лежат в server/src/main/resources/assets/
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/assets/")
                .setCachePeriod(86400); // 24h — они не меняются
    }
}