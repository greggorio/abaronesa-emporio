package com.baronesa.website.controller;

import com.baronesa.website.entity.CategoriaFoto;
import com.baronesa.website.repository.CategoriaFotoRepository;
import com.baronesa.website.repository.FotoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/galeria/categorias")
@RequiredArgsConstructor
public class CategoriaFotoController {

    private final CategoriaFotoRepository categoriaFotoRepository;
    private final FotoRepository fotoRepository;

    /**
     * Endpoint público - lista categorias ordenadas
     * Por padrão lista apenas ativas. Admin pode solicitar todas com includeInactive=true
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listar(@RequestParam(required = false, defaultValue = "false") boolean includeInactive) {
        try {
            List<CategoriaFoto> categorias;
            
            if (includeInactive) {
                categorias = categoriaFotoRepository.findAllByOrderByOrdemAsc();
            } else {
                categorias = categoriaFotoRepository.findAllByAtivoTrueOrderByOrdemAsc();
            }

            // Obter contagem de fotos por categoria em uma única consulta
            var counts = fotoRepository.countFotosByCategoria();
            Map<Long, Long> countMap = new HashMap<>();
            counts.forEach(c -> countMap.put(c.getCategoriaId(), c.getTotal()));

            // Montar resposta com contagem
            List<Map<String, Object>> response = categorias.stream().map(cat -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", cat.getId());
                map.put("nome", cat.getNome());
                map.put("ordem", cat.getOrdem());
                map.put("ativo", cat.getAtivo());
                map.put("createdAt", cat.getCreatedAt());
                map.put("photoCount", countMap.getOrDefault(cat.getId(), 0L));
                return map;
            }).toList();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erro ao listar categorias", e);
            return ResponseEntity.ok(List.of());
        }
    }

    /**
     * Endpoint admin - criar categoria
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> criar(@RequestBody Map<String, Object> request) {
        try {
            String nome = (String) request.get("nome");
            Integer ordem = request.get("ordem") != null ? (Integer) request.get("ordem") : 0;
            Boolean ativo = request.get("ativo") != null ? (Boolean) request.get("ativo") : true;

            if (nome == null || nome.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Nome é obrigatório"
                ));
            }

            CategoriaFoto categoria = CategoriaFoto.builder()
                    .nome(nome.trim())
                    .ordem(ordem)
                    .ativo(ativo)
                    .build();

            categoriaFotoRepository.save(categoria);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Categoria criada com sucesso",
                    "categoria", categoria
            ));
        } catch (Exception e) {
            log.error("Erro ao criar categoria", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Erro ao criar categoria: " + e.getMessage()
            ));
        }
    }

    /**
     * Endpoint admin - atualizar categoria
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<Map<String, Object>> atualizar(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        try {
            CategoriaFoto categoria = categoriaFotoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));

            if (request.containsKey("nome")) {
                String nome = (String) request.get("nome");
                if (nome != null && !nome.trim().isEmpty()) {
                    categoria.setNome(nome.trim());
                }
            }

            if (request.containsKey("ordem")) {
                categoria.setOrdem((Integer) request.get("ordem"));
            }

            if (request.containsKey("ativo")) {
                categoria.setAtivo((Boolean) request.get("ativo"));
            }

            categoriaFotoRepository.save(categoria);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Categoria atualizada com sucesso",
                    "categoria", categoria
            ));
        } catch (Exception e) {
            log.error("Erro ao atualizar categoria", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Endpoint admin - excluir categoria (somente se vazia)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    @Transactional
    public ResponseEntity<Map<String, Object>> excluir(@PathVariable Long id) {
        try {
            CategoriaFoto categoria = categoriaFotoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Categoria não encontrada"));

            // Verificar se existem fotos vinculadas
            if (fotoRepository.existsByCategoriaId(id)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Não é possível excluir uma categoria que possui fotos vinculadas. Remova as fotos primeiro."
                ));
            }

            // Excluir categoria
            categoriaFotoRepository.delete(categoria);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Categoria excluída com sucesso"
            ));
        } catch (Exception e) {
            log.error("Erro ao excluir categoria", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
