package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.CategoriaDespesaDTO;
import com.baronesa.emporio.dto.CategoriaDespesaOptionDTO;
import com.baronesa.emporio.dto.CategoriaDespesaRequest;
import com.baronesa.emporio.service.CategoriaDespesaService;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.CategoriaDespesaListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categorias-despesa")
@RequiredArgsConstructor
@Slf4j
public class CategoriaDespesaController extends BaseListController<CategoriaDespesaListService>
        implements FormConfigurableController {

    private final CategoriaDespesaListService listService;
    private final CategoriaDespesaService categoriaDespesaService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService; // NOVO - Substituiu HybridFormConfigRegistry

    @Override
    protected CategoriaDespesaListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "categorias-despesa";
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

    /* ----------------------------------------------------------------
     *  Endpoints CRUD
     * -------------------------------------------------------------- */

    @PostMapping
    public ResponseEntity<Void> criar(@RequestBody CategoriaDespesaRequest request) {
        categoriaDespesaService.criar(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> editar(@PathVariable Long id, @RequestBody CategoriaDespesaRequest request) {
        categoriaDespesaService.editar(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        categoriaDespesaService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoriaDespesaDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(categoriaDespesaService.buscarPorId(id));
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<CategoriaDespesaOptionDTO> options = categoriaDespesaService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }
}
