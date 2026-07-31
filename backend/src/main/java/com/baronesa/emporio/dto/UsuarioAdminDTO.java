package com.baronesa.emporio.dto;

import com.baronesa.emporio.entity.Usuario;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

public record UsuarioAdminDTO(
        Long id,
        String nome,
        String email,
        String telefone,
        Boolean ativo,
        Boolean emailVerificado,
        Set<Usuario.Role> roles,
        String rolesDisplay, // Para exibir as roles formatadas
        Long grupoUsuario,
        String grupoUsuarioNome,
        LocalDateTime criadoEm,
        LocalDateTime ultimoLogin,
        BigDecimal voucherVr
) {}