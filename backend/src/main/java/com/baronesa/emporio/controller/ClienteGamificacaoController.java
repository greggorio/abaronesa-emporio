package com.baronesa.emporio.controller;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.service.GamificacaoConsultaService;
import com.baronesa.emporio.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/clientes/me")
@RequiredArgsConstructor
public class ClienteGamificacaoController {

    private final GamificacaoConsultaService gamificacaoConsultaService;
    private final UsuarioRepository usuarioRepository;

    @GetMapping("/gamificacao")
    public ResponseEntity<Map<String, Object>> minhaGamificacao() {
        Usuario usuario = obterUsuarioLogado();
        Map<String, Object> resposta = gamificacaoConsultaService.buscarMinhaGamificacao(usuario);
        return ResponseEntity.ok(resposta);
    }

    private Usuario obterUsuarioLogado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("Usuário logado não encontrado"));
    }
}
