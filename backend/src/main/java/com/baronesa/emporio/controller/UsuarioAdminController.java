package com.baronesa.emporio.controller;

import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.controller.base.BaseListController;
import com.baronesa.emporio.controller.base.FormConfigurableController;
import com.baronesa.emporio.dto.UsuarioAdminDTO;
import com.baronesa.emporio.dto.UsuarioAdminRequest;
import com.baronesa.emporio.dto.UsuarioAdminUpdateRequest;
import com.baronesa.emporio.service.FormConfigService;
import com.baronesa.emporio.service.UsuarioAdminService;
import com.baronesa.emporio.service.base.BaseListService;
import com.baronesa.emporio.service.list.UsuarioAdminListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/usuarios-admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
public class UsuarioAdminController extends BaseListController<UsuarioAdminListService>
        implements FormConfigurableController {

    private final UsuarioAdminListService listService;
    private final UsuarioAdminService usuarioAdminService;
    private final MessageResolver messageResolver;
    private final FormConfigService formConfigService; // NOVO - Substituiu HybridFormConfigRegistry

    @Override
    protected UsuarioAdminListService getService() {
        return listService;
    }

    @Override
    protected MessageResolver getMessageResolver() {
        return messageResolver;
    }

    // Implementação da interface FormConfigurableController
    @Override
    public String getEntityType() {
        return "usuarios-admin";
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

    // CRUD básico
    @PostMapping
    public ResponseEntity<Map<String, Object>> criar(@RequestBody UsuarioAdminRequest request) {
        try {
            usuarioAdminService.criar(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", messageResolver.getMessage("usuario.admin.success.created")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioAdminDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioAdminService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> editar(@PathVariable Long id,
                                                      @RequestBody UsuarioAdminUpdateRequest request) {
        try {
            usuarioAdminService.editar(id, request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", messageResolver.getMessage("usuario.admin.success.updated")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Long id) {
        usuarioAdminService.deletar(id);
        return ResponseEntity.noContent().build();
    }

    // Ação especial
    @PostMapping("/{id}/reset-senha")
    public ResponseEntity<Map<String, Object>> resetarSenha(@PathVariable Long id) {
        try {
            usuarioAdminService.resetarSenha(id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", messageResolver.getMessage("usuario.admin.success.password-reset")
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}