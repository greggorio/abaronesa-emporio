package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.auth.*;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.exception.BusinessException;
import com.baronesa.emporio.exception.ResourceNotFoundException;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.security.JwtTokenProvider;
import com.baronesa.emporio.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ClienteRefSyncService clienteRefSyncService;

    @Value("${app.jwt.expiration:604800000}")
    private Long jwtExpirationMs;

    /**
     * Login tradicional com email e senha
     * IMPORTANTE: Por enquanto aceita login mesmo sem email verificado (simplificação temporária)
     */
    @Transactional
    public TokenResponse login(LoginRequest request) {
        log.info("Tentativa de login para: {}", request.email());

        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Credenciais inválidas"));

        // Verificar senha
        if (!passwordEncoder.matches(request.password(), usuario.getSenha())) {
            log.warn("Senha incorreta para usuário: {}", request.email());
            throw new BadCredentialsException("Credenciais inválidas");
        }

        // Verificar se está ativo
        if (!usuario.getAtivo()) {
            throw new BusinessException("Usuário inativo. Entre em contato com o administrador.");
        }

        // Atualizar último login
        usuario.setUltimoLogin(LocalDateTime.now());
        usuarioRepository.save(usuario);

        log.info("Login realizado com sucesso para: {}", usuario.getEmail());

        // Disparar sincronização de dados do cliente de forma assíncrona
        log.info("=== INICIANDO SINCRONIZAÇÃO DE DADOS DO CLIENTE ===");
        try {
            clienteRefSyncService.syncAsync(usuario)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("SYNC_CLIENTE_REF_FAIL userId={} error={}", usuario.getId(), throwable.getMessage());
                    } else {
                        log.info("SYNC_CLIENTE_REF_OK userId={}", usuario.getId());
                    }
                });
        } catch (Exception e) {
            log.error("Erro ao iniciar sincronização assíncrona do cliente", e);
        }

        // Gerar token
        return generateTokenResponse(usuario);
    }

    /**
     * Registro de novos usuários
     * IMPORTANTE: Por enquanto cria usuário já com email verificado (simplificação temporária)
     */
    @Transactional
    public AuthApiResponse register(RegisterRequest request) {
        log.info("Novo registro de usuário: {}", request.email());

        // Verificar se email já existe
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new BusinessException("Email já cadastrado");
        }

        // Criar novo usuário
        Set<Usuario.Role> roles = new HashSet<>();
        roles.add(Usuario.Role.CLIENTE);

        Usuario usuario = Usuario.builder()
                .nome(request.nome())
                .email(request.email())
                .senha(passwordEncoder.encode(request.password()))
                .telefone(request.telefone())
                .ativo(true)
                .emailVerificado(true) // TEMPORÁRIO: Sem fluxo de verificação por enquanto
                .roles(roles)
                .provider(Usuario.AuthProvider.LOCAL)
                .build();

        usuario = usuarioRepository.save(usuario);

        log.info("Usuário criado com sucesso - ID: {}", usuario.getId());

        // IMPORTANTE: Retornar resposta SEM token
        return AuthApiResponse.success(
                "Usuário registrado com sucesso!"
        );
    }

    /**
     * Obter usuário atual autenticado com dados completos
     */
    public AuthUserResponse getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() ||
                !(auth.getPrincipal() instanceof UserPrincipal)) {
            throw new BusinessException("Usuário não autenticado");
        }

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        Usuario usuario = usuarioRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Set<String> roles = usuario.getRoles().stream()
                .map(Enum::name)
                .collect(Collectors.toSet());

        return new AuthUserResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getFotoPerfil(),
                roles,
                usuario.getGrupoUsuario() != null ? usuario.getGrupoUsuario().getId() : null
        );
    }

    /**
     * Renovar token JWT
     */
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        if (!jwtTokenProvider.validateToken(request.refreshToken())) {
            throw new BadCredentialsException("Refresh token inválido");
        }

        String email = jwtTokenProvider.parse(request.refreshToken()).getSubject();
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        return generateTokenResponse(usuario);
    }

    // Métodos auxiliares privados

    private TokenResponse generateTokenResponse(Usuario usuario) {
        Set<String> roles = usuario.getRoles().stream()
                .map(role -> "ROLE_" + role.name())
                .collect(Collectors.toSet());

        String accessToken = jwtTokenProvider.generateToken(usuario.getEmail(), roles, usuario.getId());
        String refreshToken = accessToken; // Por simplicidade, usando o mesmo

        return TokenResponse.of(accessToken, refreshToken, jwtExpirationMs);
    }
}
