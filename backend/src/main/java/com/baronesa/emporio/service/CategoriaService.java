package com.baronesa.emporio.service;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dto.*;
import com.baronesa.emporio.entity.Categoria;
import com.baronesa.emporio.repository.CategoriaRepository;
import com.baronesa.emporio.util.FilterSpecificationBuilder;
import com.baronesa.emporio.service.TranslationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.Comparator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Page;


@Service
@RequiredArgsConstructor
public class CategoriaService {

    @Value("${store.upload.categoria-dir}")
    private String uploadCategoriaDir;

    private final CategoriaRepository categoriaRepository;
    private final MessageResolver messageResolver;
    private final TranslationService translationService;

    public List<CategoriaDTO> listarTodas() {
        return categoriaRepository.findAll().stream().map(categoria ->
                new CategoriaDTO(
                        categoria.getId(),
                        categoria.getNome(),
                        categoria.getIcone(),
                        categoria.getSubcategorias().stream().map(sub ->
                                new SubcategoriaDTO(
                                        sub.getId(),
                                        sub.getNome(),
                                        sub.getCover(),
                                        sub.getCategoria().getId(),
                                        sub.getCategoria().getNome()
                                )
                        ).toList(),
                        categoria.getCover(),
                        categoria.getExibirNoCardapio(),
                        categoria.getOrdem()
                )
        ).toList();
    }

    public CategoriaDTO buscarPorId(Long id) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));

        return new CategoriaDTO(
                categoria.getId(),
                categoria.getNome(),
                categoria.getIcone(),
                categoria.getSubcategorias().stream().map(sub ->
                        new SubcategoriaDTO(
                                sub.getId(),
                                sub.getNome(),
                                sub.getCover(),
                                sub.getCategoria().getId(),
                                sub.getCategoria().getNome()
                        )
                ).toList(),
                categoria.getCover(),
                categoria.getExibirNoCardapio(),
                categoria.getOrdem()
        );
    }

    public void criar(CategoriaRequest request) {
        Categoria categoria = Categoria.builder()
                .nome(request.nome())
                .icone(request.icone())
                .cover(request.cover())
                .exibirNoCardapio(request.exibirNoCardapio() != null ? request.exibirNoCardapio() : false)
                .ordem(request.ordem() != null ? request.ordem() : 0)
                .build();

        categoriaRepository.save(categoria);
        translationService.markSourceChanged("CATEGORY", categoria.getId(), "nome", categoria.getNome());

        // Exemplo de uso do messageResolver
        String successMsg = messageResolver.getMessage("categoria.success.create");
        // Use a mensagem conforme necessário, por exemplo, retornando em uma resposta ou logando
    }

    public void editar(Long id, CategoriaRequest request) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));

        categoria.setNome(request.nome());
        categoria.setIcone(request.icone());
        categoria.setCover(request.cover());
        categoria.setExibirNoCardapio(request.exibirNoCardapio() != null ? request.exibirNoCardapio() : categoria.getExibirNoCardapio());
        categoria.setOrdem(request.ordem() != null ? request.ordem() : categoria.getOrdem());

        categoriaRepository.save(categoria);
        translationService.markSourceChanged("CATEGORY", categoria.getId(), "nome", categoria.getNome());

        // Exemplo de uso do messageResolver
        String successMsg = messageResolver.getMessage("categoria.success.edit");
        // Use a mensagem conforme necessário
    }

    public void deletar(Long id) {
        categoriaRepository.deleteById(id);
    }

    public List<CategoriaOptionDTO> listarOptions() {
        List<CategoriaOptionDTO> options = new ArrayList<>(categoriaRepository.findAll().stream()
                .map(c -> new CategoriaOptionDTO(c.getId(), c.getNome()))
                .sorted(Comparator.comparing(CategoriaOptionDTO::label))
                .toList());
        options.add(0, new CategoriaOptionDTO(null, "Sem categoria"));
        return options;
    }

    /**
     * Lista apenas as categorias que devem aparecer no cardápio
     */
    public List<CategoriaDTO> listarParaCardapio() {
        return categoriaRepository.findAll().stream()
                .filter(categoria -> Boolean.TRUE.equals(categoria.getExibirNoCardapio()))
                .sorted(Comparator.comparing(Categoria::getOrdem).thenComparing(Categoria::getNome))
                .map(categoria ->
                        new CategoriaDTO(
                                categoria.getId(),
                                categoria.getNome(),
                                categoria.getIcone(),
                                categoria.getSubcategorias().stream().map(sub ->
                                        new SubcategoriaDTO(
                                                sub.getId(),
                                                sub.getNome(),
                                                sub.getCover(),
                                                sub.getCategoria().getId(),
                                                sub.getCategoria().getNome()
                                        )
                                ).toList(),
                                categoria.getCover(),
                                categoria.getExibirNoCardapio(),
                                categoria.getOrdem()
                        )
                ).toList();
    }

    /**
     * Faz upload da imagem de capa de uma categoria
     * @param categoriaId ID da categoria
     * @param arquivo Arquivo de imagem
     * @return URL da imagem salva
     * @throws IOException se houver erro no upload
     */
    public String uploadCover(Long categoriaId, MultipartFile arquivo) throws IOException {
        Categoria categoria = categoriaRepository.findById(categoriaId)
                .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));

        if (arquivo.isEmpty()) {
            throw new IllegalArgumentException("O arquivo está vazio");
        }

        String originalName = arquivo.getOriginalFilename();
        String extension = originalName != null ? originalName.substring(originalName.lastIndexOf('.') + 1) : "bin";
        String filename = UUID.randomUUID() + "." + extension;

        Path categoriaDir = Paths.get(uploadCategoriaDir, String.valueOf(categoriaId));
        Files.createDirectories(categoriaDir);

        Path filePath = categoriaDir.resolve(filename);
        Files.copy(arquivo.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        String fileUrl = "/media/categorias/" + categoriaId + "/" + filename;
        categoria.setCover(fileUrl);

        categoriaRepository.save(categoria);

        // Retorna a URL da imagem salva
        return fileUrl;
    }

}
