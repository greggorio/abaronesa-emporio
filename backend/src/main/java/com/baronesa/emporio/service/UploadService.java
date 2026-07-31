package com.baronesa.emporio.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class UploadService {

    @Value("${store.upload.produto-dir:uploads/produtos}")
    private String produtoUploadDir;

    @Value("${store.upload.certificado-dir:uploads/certificados}")
    private String certificadoUploadDir;

    @Value("${store.upload.base-url:http://localhost:8080/}")
    private String baseUrl;

    public String salvarImagemProduto(MultipartFile file) {
        return salvarArquivo(file, produtoUploadDir, "produtos");
    }

    public String salvarCertificadoDigital(MultipartFile file) {
        return salvarArquivo(file, certificadoUploadDir, "certificados");
    }

    public String salvarArquivo(MultipartFile file, String caminhoFisico, String tipo) {
        try {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("Arquivo inválido");
            }

            String originalName = file.getOriginalFilename();
            String filename = UUID.randomUUID() + "_" + (originalName != null ? originalName : "arquivo");

            Path dirPath = Paths.get(caminhoFisico);
            Files.createDirectories(dirPath);
            Path filePath = dirPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return filePath.toString(); // Retorna o caminho absoluto para certificados
        } catch (IOException e) {
            log.error("Erro ao salvar arquivo: {}", e.getMessage());
            throw new RuntimeException("Erro ao fazer upload do arquivo");
        }
    }

    public record SavedFile(String url, String absolutePath) {}
}
