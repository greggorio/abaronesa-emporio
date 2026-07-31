package com.baronesa.emporio.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Service
public class AiImageStorageService {
    
    @Value("${store.upload.signage-ai-dir:uploads/signage/ai}")
    private String storagePath;

    @Value("${store.upload.base-url:http://localhost:8080/}")
    private String baseUrl;
    
    private Path basePath;
    
    @PostConstruct
    public void init() {
        this.basePath = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(basePath);
            log.info("Diretório de storage de imagens IA inicializado: {}", basePath);
        } catch (IOException e) {
            throw new RuntimeException("Não foi possível criar diretório de storage: " + basePath, e);
        }
    }
    
    /**
     * Salva imagem no storage
     */
    public void saveImage(byte[] imageData, String hash) throws IOException {
        Path filePath = resolvePath(hash);
        Files.write(filePath, imageData);
        log.debug("Imagem salva: {}", filePath);
    }
    
    /**
     * Retorna o caminho completo do arquivo
     */
    public Path getImagePath(String hash) {
        return resolvePath(hash);
    }
    
    /**
     * Resolve a URL pública da imagem
     */
    public String resolveUrl(String hash) {
        String base = baseUrl != null ? baseUrl.trim() : "";
        if (!base.isEmpty() && !base.endsWith("/")) {
            base = base + "/";
        }
        String path = "uploads/signage/ai/" + hash + ".png";
        return base.isEmpty() ? ("/" + path) : (base + path);
    }
    
    /**
     * Verifica se imagem existe no cache
     */
    public boolean exists(String hash) {
        return Files.exists(resolvePath(hash));
    }
    
    /**
     * Deleta imagem do storage
     */
    public void deleteImage(String hash) throws IOException {
        Path filePath = resolvePath(hash);
        if (Files.exists(filePath)) {
            Files.delete(filePath);
            log.debug("Imagem deletada: {}", filePath);
        }
    }
    
    private Path resolvePath(String hash) {
        return basePath.resolve(hash + ".png");
    }
}
