package com.baronesa.emporio.dto.formbuilder;

import com.baronesa.emporio.entity.Usuario;
import jakarta.validation.constraints.*;
import java.util.Set;

public record UsuarioAdminFormRequest(
        @NotBlank(message = "Nome é obrigatório")
        @Size(min = 3, max = 100)
        String nome,

        @NotBlank(message = "Email é obrigatório")
        @Email(message = "Email inválido")
        String email,

        @Pattern(regexp = "^\\+?[0-9\\s\\-\\(\\)]+$", message = "Telefone inválido")
        String telefone,

        String senha, // Opcional - oculto na edição

        @NotNull(message = "Status é obrigatório")
        Boolean ativo,

        @NotEmpty(message = "Selecione pelo menos uma permissão")
        Set<Usuario.Role> roles,

        Long grupoUsuarioId,

        // Campos apenas para exibição (readonly no form)
        String criadoEm,
        String ultimoLogin,
        Boolean emailVerificado
) {
    // Método factory para criar a partir da entidade (para edição)
    public static UsuarioAdminFormRequest fromEntity(Usuario usuario) {
        return new UsuarioAdminFormRequest(
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                null, // Nunca retorna senha
                usuario.getAtivo(),
                usuario.getRoles(),
                usuario.getGrupoUsuario() != null ? usuario.getGrupoUsuario().getId() : null,
                usuario.getCriadoEm() != null ? usuario.getCriadoEm().toString() : null,
                usuario.getUltimoLogin() != null ? usuario.getUltimoLogin().toString() : null,
                usuario.getEmailVerificado()
        );
    }
}