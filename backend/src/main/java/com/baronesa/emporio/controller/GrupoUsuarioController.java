package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.GrupoUsuarioDTO;
import com.baronesa.emporio.dto.GrupoUsuarioOptionDTO;
import com.baronesa.emporio.dto.GrupoUsuarioRequest;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.GrupoUsuarioService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.GrupoUsuarioListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/grupos-usuario")
@RequiredArgsConstructor
@Slf4j
public class GrupoUsuarioController extends BaseListController<GrupoUsuarioListService>
        implements FormConfigurableController {

    private final GrupoUsuarioListService listService;
    private final GrupoUsuarioService grupoUsuarioService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService;

    @Override
    protected GrupoUsuarioListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "grupos-usuario";
    }

    @Override
    public BaseListService<?> getListService() {
        return listService;
    }

    @Override
    public FormConfigService getFormConfigService() {
        return formConfigService;
    }

    @PostMapping
    public ResponseEntity<Void> criar(@RequestBody GrupoUsuarioRequest request) {
        grupoUsuarioService.criar(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> editar(@PathVariable Long id, @RequestBody GrupoUsuarioRequest request) {
        grupoUsuarioService.editar(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        grupoUsuarioService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<GrupoUsuarioDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(grupoUsuarioService.buscarPorId(id));
    }

    @GetMapping("/all")
    public ResponseEntity<List<GrupoUsuarioDTO>> listarTodos() {
        return ResponseEntity.ok(grupoUsuarioService.listarTodos());
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<GrupoUsuarioOptionDTO> options = grupoUsuarioService.listarOptions();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", options
        ));
    }
}
