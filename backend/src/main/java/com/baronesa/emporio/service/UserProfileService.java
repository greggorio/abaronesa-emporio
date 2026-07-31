package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.profile.ChangePasswordRequest;
import com.baronesa.emporio.dto.profile.ProfileResponse;
import com.baronesa.emporio.dto.profile.UpdateProfileRequest;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.exception.BusinessException;
import com.baronesa.emporio.exception.ResourceNotFoundException;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${bares.upload.base-url:http://localhost:8080/}")
    private String avatarBaseUrl;

    /**
     * Obter perfil do usuário atual
     */
    public ProfileResponse getCurrentProfile() {
        Usuario usuario = getCurrentUser();
        return mapToProfileResponse(usuario);
    }

    /**
     * Atualizar perfil do usuário atual
     */
    @Transactional
    public ProfileResponse updateProfile(UpdateProfileRequest request) {
        Usuario usuario = getCurrentUser();

        log.info("Atualizando perfil do usuário: {}", usuario.getEmail());

        // Atualizar dados básicos
        usuario.setNome(request.nome());
        usuario.setTelefone(request.telefone());

        usuario = usuarioRepository.save(usuario);
        log.info("Perfil salvo com sucesso para usuário ID: {}", usuario.getId());

        return mapToProfileResponse(usuario);
    }

    /**
     * Alterar senha do usuário atual
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Usuario usuario = getCurrentUser();

        // Verificar senha atual
        if (!passwordEncoder.matches(request.currentPassword(), usuario.getSenha())) {
            throw new BusinessException("Senha atual incorreta");
        }

        // Validar se nova senha é diferente
        if (request.currentPassword().equals(request.newPassword())) {
            throw new BusinessException("A nova senha deve ser diferente da atual");
        }

        // Atualizar senha
        usuario.setSenha(passwordEncoder.encode(request.newPassword()));
        usuarioRepository.save(usuario);

        log.info("Senha alterada com sucesso para usuário: {}", usuario.getEmail());
    }

    // Métodos auxiliares

    private Usuario getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() ||
                !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new BusinessException("Usuário não autenticado");
        }

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        return usuarioRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
    }

    private ProfileResponse mapToProfileResponse(Usuario usuario) {
        String fotoPerfilUrl = null;
        if (usuario.getFotoPerfil() != null && !usuario.getFotoPerfil().isEmpty()) {
            // Verificar se já é uma URL completa (Google, Facebook, etc.)
            if (usuario.getFotoPerfil().startsWith("http://") ||
                    usuario.getFotoPerfil().startsWith("https://")) {
                // Manter URL externa como está
                fotoPerfilUrl = usuario.getFotoPerfil();
            } else {
                // Construir URL completa para arquivos locais
                fotoPerfilUrl = avatarBaseUrl + "media/avatars/" + usuario.getFotoPerfil();
            }
        }

        return ProfileResponse.builder()
                .id(usuario.getId())
                .nome(usuario.getNome())
                .email(usuario.getEmail())
                .telefone(usuario.getTelefone())
                .fotoPerfil(fotoPerfilUrl)
                .ativo(usuario.getAtivo())
                .emailVerificado(usuario.getEmailVerificado())
                .roles(usuario.getRoles())
                .grupoId(usuario.getGrupoUsuario() != null ? usuario.getGrupoUsuario().getId() : null)
                .criadoEm(usuario.getCriadoEm())
                .ultimoLogin(usuario.getUltimoLogin())
                .build();
    }
}
