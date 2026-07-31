package com.baronesa.emporio.dto.auth;

import java.util.Set;

/**
 * DTO com dados completos do usuário autenticado
 * Retornado pelo endpoint /auth/me
 */
public record AuthUserResponse(
        Long id,
        String nome,
        String email,
        String fotoPerfil,
        Set<String> roles,
        Long grupoId,
        String grupoNome,
        boolean emailVerificado,
        String origemCadastro,
        boolean perfilCompleto
) {
    /**
     * Construtor simplificado para manter compatibilidade
     */
    public AuthUserResponse(Long id, String nome, String email, String fotoPerfil,
                            Set<String> roles, Long grupoId) {
        this(id, nome, email, fotoPerfil, roles, grupoId, null, true, null, false);
    }
}
