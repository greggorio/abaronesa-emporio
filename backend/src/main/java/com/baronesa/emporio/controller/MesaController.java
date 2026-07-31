package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.MesaDTO;
import com.baronesa.emporio.dto.MesaOptionDTO;
import com.baronesa.emporio.dto.MesaReferenciaRequest;
import com.baronesa.emporio.dto.MesaRequest;
import com.baronesa.emporio.service.MesaService;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.MesaListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mesas")
@RequiredArgsConstructor
@Slf4j
public class MesaController extends BaseListController<MesaListService>
        implements FormConfigurableController {

    private final MesaListService listService;
    private final MesaService mesaService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService;

    @Override
    protected MesaListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "mesas";
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
    public ResponseEntity<Void> criar(@RequestBody MesaRequest request) {
        mesaService.criar(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> editar(@PathVariable Long id, @RequestBody MesaRequest request) {
        mesaService.editar(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        mesaService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<MesaDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(mesaService.buscarPorId(id));
    }

    @PatchMapping("/{mesaSlug}/referencia")
    public ResponseEntity<Void> atualizarReferencia(@PathVariable String mesaSlug,
                                                    @RequestBody MesaReferenciaRequest request) {
        mesaService.atualizarReferencia(mesaSlug, request.referencia());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<MesaOptionDTO> options = mesaService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }
}
