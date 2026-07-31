package com.baronesa.website.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping("/api/themes/assets")
@RequiredArgsConstructor
public class ThemeAssetController {

    @Value("${store.upload.theme-assets-dir:uploads/theme-assets}")
    private String themeAssetsUploadDir;

    @Value("${store.upload.base-url:http://localhost:8080/}")
    private String baseUrl;

    /**
     * Endpoint admin - upload de arquivo de tema
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> uploadAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            HttpServletRequest request) {

        try {
            // Validar tipo de arquivo
            if (!isValidImageType(file.getContentType())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Tipo de arquivo não suportado. Apenas imagens são permitidas."
                ));
            }

            // Resolver diretório absoluto e garantir existência
            Path uploadPath = Paths.get(themeAssetsUploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            // Gerar nome único para o arquivo
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "asset";
            // Evitar path traversal mantendo apenas o nome do arquivo
            String safeOriginal = new File(originalFilename).getName();
            // Sanitizar nome do arquivo removendo caracteres especiais
            String sanitizedOriginal = safeOriginal.replaceAll("[^a-zA-Z0-9.-]", "_");
            String filename = UUID.randomUUID() + "_" + type + "_" + sanitizedOriginal;
            Path destino = uploadPath.resolve(filename);

            // Salvar arquivo
            Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            // Retornar URL pública
            String resolvedBaseUrl = resolveBaseUrl(request);
            String url = resolvedBaseUrl + "media/theme-assets/" + filename;

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Arquivo enviado com sucesso",
                    "url", url,
                    "filename", filename
            ));

        } catch (Exception e) {
            log.error("Erro ao fazer upload de asset de tema", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Erro ao fazer upload: " + e.getMessage()
            ));
        }
    }

    private boolean isValidImageType(String contentType) {
        return contentType != null && (
                contentType.equals("image/jpeg") ||
                contentType.equals("image/jpg") ||
                contentType.equals("image/png") ||
                contentType.equals("image/gif") ||
                contentType.equals("image/webp") ||
                contentType.equals("image/svg+xml")
        );
    }

    private String resolveBaseUrl(HttpServletRequest request) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.isBlank() || isLocalhost(normalized)) {
            String fromRequest = ServletUriComponentsBuilder.fromRequestUri(request)
                    .replacePath(request.getContextPath())
                    .replaceQuery(null)
                    .build()
                    .toUriString();
            return normalizeBaseUrl(fromRequest);
        }
        return normalized;
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private boolean isLocalhost(String value) {
        return value.contains("localhost") || value.contains("127.0.0.1");
    }

}
