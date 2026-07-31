package com.baronesa.emporio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class SignageVideoStorageService {

    @Value("${store.upload.signage-dir:uploads/signage}")
    private String signageBaseDir;

    @Value("${store.upload.base-url:http://localhost:8080/}")
    private String baseUrl;

    @Value("${signage.video.retention.per-product:2}")
    private Integer retentionPerProduct;

    public String storeFromUrl(String sourceUrl, Long productId, String renderHash) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            throw new IllegalArgumentException("URL do vídeo é obrigatória");
        }
        String filename = buildFilename(renderHash);
        Path targetDir = Paths.get(signageBaseDir, "videos", String.valueOf(productId));
        Path targetPath = targetDir.resolve(filename);

        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar diretório de signage: " + e.getMessage(), e);
        }

        if (Files.exists(targetPath)) {
            return buildUrl(productId, filename);
        }

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(sourceUrl)).GET().build();

        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Falha ao baixar vídeo: status " + response.statusCode());
            }
            try (InputStream body = response.body()) {
                Files.copy(body, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            applyPermissions(targetPath);
            // Best-effort pruning: keep disk usage bounded even if old files were left behind.
            pruneOldVideos(productId, retentionPerProduct != null ? retentionPerProduct : 2);
            return buildUrl(productId, filename);
        } catch (Exception e) {
            throw new RuntimeException("Falha ao salvar vídeo no ERP: " + e.getMessage(), e);
        }
    }

    public int pruneOldVideos(Long productId, int keepLastN) {
        if (productId == null) {
            return 0;
        }
        if (keepLastN < 1) {
            keepLastN = 1;
        }

        Path targetDir = Paths.get(signageBaseDir, "videos", String.valueOf(productId));
        if (!Files.isDirectory(targetDir)) {
            return 0;
        }

        try {
            List<Path> mp4s = new ArrayList<>();
            try (var stream = Files.list(targetDir)) {
                stream.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase().endsWith(".mp4"))
                        .forEach(mp4s::add);
            }

            if (mp4s.size() <= keepLastN) {
                return 0;
            }

            mp4s.sort(Comparator.comparing((Path p) -> lastModifiedSafe(p)).reversed());
            List<Path> toDelete = mp4s.subList(Math.min(keepLastN, mp4s.size()), mp4s.size());

            int deleted = 0;
            for (Path p : new ArrayList<>(toDelete)) {
                try {
                    Files.deleteIfExists(p);
                    deleted++;
                } catch (Exception e) {
                    log.warn("Não foi possível remover vídeo antigo de signage '{}': {}", p, e.getMessage());
                }
            }

            if (deleted > 0) {
                log.info("Prune signage videos: produto {} -> removidos {} arquivo(s) (mantendo {}).",
                        productId, deleted, keepLastN);
            }
            return deleted;
        } catch (Exception e) {
            log.warn("Falha ao executar prune de vídeos do produto {}: {}", productId, e.getMessage());
            return 0;
        }
    }

    public int pruneAllProducts(int keepLastN) {
        if (keepLastN < 1) keepLastN = 1;

        Path baseDir = Paths.get(signageBaseDir, "videos");
        if (!Files.isDirectory(baseDir)) {
            return 0;
        }

        int deleted = 0;
        try (var stream = Files.list(baseDir)) {
            for (Path dir : stream.filter(Files::isDirectory).toList()) {
                Long productId = null;
                try {
                    productId = Long.parseLong(dir.getFileName().toString());
                } catch (Exception ignored) {
                    // ignore unknown dirs
                }
                if (productId != null) {
                    deleted += pruneOldVideos(productId, keepLastN);
                }
            }
        } catch (Exception e) {
            log.warn("Falha ao executar prune global de vídeos: {}", e.getMessage());
        }
        return deleted;
    }

    private static FileTime lastModifiedSafe(Path p) {
        try {
            return Files.getLastModifiedTime(p);
        } catch (Exception e) {
            return FileTime.fromMillis(0L);
        }
    }

    private String buildFilename(String renderHash) {
        String base = (renderHash != null && !renderHash.isBlank())
                ? renderHash.trim()
                : UUID.randomUUID().toString().replace("-", "");
        return base + ".mp4";
    }

    private String buildUrl(Long productId, String filename) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + "/uploads/signage/videos/" + productId + "/" + filename;
    }

    private void applyPermissions(Path targetPath) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-r--r--");
            Files.setPosixFilePermissions(targetPath, perms);
        } catch (UnsupportedOperationException e) {
            // ignore
        } catch (IOException e) {
            log.warn("Não foi possível ajustar permissões do vídeo: {}", e.getMessage());
        }
    }
}
