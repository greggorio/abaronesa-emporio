package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.SubcategoriaDTO;
import com.baronesa.emporio.dto.SubcategoriaOptionDTO;
import com.baronesa.emporio.dto.SubcategoriaRequest;
import com.baronesa.emporio.entity.Categoria;
import com.baronesa.emporio.entity.Subcategoria;
import com.baronesa.emporio.repository.CategoriaRepository;
import com.baronesa.emporio.repository.SubcategoriaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubcategoriaService {

    private final SubcategoriaRepository subcategoriaRepository;
    private final CategoriaRepository categoriaRepository;

    @Value("${store.upload.subcategoria-dir}")
    private String uploadSubcategoriaDir;

    public List<SubcategoriaDTO> listarPorCategoria(Long categoriaId) {
        return subcategoriaRepository.findByCategoriaId(categoriaId).stream().map(sub ->
                new SubcategoriaDTO(sub.getId(), sub.getNome(), sub.getCover(), sub.getCategoria().getId(), sub.getCategoria().getNome())
        ).toList();

    }

    public void criar(SubcategoriaRequest request) {
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));

        Subcategoria subcategoria = Subcategoria.builder()
                .nome(request.nome())
                .cover(request.cover())
                .categoria(categoria)
                .build();

        subcategoriaRepository.save(subcategoria);
    }

    public void editar(Long id, SubcategoriaRequest request) {
        Subcategoria subcategoria = subcategoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subcategoria não encontrada"));

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));

        subcategoria.setNome(request.nome());
        subcategoria.setCover(request.cover());
        subcategoria.setCategoria(categoria);

        subcategoriaRepository.save(subcategoria);
    }

    public List<SubcategoriaOptionDTO> listarOptions() {
        List<SubcategoriaOptionDTO> options = new ArrayList<>(subcategoriaRepository.findAll().stream()
                .map(s -> new SubcategoriaOptionDTO(s.getId(), s.getNome()))
                .toList());
        options.add(0, new SubcategoriaOptionDTO(null, "Sem subcategoria"));
        return options;
    }

    public List<SubcategoriaOptionDTO> listarOptionsPorCategoria(Long categoriaId) {
        List<SubcategoriaOptionDTO> options = new ArrayList<>(subcategoriaRepository.findByCategoriaId(categoriaId).stream()
                .map(s -> new SubcategoriaOptionDTO(s.getId(), s.getNome()))
                .toList());
        options.add(0, new SubcategoriaOptionDTO(null, "Sem subcategoria"));
        return options;
    }

    public SubcategoriaDTO buscarPorId(Long id) {
        Subcategoria sub = subcategoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subcategoria não encontrada"));

        return new SubcategoriaDTO(
                sub.getId(),
                sub.getNome(),
                sub.getCover(),
                sub.getCategoria() != null ? sub.getCategoria().getId() : null,
                sub.getCategoria() != null ? sub.getCategoria().getNome() : null
        );
    }


    public void deletar(Long id) {
        subcategoriaRepository.deleteById(id);
    }

    public String uploadCover(Long subcategoriaId, MultipartFile arquivo) throws IOException {
        Subcategoria subcategoria = subcategoriaRepository.findById(subcategoriaId)
                .orElseThrow(() -> new RuntimeException("Subcategoria não encontrada"));

        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("Arquivo vazio");
        }

        String originalName = arquivo.getOriginalFilename();
        String extension = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1)
                : "bin";

        String filename = UUID.randomUUID() + "." + extension;

        Path subcategoriaDir = Paths.get(uploadSubcategoriaDir, String.valueOf(subcategoriaId));
        Files.createDirectories(subcategoriaDir);
        Path filePath = subcategoriaDir.resolve(filename);
        Files.copy(arquivo.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String url = "/media/subcategorias/" + subcategoriaId + "/" + filename;
        subcategoria.setCover(url);
        subcategoriaRepository.save(subcategoria);

        return url;
    }
}
