package com.baronesa.emporio.dto;

import com.baronesa.emporio.entity.Usuario;
import java.math.BigDecimal;
import java.util.Set;

public record UsuarioAdminRequest(
        String nome,
        String email,
        String telefone,
        String senha, // Opcional
        Boolean ativo,
        Set<Usuario.Role> roles,
        Long grupoUsuario,
        BigDecimal voucherVr
) {}