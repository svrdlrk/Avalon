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

    @Value("${upload.path:./uploads/maps/finished}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Загруженные карты и ассеты из корня проекта.
        // В репозитории всё лежит в top-level ./uploads, а не в server/src/main/resources/uploads.
        Path projectUploadRoot = Paths.get("uploads").toAbsolutePath().normalize();
        Path sourceUploadDir = Paths.get("server/src/main/resources/uploads").toAbsolutePath().normalize();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + projectUploadRoot + "/")
                .addResourceLocations("file:" + sourceUploadDir + "/")
                .addResourceLocations("classpath:/uploads/")
                .setCachePeriod(3600);

        Path projectAssetsDir = Paths.get("uploads/assets").toAbsolutePath().normalize();
        Path sourceAssetsDir = Paths.get("server/src/main/resources/assets").toAbsolutePath().normalize();
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("file:" + projectAssetsDir + "/")
                .addResourceLocations("classpath:/assets/")
                .addResourceLocations("file:" + sourceAssetsDir + "/")
                .setCachePeriod(86400);
    }
}