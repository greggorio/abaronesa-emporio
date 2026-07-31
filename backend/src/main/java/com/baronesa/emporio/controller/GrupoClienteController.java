package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.GrupoClienteDTO;
import com.baronesa.emporio.dto.GrupoClienteOptionDTO;
import com.baronesa.emporio.dto.GrupoClienteRequest;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.GrupoClienteService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.GrupoClienteListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/grupos-clientes")
@RequiredArgsConstructor
@Slf4j
public class GrupoClienteController extends BaseListController<GrupoClienteListService>
        implements FormConfigurableController {

    private final GrupoClienteListService listService;
    private final GrupoClienteService grupoClienteService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService; // NOVO - Substituiu HybridFormConfigRegistry

    @Override
    protected GrupoClienteListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "grupos-clientes";
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
    public ResponseEntity<Void> criar(@RequestBody GrupoClienteRequest request) {
        grupoClienteService.criar(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> editar(@PathVariable Long id, @RequestBody GrupoClienteRequest request) {
        grupoClienteService.editar(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        grupoClienteService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<GrupoClienteDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(grupoClienteService.buscarPorId(id));
    }

    /* ----------------------------------------------------------------
     *  Endpoints adicionais
     * -------------------------------------------------------------- */

    @GetMapping("/all")
    public ResponseEntity<List<GrupoClienteDTO>> listarTodos() {
        return ResponseEntity.ok(grupoClienteService.listarTodos());
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<GrupoClienteOptionDTO> options = grupoClienteService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }
}