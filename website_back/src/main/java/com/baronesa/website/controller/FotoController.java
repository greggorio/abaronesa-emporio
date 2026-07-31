package com.baronesa.website.controller;

import com.baronesa.website.entity.CategoriaFoto;
import com.baronesa.website.entity.Foto;
import com.baronesa.website.repository.CategoriaFotoRepository;
import com.baronesa.website.repository.FotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/galeria/fotos")
@RequiredArgsConstructor
public class FotoController {

    private final FotoRepository fotoRepository;
    private final CategoriaFotoRepository categoriaFotoRepository;

    @Value("${store.upload.galeria-dir:uploads/galeria}")
    private String galeriaUploadDir;

    @Value("${store.upload.base-url:http://localhost:8080/}")
    private String baseUrl;

    /**
     * Endpoint público - listar fotos por categoria
     */
    @GetMapping("/categoria/{categoriaId}")
    public ResponseEntity<List<Map<String, Object>>> listarPorCategoria(@PathVariable Long categoriaId) {
        try {
            List<Foto> fotos = fotoRepository.findByCategoriaIdOrderByCreatedAtDesc(categoriaId);

            List<Map<String, Object>> response = fotos.stream()
                    .map(foto -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", foto.getId());
                        map.put("url", foto.getUrl());
                        map.put("createdAt", foto.getCreatedAt());
                        return map;
                    })
                    .toList();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao listar fotos", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Endpoint público - listar todas as fotos agrupadas por categoria
     */
    @GetMapping("/todas")
    public ResponseEntity<Map<String, Object>> listarTodas() {
        try {
            List<CategoriaFoto> categorias = categoriaFotoRepository.findAllByOrderByOrdemAsc();
            Map<String, Object> response = new HashMap<>();

            for (CategoriaFoto categoria : categorias) {
                List<Foto> fotos = fotoRepository.findByCategoriaIdOrderByCreatedAtDesc(categoria.getId());

                List<Map<String, Object>> fotosMap = fotos.stream()
                        .map(foto -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", foto.getId());
                            map.put("url", foto.getUrl());
                            return map;
                        })
                        .toList();

                Map<String, Object> categoriaData = new HashMap<>();
                categoriaData.put("id", categoria.getId());
                categoriaData.put("nome", categoria.getNome());
                categoriaData.put("ordem", categoria.getOrdem());
                categoriaData.put("fotos", fotosMap);

                response.put(categoria.getId().toString(), categoriaData);
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao listar todas as fotos", e);
            return ResponseEntity.ok(Map.of());
        }
    }

    /**
     * Endpoint admin - upload de múltiplas fotos
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("categoriaId") Long categoriaId,
            @RequestParam("fotos") MultipartFile[] fotos) {

        try {
            CategoriaFoto categoria = categoriaFotoRepository.findById(categoriaId)
                    .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));

            // Resolver diretório absoluto e garantir existência
            Path uploadPath = Paths.get(galeriaUploadDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadPath);

            List<Foto> fotosSalvas = new ArrayList<>();

            for (MultipartFile file : fotos) {
                if (file.isEmpty()) continue;

                // Gerar nome único para o arquivo
                String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "foto";
                // Evitar path traversal mantendo apenas o nome do arquivo
                String safeOriginal = new File(originalFilename).getName();
                String filename = UUID.randomUUID() + "_" + safeOriginal;
                Path destino = uploadPath.resolve(filename);

                // Salvar arquivo (fora do diretório temporário do container)
                Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

                // Criar registro no banco com URL pública
                String url = baseUrl + "media/galeria/" + filename;
                Foto foto = Foto.builder()
                        .categoria(categoria)
                        .url(url)
                        .build();

                fotoRepository.save(foto);
                fotosSalvas.add(foto);
            }

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", fotosSalvas.size() + " foto(s) enviada(s) com sucesso",
                    "fotos", fotosSalvas
            ));

        } catch (Exception e) {
            log.error("Erro ao fazer upload de fotos", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Erro ao fazer upload: " + e.getMessage()
            ));
        }
    }

    /**
     * Endpoint admin - excluir foto
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> excluir(@PathVariable Long id) {
        try {
            Foto foto = fotoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Foto não encontrada"));

            // Excluir arquivo físico
            try {
                // Extrair apenas o nome do arquivo da URL
                String filename = foto.getUrl().substring(foto.getUrl().lastIndexOf("/") + 1);
                Path uploadPath = Paths.get(galeriaUploadDir).toAbsolutePath().normalize();
                Path filePath = uploadPath.resolve(filename);
                if (Files.deleteIfExists(filePath)) {
                    log.info("Arquivo físico excluído: {}", filePath.toAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("Erro ao excluir arquivo físico: " + e.getMessage());
            }

            // Excluir registro do banco
            fotoRepository.delete(foto);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Foto excluída com sucesso"
            ));
        } catch (Exception e) {
            log.error("Erro ao excluir foto", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
