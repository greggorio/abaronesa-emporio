package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.RecompensaClienteDTO;
import com.baronesa.emporio.service.RecompensaClienteService;
import com.baronesa.emporio.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clientes/me/gamificacao")
@RequiredArgsConstructor
public class RecompensaClienteController {

    private final RecompensaClienteService recompensaClienteService;
    private final UsuarioService usuarioService;

    @GetMapping("/recompensas")
    public ResponseEntity<List<RecompensaClienteDTO>> getRecompensasDisponiveis() {
        String email = org.springframework.security.core.context.SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getName();
            
        Long clienteId = usuarioService.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("Usuário não encontrado"))
            .getId();
        
        List<RecompensaClienteDTO> recompensas = recompensaClienteService.getRecompensasDisponiveis(clienteId);
        return ResponseEntity.ok(recompensas);
    }
}