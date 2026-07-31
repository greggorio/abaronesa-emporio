package com.baronesa.emporio.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baronesa.emporio.dto.UserDTO;
import com.baronesa.emporio.repository.UsuarioRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/usuario")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "Endpoints para listagem de usuários")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;

    @GetMapping
    @Operation(summary = "Listar todos os usuários ativos")
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        List<UserDTO> usuarios = usuarioRepository.findAll()
                .stream()
                .filter(u -> u.getAtivo() != null && u.getAtivo())
                .map(u -> {
                    UserDTO dto = new UserDTO();
                    dto.setId(u.getId());
                    dto.setNome(u.getNome());
                    return dto;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("objeto", usuarios);

        return ResponseEntity.ok(response);
    }
}
