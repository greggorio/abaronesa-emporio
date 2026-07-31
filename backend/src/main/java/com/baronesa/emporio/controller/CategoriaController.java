package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.CategoriaDTO;
import com.baronesa.emporio.dto.CategoriaOptionDTO;
import com.baronesa.emporio.dto.CategoriaRequest;
import com.baronesa.emporio.service.CategoriaService;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.CategoriaListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categorias")
@RequiredArgsConstructor
@Slf4j
public class CategoriaController extends BaseListController<CategoriaListService>
        implements FormConfigurableController {

    private final CategoriaListService listService;
    private final CategoriaService categoriaService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService; // NOVO

    @Override
    protected CategoriaListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "categorias";
    }

    @Override
    public BaseListService<?> getListService() {
        return listService;
    }

    @Override
    public FormConfigService getFormConfigService() {
        return formConfigService;
    }

    // O endpoint /form-config agora vem automaticamente da interface
    // com paginação incluída!

    /** Listagem completa sem paginação */
    @GetMapping("/all")
    public ResponseEntity<List<CategoriaDTO>> listarTodas() {
        return ResponseEntity.ok(categoriaService.listarTodas());
    }

    /** Listagem de categorias para o cardápio */
    @GetMapping("/cardapio")
    public ResponseEntity<List<CategoriaDTO>> listarParaCardapio() {
        return ResponseEntity.ok(categoriaService.listarParaCardapio());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoriaDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(categoriaService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<Void> criar(@RequestBody CategoriaRequest request) {
        categoriaService.criar(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> editar(@PathVariable Long id,
                                       @RequestBody CategoriaRequest request) {
        categoriaService.editar(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        categoriaService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/upload-cover")
    public ResponseEntity<String> uploadCover(@PathVariable Long id,
                                              @RequestParam("arquivo") MultipartFile arquivo) {
        try {
            String imageUrl = categoriaService.uploadCover(id, arquivo);
            return ResponseEntity.ok(imageUrl);

        } catch (Exception e) {
            return ResponseEntity.ok(e.getMessage());
        }
    }

    @PostMapping("/change-language")
    public ResponseEntity<Map<String, Object>> changeLanguage(@RequestParam String lang) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", messageResolver.getMessage("system.language.changed"),
                "currentLanguage", lang
        ));
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<CategoriaOptionDTO> options = categoriaService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }
}