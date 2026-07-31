package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.UsuarioOptionDTO;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<Usuario> findById(Long id) {
        return usuarioRepository.findById(id);
    }

    public Optional<Usuario> findByEmail(String email) {
        return usuarioRepository.findByEmail(email);
    }

    public Optional<Usuario> findByTelefone(String telefone) {
        return usuarioRepository.findByTelefone(telefone);
    }

    public boolean existsByEmail(String email) {
        return usuarioRepository.existsByEmail(email);
    }

    public Usuario salvar(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario cadastrarUsuarioComum(Usuario usuario, String senhaPura) {
        usuario.setSenha(passwordEncoder.encode(senhaPura));
        usuario.setAtivo(true);
        usuario.setEmailVerificado(false);
        usuario.setRoles(Collections.singleton(Usuario.Role.CLIENTE));
        usuario.setCriadoEm(LocalDateTime.now());
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario cadastrarUsuarioOAuth2(String nome, String email, String providerId, Usuario.AuthProvider provider) {
        Usuario usuario = Usuario.builder()
                .nome(nome)
                .email(email)
                .ativo(true)
                .emailVerificado(true)
                .provider(provider)
                .providerId(providerId)
                .roles(Collections.singleton(Usuario.Role.CLIENTE))
                .criadoEm(LocalDateTime.now())
                .build();
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public Usuario cadastrarUsuarioInterno(String nome, String email, String senhaPura, Set<Usuario.Role> roles) {
        Usuario usuario = Usuario.builder()
                .nome(nome)
                .email(email)
                .senha(passwordEncoder.encode(senhaPura))
                .ativo(true)
                .emailVerificado(true)
                .roles(roles)
                .criadoEm(LocalDateTime.now())
                .build();
        return usuarioRepository.save(usuario);
    }

    public Optional<Usuario> ativarUsuarioPorToken(String token) {
        Optional<Usuario> optUsuario = usuarioRepository.findByEmailVerificationToken(token);
        optUsuario.ifPresent(usuario -> {
            usuario.setEmailVerificado(true);
            usuario.setEmailVerificationToken(null);
            usuario.setEmailVerificationExpiresAt(null);
            usuarioRepository.save(usuario);
        });
        return optUsuario;
    }

    public Optional<Usuario> buscarPorTokenRedefinicaoSenha(String token) {
        return usuarioRepository.findByPasswordResetToken(token);
    }

    @Transactional
    public void atualizarSenha(Long usuarioId, String novaSenha) {
        usuarioRepository.findById(usuarioId).ifPresent(usuario -> {
            usuario.setSenha(passwordEncoder.encode(novaSenha));
            usuario.setPasswordResetToken(null);
            usuario.setPasswordResetExpiresAt(null);
            usuarioRepository.save(usuario);
        });
    }

    public List<Usuario> listarPorRole(Usuario.Role role) {
        return new ArrayList<>(usuarioRepository.findByRolesContaining(role));
    }

    public boolean isSystem(Usuario usuario) {
        return usuario.getRoles().contains(Usuario.Role.SYSTEM);
    }

    public boolean isAdmin(Usuario usuario) {
        return usuario.getRoles().contains(Usuario.Role.ADMIN);
    }

    public boolean isCliente(Usuario usuario) {
        return usuario.getRoles().contains(Usuario.Role.CLIENTE);
    }

    public boolean isAtendente(Usuario usuario) {
        return usuario.getRoles().contains(Usuario.Role.FUNCIONARIO);
    }

    public List<UsuarioOptionDTO> listarUsuariosAtivosExcetoSystem() {
        return usuarioRepository.findByAtivoTrueAndRolesNotContaining(Usuario.Role.SYSTEM).stream()
                .sorted(Comparator.comparing(u -> Optional.ofNullable(u.getNome()).orElse("").toLowerCase(Locale.ROOT)))
                .map(u -> new UsuarioOptionDTO(
                        u.getId(),
                        Optional.ofNullable(u.getNome()).orElse("(Sem nome)"),
                        u.getEmail()
                ))
                .toList();
    }
}
