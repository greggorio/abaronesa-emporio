package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.SubcategoriaDTO;
import com.baronesa.emporio.dto.SubcategoriaOptionDTO;
import com.baronesa.emporio.dto.SubcategoriaRequest;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.SubcategoriaService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.SubcategoriaListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subcategorias")
@RequiredArgsConstructor
@Slf4j
public class SubcategoriaController extends BaseListController<SubcategoriaListService>
        implements FormConfigurableController {

    private final SubcategoriaListService listService;
    private final SubcategoriaService subcategoriaService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService; // NOVO - Substituiu HybridFormConfigRegistry

    @Override
    protected SubcategoriaListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "subcategorias";
    }

    @Override
    public BaseListService<?> getListService() {
        return listService;
    }

    @Override
    public FormConfigService getFormConfigService() {
        return formConfigService;
    }

    // O endpoint /form-config agora vem automaticamente da interface FormConfigurableController
    // com paginação incluída! Não precisa mais implementar aqui.

    /** Lista todas as subcategorias de uma categoria específica. */
    @GetMapping("/categoria/{categoriaId}")
    public ResponseEntity<List<SubcategoriaDTO>> listarPorCategoria(@PathVariable Long categoriaId) {
        return ResponseEntity.ok(subcategoriaService.listarPorCategoria(categoriaId));
    }

    @PostMapping
    public ResponseEntity<Void> criar(@RequestBody SubcategoriaRequest request) {
        subcategoriaService.criar(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> editar(@PathVariable Long id, @RequestBody SubcategoriaRequest request) {
        subcategoriaService.editar(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        subcategoriaService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<SubcategoriaOptionDTO> options = subcategoriaService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubcategoriaDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(subcategoriaService.buscarPorId(id));
    }

    @GetMapping("/categoria/{categoriaId}/options")
    public ResponseEntity<Map<String, Object>> listarOptionsPorCategoria(@PathVariable Long categoriaId) {
        List<SubcategoriaOptionDTO> options = subcategoriaService.listarOptionsPorCategoria(categoriaId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }

    /**
     * Upload de cover — rota permanece /{id}/upload para compatibilidade.
     */
    @PostMapping("/{id}/upload-cover")
    public ResponseEntity<String> uploadCover(@PathVariable Long id,
                                              @RequestParam("arquivo") MultipartFile arquivo) {
        try {
            String imageUrl = subcategoriaService.uploadCover(id, arquivo);

            return ResponseEntity.ok(imageUrl);
        } catch (Exception e) {
            return ResponseEntity.ok(e.getMessage());
        }
    }
}