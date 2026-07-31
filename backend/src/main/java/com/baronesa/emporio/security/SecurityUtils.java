package com.baronesa.emporio.security;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final UsuarioRepository usuarioRepository;

    /**
     * Obtém o usuário atualmente autenticado
     * Utiliza UserPrincipal para obter o ID diretamente, evitando consulta desnecessária
     */
    public Usuario getUsuarioAtual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("Usuário não autenticado");
        }

        // Verificar se o principal é UserPrincipal (implementação que funciona do ContaPagarService)
        if (authentication.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return usuarioRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new IllegalStateException("Usuário logado não encontrado"));
        }

        // Fallback: tentar buscar por email (compatibilidade com autenticação antiga)
        String email = authentication.getName();
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Usuário não encontrado: " + email));
    }

    /**
     * Obtém o nome do usuário atual
     */
    public String getUsuarioAtualNome() {
        return getUsuarioAtual().getNome();
    }

    /**
     * Obtém o ID do usuário atual
     */
    public Long getUsuarioAtualId() {
        return getUsuarioAtual().getId();
    }
}
