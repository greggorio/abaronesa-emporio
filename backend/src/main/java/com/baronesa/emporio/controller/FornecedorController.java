package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.FornecedorDTO;
import com.baronesa.emporio.dto.FornecedorOptionDTO;
import com.baronesa.emporio.dto.FornecedorRequest;
import com.baronesa.emporio.service.FornecedorService;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.FornecedorListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fornecedores")
@RequiredArgsConstructor
@Slf4j
public class FornecedorController extends BaseListController<FornecedorListService>
        implements FormConfigurableController {

    private final FornecedorListService listService;
    private final FornecedorService fornecedorService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService;

    @Override
    protected FornecedorListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "fornecedores";
    }

    @Override
    public BaseListService<?> getListService() {
        return listService;
    }

    @Override
    public FormConfigService getFormConfigService() {
        return formConfigService;
    }

    // O endpoint /form-config vem automaticamente da interface

    @GetMapping("/{id}")
    public ResponseEntity<FornecedorDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(fornecedorService.buscarPorId(id));
    }

    @PostMapping
    public ResponseEntity<FornecedorDTO> criar(@Valid @RequestBody FornecedorRequest request) {
        FornecedorDTO fornecedor = fornecedorService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(fornecedor);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FornecedorDTO> editar(
            @PathVariable Long id,
            @Valid @RequestBody FornecedorRequest request) {
        return ResponseEntity.ok(fornecedorService.editar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        fornecedorService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<FornecedorOptionDTO> options = fornecedorService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }

    @GetMapping("/optionsfornecedor")
    public ResponseEntity<Map<String, Object>> listarOptionsFornecedor() {
        List<FornecedorOptionDTO> options = fornecedorService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }

    @GetMapping("/ativos")
    public ResponseEntity<List<FornecedorDTO>> listarAtivos() {
        return ResponseEntity.ok(fornecedorService.listarAtivos());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Map<String, Object>>> search(@RequestParam String search) {
        try {
            List<Map<String, Object>> fornecedores = fornecedorService.buscarParaLookup(search);
            return ResponseEntity.ok(fornecedores);
        } catch (Exception e) {
            log.error("Erro ao buscar fornecedores", e);
            return ResponseEntity.badRequest().body(List.of());
        }
    }
}