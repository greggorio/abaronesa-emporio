package com.baronesa.website.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@RestController
@RequestMapping("/api/themes/android")
@RequiredArgsConstructor
public class ThemeAndroidAssetController {

    @Value("${store.upload.android-assets-dir:uploads/android-assets}")
    private String androidAssetsDir;

    @Value("${store.upload.android-private-dir:uploads/android-private}")
    private String androidPrivateDir;

    @Value("${store.upload.base-url:http://localhost:8080/}")
    private String baseUrl;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> uploadAndroidAsset(
            @RequestParam("file") MultipartFile file,
            @RequestParam("type") String type,
            HttpServletRequest request) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Arquivo vazio."
            ));
        }

        AndroidAssetType assetType = AndroidAssetType.from(type);
        if (assetType == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Tipo de arquivo nao suportado."
            ));
        }

        if (!assetType.isValid(file)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Arquivo invalido para o tipo informado."
            ));
        }

        try {
            Path uploadPath = Paths.get(assetType.isPublicAsset ? androidAssetsDir : androidPrivateDir)
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(uploadPath);

            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "asset";
            String safeOriginal = new File(originalFilename).getName();
            String sanitizedOriginal = safeOriginal.replaceAll("[^a-zA-Z0-9.-]", "_");
            String filename = UUID.randomUUID() + "_" + assetType.prefix + "_" + sanitizedOriginal;
            Path destino = uploadPath.resolve(filename);

            Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            if (assetType == AndroidAssetType.ICONS_ZIP && !validateIconsZip(destino)) {
                Files.deleteIfExists(destino);
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Zip de icones invalido. Estrutura esperada nao encontrada."
                ));
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("type", assetType.type);
            response.put("filename", filename);
            response.put("public", assetType.isPublicAsset);

            if (assetType.isPublicAsset) {
                String resolvedBaseUrl = resolveBaseUrl(request);
                response.put("url", resolvedBaseUrl + "media/android-assets/" + filename);
                if (assetType == AndroidAssetType.GOOGLE_SERVICES) {
                    String packageName = extractPackageName(destino);
                    if (!packageName.isBlank()) {
                        response.put("packageName", packageName);
                    }
                }
            } else {
                response.put("path", destino.toString());
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao fazer upload de asset Android", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Erro ao fazer upload: " + e.getMessage()
            ));
        }
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

    private String extractPackageName(Path filePath) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(filePath.toFile());
            JsonNode clients = root.path("client");
            if (clients.isArray() && clients.size() > 0) {
                JsonNode packageNode = clients.get(0)
                        .path("client_info")
                        .path("android_client_info")
                        .path("package_name");
                if (!packageNode.isMissingNode()) {
                    return packageNode.asText("");
                }
            }
        } catch (Exception e) {
            log.warn("Nao foi possivel extrair package_name do google-services.json", e);
        }
        return "";
    }

    private boolean validateIconsZip(Path filePath) {
        Set<String> entries = new HashSet<>();
        try (ZipFile zipFile = new ZipFile(filePath.toFile())) {
            zipFile.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(ZipEntry::getName)
                    .forEach(entries::add);
        } catch (Exception e) {
            log.warn("Nao foi possivel ler zip de icones", e);
            return false;
        }

        if (entries.isEmpty()) {
            return false;
        }

        Set<String> normalized = normalizeEntries(entries, false);
        Set<String> normalizedStrip = normalizeEntries(entries, true);

        Set<String> requiredXml = Set.of(
                "mipmap-anydpi-v26/ic_launcher.xml",
                "mipmap-anydpi-v26/ic_launcher_round.xml",
                "drawable/ic_launcher_background.xml",
                "drawable-v24/ic_launcher_foreground.xml"
        );

        if (!containsAll(requiredXml, normalized) && !containsAll(requiredXml, normalizedStrip)) {
            return false;
        }

        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (String density : densities) {
            String base = "mipmap-" + density + "/";
            if (!hasIconPair(base, "ic_launcher", normalized, normalizedStrip)) {
                return false;
            }
            if (!hasIconPair(base, "ic_launcher_round", normalized, normalizedStrip)) {
                return false;
            }
        }

        return true;
    }

    private Set<String> normalizeEntries(Set<String> entries, boolean stripFirst) {
        Set<String> normalized = new HashSet<>();
        for (String entry : entries) {
            if (stripFirst) {
                int idx = entry.indexOf('/');
                if (idx >= 0 && idx + 1 < entry.length()) {
                    normalized.add(entry.substring(idx + 1));
                    continue;
                }
            }
            normalized.add(entry);
        }
        return normalized;
    }

    private boolean containsAll(Set<String> required, Set<String> entries) {
        return entries.containsAll(required);
    }

    private boolean hasIconPair(String base, String name, Set<String> entries, Set<String> entriesStrip) {
        String png = base + name + ".png";
        String webp = base + name + ".webp";
        return entries.contains(png) || entries.contains(webp) || entriesStrip.contains(png) || entriesStrip.contains(webp);
    }

    private enum AndroidAssetType {
        GOOGLE_SERVICES("googleServices", "google-services", true, "json"),
        FIREBASE_ADMIN("firebaseAdmin", "firebase-adminsdk", false, "json"),
        ICONS_ZIP("iconsZip", "icons", true, "zip");

        private final String type;
        private final String prefix;
        private final boolean isPublicAsset;
        private final String extension;

        AndroidAssetType(String type, String prefix, boolean isPublicAsset, String extension) {
            this.type = type;
            this.prefix = prefix;
            this.isPublicAsset = isPublicAsset;
            this.extension = extension;
        }

        static AndroidAssetType from(String type) {
            if (type == null) {
                return null;
            }
            for (AndroidAssetType assetType : values()) {
                if (assetType.type.equalsIgnoreCase(type.trim())) {
                    return assetType;
                }
            }
            return null;
        }

        boolean isValid(MultipartFile file) {
            String contentType = file.getContentType();
            String filename = file.getOriginalFilename();
            if (filename != null && !filename.toLowerCase(Locale.ROOT).endsWith("." + extension)) {
                return false;
            }
            if ("json".equals(extension)) {
                return contentType == null
                        || contentType.equals("application/json")
                        || contentType.equals("text/json")
                        || contentType.equals("application/octet-stream");
            }
            if ("zip".equals(extension)) {
                return contentType == null
                        || contentType.equals("application/zip")
                        || contentType.equals("application/x-zip-compressed")
                        || contentType.equals("multipart/x-zip")
                        || contentType.equals("application/octet-stream");
            }
            return false;
        }
    }
}
