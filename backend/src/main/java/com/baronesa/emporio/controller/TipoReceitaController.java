package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.TipoReceitaDTO;
import com.baronesa.emporio.dto.TipoReceitaOptionDTO;
import com.baronesa.emporio.dto.TipoReceitaRequest;
import com.baronesa.emporio.service.TipoReceitaService;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.TipoReceitaListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tipos-receita")
@RequiredArgsConstructor
@Slf4j
public class TipoReceitaController extends BaseListController<TipoReceitaListService>
        implements FormConfigurableController {

    private final TipoReceitaListService listService;
    private final TipoReceitaService tipoReceitaService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService;

    @Override
    protected TipoReceitaListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "tipos-receita";
    }

    @Override
    public BaseListService<?> getListService() {
        return listService;
    }

    @Override
    public FormConfigService getFormConfigService() {
        return formConfigService;
    }

    /* ----------------------------------------------------------------
     *  Endpoints CRUD
     * -------------------------------------------------------------- */

    @PostMapping
    public ResponseEntity<Void> criar(@RequestBody TipoReceitaRequest request) {
        tipoReceitaService.criar(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> editar(@PathVariable Long id, @RequestBody TipoReceitaRequest request) {
        tipoReceitaService.editar(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        tipoReceitaService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TipoReceitaDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(tipoReceitaService.buscarPorId(id));
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<TipoReceitaOptionDTO> options = tipoReceitaService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }
}
