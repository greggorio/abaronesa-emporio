package com.baronesa.emporio.security;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.security.oauth2.OAuth2UserInfo;
import com.baronesa.emporio.security.oauth2.OAuth2UserInfoFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UsuarioRepository usuarioRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest request)
            throws OAuth2AuthenticationException {

        OAuth2User oauth = super.loadUser(request);

        try {
            return processOAuth2User(request, oauth);
        } catch (OAuth2AuthenticationException ex) {
            throw ex; // relança para o handler capturar
        } catch (Exception ex) {
            throw new InternalAuthenticationServiceException(ex.getMessage(), ex);
        }
    }

    private OAuth2User processOAuth2User(OAuth2UserRequest req, OAuth2User oauth) {
        String registrationId = req.getClientRegistration().getRegistrationId();

        OAuth2UserInfo info = OAuth2UserInfoFactory
                .getOAuth2UserInfo(registrationId, oauth.getAttributes());

        if (!StringUtils.hasText(info.getEmail())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_required",
                            "Email é obrigatório para autenticação", null));
        }

        // Detectar origem da requisição OAuth2
        String origem = detectarOrigem();
        log.info("Origem OAuth2 detectada: '{}' para email: {}", origem, info.getEmail());

        Optional<Usuario> opt = usuarioRepository.findByEmail(info.getEmail());
        Usuario usuario;

        // Validação específica para ERP: bloquear se usuário não existir
        if ("erp".equals(origem) && !opt.isPresent()) {
            log.warn("❌ Tentativa de login OAuth2 no ERP para usuário não cadastrado: {}", info.getEmail());
            throw new OAuth2AuthenticationException(
                new OAuth2Error("usuario_nao_cadastrado_erp",
                    "Este e-mail não possui cadastro no sistema ERP. " +
                    "Entre em contato com o administrador para criar seu usuário.", null));
        }

        if (opt.isPresent()) {
            usuario = opt.get();

            Usuario.AuthProvider current = Usuario.AuthProvider
                    .valueOf(registrationId.toUpperCase());

            // Permitir upgrade de LOCAL para OAuth2 (Google, etc.)
            if (usuario.getProvider() == Usuario.AuthProvider.LOCAL) {
                log.info("🔄 Convertendo usuário LOCAL para OAuth2: {} (provider: {} → {})",
                         usuario.getEmail(), usuario.getProvider(), current);
                usuario.setProvider(current);
                usuario = updateExistingUser(usuario, info);
            } else if (!usuario.getProvider().equals(current)) {
                // Bloquear apenas se tentar trocar entre provedores OAuth2 diferentes
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("different_provider",
                                "Use sua conta " + usuario.getProvider()
                                        + " para login.", null));
            } else {
                // Mesmo provider, apenas atualizar dados
                usuario = updateExistingUser(usuario, info);
            }
        } else {
            usuario = registerNewUser(req, info);
        }

        return UserPrincipal.create(usuario, oauth.getAttributes());
    }

    /**
     * Detecta a origem da requisição OAuth2 (erp vs site/ecommerce)
     */
    private String detectarOrigem() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

            if (attributes != null) {
                var request = attributes.getRequest();
                var session = request.getSession(false);

                // Verificar sessão (definida no OAuth2ProxyController)
                if (session != null) {
                    Object sessionOrigin = session.getAttribute("oauth_origin");
                    if (sessionOrigin != null) {
                        String origem = sessionOrigin.toString();
                        log.debug("Origem detectada via sessão: '{}'", origem);
                        return origem;
                    }
                }

                // Fallback: verificar cookies
                if (request.getCookies() != null) {
                    for (var cookie : request.getCookies()) {
                        if ("oauth_origin".equals(cookie.getName())) {
                            String origem = cookie.getValue();
                            log.debug("Origem detectada via cookie: '{}'", origem);
                            return origem;
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.warn("Erro ao detectar origem OAuth2: {}", e.getMessage());
        }

        // Default: assumir que é site/ecommerce (comportamento seguro)
        return "site";
    }

    private Usuario registerNewUser(OAuth2UserRequest req, OAuth2UserInfo info) {
        Usuario.AuthProvider provider = Usuario.AuthProvider
                .valueOf(req.getClientRegistration().getRegistrationId().toUpperCase());

        // IMPORTANTE: Usar HashSet mutável ao invés de Set.of()
        Set<Usuario.Role> roles = new HashSet<>();
        roles.add(Usuario.Role.CLIENTE);

        Usuario usuario = Usuario.builder()
                .nome(info.getName())
                .email(info.getEmail())
                .fotoPerfil(info.getImageUrl())
                .provider(provider)
                .providerId(info.getId())
                .emailVerificado(true)
                .ativo(true)
                .roles(roles)
                .ultimoLogin(LocalDateTime.now())
                .build();

        usuario = usuarioRepository.save(usuario);
        log.info("✅ Novo usuário OAuth2 criado: {} (provider: {})", usuario.getEmail(), provider);

        return usuario;
    }

    private Usuario updateExistingUser(Usuario user, OAuth2UserInfo info) {
        user.setNome(info.getName());
        user.setFotoPerfil(info.getImageUrl());
        user.setProviderId(info.getId());
        user.setUltimoLogin(LocalDateTime.now());

        user = usuarioRepository.save(user);
        log.info("✅ Usuário OAuth2 atualizado: {}", user.getEmail());

        return user;
    }
}
