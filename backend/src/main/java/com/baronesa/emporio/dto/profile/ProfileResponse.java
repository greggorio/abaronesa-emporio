package com.baronesa.emporio.dto.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.baronesa.emporio.entity.Usuario;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Set;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProfileResponse(
        Long id,
        String nome,
        String email,
        String telefone,
        String fotoPerfil,
        Boolean ativo,
        Boolean emailVerificado,
        Set<Usuario.Role> roles,
        Long grupoId,
        LocalDateTime criadoEm,
        LocalDateTime ultimoLogin
) {}
