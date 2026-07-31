package com.baronesa.emporio.dto;

import com.baronesa.emporio.entity.Usuario;
import java.math.BigDecimal;
import java.util.Set;

public record UsuarioAdminUpdateRequest(
        String nome,
        String email,
        String telefone,
        Boolean ativo,
        Set<Usuario.Role> roles,
        Long grupoUsuario,
        String senha, // Opcional: se informada, atualiza a senha
        BigDecimal voucherVr
) {}