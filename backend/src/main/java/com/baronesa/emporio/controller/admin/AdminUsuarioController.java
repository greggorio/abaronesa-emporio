package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.UsuarioOptionDTO;
import com.baronesa.emporio.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM', 'WAITER', 'CAIXA')")
public class AdminUsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarOptions() {
        List<UsuarioOptionDTO> options = usuarioService.listarUsuariosAtivosExcetoSystem();
        return ResponseEntity.ok(Map.of("data", options));
    }
}
