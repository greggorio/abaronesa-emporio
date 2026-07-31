package com.baronesa.emporio.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import com.baronesa.emporio.service.ClienteRefSyncService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UsuarioRepository usuarioRepository;
    private final ClienteRefSyncService clienteRefSyncService;

    /** Base URL do ERP */
    @Value("${erp.app.base-url:http://localhost:8084}")
    private String erpBaseUrl;

    /** Base URL do E-commerce */
    @Value("${ecommerce.app.base-url:http://localhost:8081}")
    private String ecommerceBaseUrl;

    /** Base URL do Android */
    @Value("${android.app.base-url:http://localhost:9000}")
    private String androidBaseUrl;

    /** Custom URL Scheme do Android (para deep links) */
    @Value("${android.app.custom-scheme:capacitor://localhost}")
    private String androidCustomScheme;

    /** Base URL do Villa (site) */
    @Value("${website.app.base-url:http://localhost:5173}")
    private String villaBaseUrl;

    private final ObjectMapper mapper = new ObjectMapper(); // mantido caso precise para debug

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        try {
            log.info("========================================");
            log.info("=== OAuth2 Success Handler INICIADO ===");
            log.info("========================================");

            // Debug: Configurações carregadas
            log.info("=== CONFIGURAÇÕES CARREGADAS ===");
            log.info("ERP Base URL: {}", erpBaseUrl);
            log.info("E-commerce Base URL: {}", ecommerceBaseUrl);
            log.info("Android Base URL: {}", androidBaseUrl);
            log.info("Android Custom Scheme: {}", androidCustomScheme);
            log.info("Villa Base URL: {}", villaBaseUrl);

            // Debug: Informações da requisição
            log.info("=== INFORMAÇÕES DA REQUISIÇÃO ===");
            log.info("Request URL: {}", request.getRequestURL());
            log.info("Query String: {}", request.getQueryString());
            log.info("Method: {}", request.getMethod());
            log.info("Remote Address: {}", request.getRemoteAddr());
            log.info("Session ID: {}", request.getSession().getId());

            // Debug: Headers da requisição
            log.info("=== HEADERS DA REQUISIÇÃO ===");
            request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
                String headerValue = request.getHeader(headerName);
                log.info("Header '{}': {}", headerName, headerValue);
            });

            // Debug: Parâmetros da requisição
            log.info("=== PARÂMETROS DA REQUISIÇÃO ===");
            Map<String, String[]> parameterMap = request.getParameterMap();
            if (parameterMap.isEmpty()) {
                log.info("Nenhum parâmetro encontrado");
            } else {
                parameterMap.forEach((key, values) -> {
                    log.info("Parameter '{}': {}", key, Arrays.toString(values));
                });
            }

            // Debug: Atributos da sessão
            log.info("=== ATRIBUTOS DA SESSÃO ===");
            if (request.getSession() != null) {
                request.getSession().getAttributeNames().asIterator().forEachRemaining(attrName -> {
                    Object attrValue = request.getSession().getAttribute(attrName);
                    log.info("Session attribute '{}': {}", attrName, attrValue);
                });
            } else {
                log.warn("Sessão é NULL!");
            }

            // Debug: Cookies
            log.info("=== COOKIES DA REQUISIÇÃO ===");
            if (request.getCookies() != null && request.getCookies().length > 0) {
                for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                    log.info("Cookie '{}': {} (Domain: {}, Path: {}, MaxAge: {}, Secure: {}, HttpOnly: {})",
                            cookie.getName(), cookie.getValue(), cookie.getDomain(),
                            cookie.getPath(), cookie.getMaxAge(), cookie.getSecure(), cookie.isHttpOnly());
                }
            } else {
                log.info("Nenhum cookie encontrado");
            }

            // Debug: Dados do usuário OAuth2
            log.info("=== DADOS DO USUÁRIO OAUTH2 ===");
            OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();
            String email = oauthUser.getAttribute("email");
            String name = oauthUser.getAttribute("name");
            String picture = oauthUser.getAttribute("picture");
            log.info("Email: {}", email);
            log.info("Name: {}", name);
            log.info("Picture: {}", picture);
            log.info("All attributes: {}", oauthUser.getAttributes());

            // Debug: Roles/Authorities
            log.info("=== ROLES/AUTHORITIES ===");
            Set<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
            log.info("Roles encontradas: {}", roles);

            // Gerar JWT (sempre com claim userId)
            log.info("=== GERAÇÃO DO TOKEN JWT ===");
            var usuario = usuarioRepository.findByEmail(email)
                    .orElseThrow(() -> new IllegalStateException("Usuário OAuth2 não encontrado para email: " + email));
            String token = jwtTokenProvider.generateToken(email, roles, usuario.getId());
            log.info("Token JWT gerado com sucesso (primeiros 50 chars): {}",
                    token != null ? token.substring(0, Math.min(50, token.length())) + "..." : "NULL");

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

            // Extrair origem (state customizado)
            log.info("=== DETECÇÃO DE ORIGEM ===");
            String origem = extractStateParameter(request);
            log.info("Origem final detectada: '{}'", origem);

            // Construir URL de redirecionamento baseado na origem
            log.info("=== CONSTRUÇÃO DA URL DE REDIRECIONAMENTO ===");
            String targetUrl = determineTargetUrl(token, origem);
            log.info("URL de redirecionamento construída: '{}'", targetUrl);

            log.info("=== RESULTADO FINAL ===");
            log.info("OAuth2 login SUCESSO para '{}' (origem: '{}'), redirecionando para '{}'",
                    email, origem, targetUrl);

            // Limpar atributos de autenticação e redirecionar
            clearAuthenticationAttributes(request);
            log.info("Atributos de autenticação limpos, iniciando redirecionamento...");

            getRedirectStrategy().sendRedirect(request, response, targetUrl);
            log.info("Redirecionamento enviado com sucesso!");

            log.info("========================================");
            log.info("=== OAuth2 Success Handler CONCLUÍDO ===");
            log.info("========================================");

        } catch (Exception ex) {
            log.error("=== ERRO NO OAuth2SuccessHandler ===");
            log.error("Erro: {}", ex.getMessage(), ex);
            log.error("Stacktrace completo:", ex);

            // Redirecionar para página de erro do e-commerce por padrão
            String errorUrl = ecommerceBaseUrl
                    + "/login?oauth2_error=true&message="
                    + URLEncoder.encode("Erro na autenticação com Google: " + ex.getMessage(), StandardCharsets.UTF_8);

            log.error("Redirecionando para URL de erro: {}", errorUrl);
            getRedirectStrategy().sendRedirect(request, response, errorUrl);
        }
    }

    /**
     * Extrai o parâmetro state/origem da requisição OAuth2
     * PROBLEMA: O Spring Security usa o state para CSRF protection
     */
    private String extractStateParameter(HttpServletRequest request) {
        log.info("=== INICIANDO EXTRAÇÃO DE STATE/ORIGEM ===");

        // 1. Tentar pegar o state direto (provavelmente será o token CSRF)
        String state = request.getParameter("state");
        log.info("1. State parameter raw: '{}'", state);

        // 2. Verificar se temos algum atributo na sessão
        log.info("2. Verificando atributos da sessão...");
        if (request.getSession() != null) {
            Object sessionOrigin = request.getSession().getAttribute("oauth_origin");
            log.info("   Session oauth_origin: '{}'", sessionOrigin);
            if (sessionOrigin != null) {
                log.info("   ✅ ORIGEM ENCONTRADA NA SESSÃO: '{}'", sessionOrigin);
                return sessionOrigin.toString();
            }
        } else {
            log.warn("   ⚠️ Sessão é NULL!");
        }

        // 3. Verificar cookies
        log.info("3. Verificando cookies...");
        if (request.getCookies() != null) {
            boolean foundOAuthOriginCookie = false;
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                log.info("   Cookie analisado: '{}' = '{}'", cookie.getName(), cookie.getValue());
                if ("oauth_origin".equals(cookie.getName())) {
                    log.info("   ✅ ORIGEM ENCONTRADA NO COOKIE: '{}'", cookie.getValue());
                    foundOAuthOriginCookie = true;
                    return cookie.getValue();
                }
                if ("capacitor_app".equals(cookie.getName())) {
                    log.info("   📱 Cookie capacitor_app encontrado: '{}'", cookie.getValue());
                }
            }
            if (!foundOAuthOriginCookie) {
                log.info("   ❌ Cookie 'oauth_origin' NÃO encontrado");
            }
        } else {
            log.warn("   ⚠️ Nenhum cookie encontrado");
        }

        // 4. Verificar Referer header
        log.info("4. Verificando Referer header...");
        String referer = request.getHeader("Referer");
        log.info("   Referer header: '{}'", referer);
        if (referer != null) {
            if (referer.contains(":8081") || referer.contains("ecommerce")) {
                log.info("   ✅ ORIGEM E-COMMERCE detectada pelo referer");
                return "ecommerce";
            } else if (referer.contains(":8084") || referer.contains("erp")) {
                log.info("   ✅ ORIGEM ERP detectada pelo referer");
                return "erp";
            } else if (referer.contains(":9000") || referer.contains("android")) {
                log.info("   ✅ ORIGEM ANDROID detectada pelo referer");
                return "android";
            }
            log.info("   ❌ Referer não contém padrões conhecidos");
        } else {
            log.info("   ❌ Referer header não encontrado");
        }

        // 5. Verificar User-Agent para detectar Android/Capacitor
        log.info("5. Verificando User-Agent...");
        String userAgent = request.getHeader("User-Agent");
        log.info("   User-Agent: '{}'", userAgent);
        if (userAgent != null) {
            String lowerUserAgent = userAgent.toLowerCase();
            log.info("   User-Agent lowercase: '{}'", lowerUserAgent);

            boolean hasCapacitor = lowerUserAgent.contains("capacitor");
            boolean hasAndroid = lowerUserAgent.contains("android");
            boolean hasMobile = lowerUserAgent.contains("mobile");
            boolean hasSafari = lowerUserAgent.contains("safari");

            log.info("   Análise User-Agent: capacitor={}, android={}, mobile={}, safari={}",
                    hasCapacitor, hasAndroid, hasMobile, hasSafari);

            if (hasCapacitor || hasAndroid || (hasMobile && !hasSafari)) {
                log.info("   ✅ ORIGEM ANDROID/CAPACITOR detectada pelo User-Agent");
                return "android";
            }
            log.info("   ❌ User-Agent não indica Android/Capacitor");
        } else {
            log.info("   ❌ User-Agent header não encontrado");
        }

        // 6. Verificar header customizado do Capacitor
        log.info("6. Verificando header X-Capacitor-App...");
        String capacitorHeader = request.getHeader("X-Capacitor-App");
        log.info("   X-Capacitor-App header: '{}'", capacitorHeader);
        if ("true".equals(capacitorHeader)) {
            log.info("   ✅ ORIGEM CAPACITOR detectada pelo header customizado");
            return "android";
        } else {
            log.info("   ❌ Header X-Capacitor-App não é 'true'");
        }

        // 7. Verificar parâmetros da URL de callback
        log.info("7. Verificando parâmetros da URL de callback...");
        String requestUri = request.getRequestURI();
        String queryString = request.getQueryString();
        log.info("   Request URI: '{}'", requestUri);
        log.info("   Query String: '{}'", queryString);
        
        if (queryString != null && queryString.contains("android")) {
            log.info("   ✅ ORIGEM ANDROID detectada na query string");
            return "android";
        }

        // 8. Default - MUDADO PARA ECOMMERCE
        log.info("8. Usando origem padrão...");
        log.info("   ⚠️ USANDO ORIGEM PADRÃO: 'ecommerce'");
        return "ecommerce";
    }

    /**
     * Determina a URL de redirecionamento baseado na origem
     */
    private String determineTargetUrl(String token, String origem) {
        log.info("=== DETERMINANDO URL DE REDIRECIONAMENTO ===");
        log.info("Token (primeiros 20 chars): {}", token != null ? token.substring(0, Math.min(20, token.length())) + "..." :
                "NULL");
        log.info("Origem recebida: '{}'", origem);

        String baseUrl;
        String path;

        if ("ecommerce".equalsIgnoreCase(origem)) {
            baseUrl = ecommerceBaseUrl;
            path = "/oauth2/handler?token=" + token;
            log.info("✅ ROTA E-COMMERCE selecionada");
            log.info("   Base URL: '{}'", baseUrl);
            log.info("   Path: '{}'", path);
        } else if ("villa".equalsIgnoreCase(origem)) {
            baseUrl = villaBaseUrl;
            path = "/oauth2/handler?token=" + token;
            log.info("🍕 ROTA VILLA (SITE) selecionada");
            log.info("   Base URL: '{}'", baseUrl);
            log.info("   Path: '{}'", path);
        } else if ("android".equalsIgnoreCase(origem)) {
            baseUrl = androidCustomScheme;
            path = "/oauth2/callback?token=" + token;
            log.info("📱 ROTA ANDROID/CAPACITOR selecionada");
            log.info("   Base URL (custom scheme): '{}'", baseUrl);
            log.info("   Path: '{}'", path);
            log.info("   ⚠️ IMPORTANTE: Esta URL deve ser interceptada pelo app Capacitor!");
        } else {
            baseUrl = erpBaseUrl;
            path = "/#/oauth2/handler?token=" + token;
            log.info("🏢 ROTA ERP selecionada");
            log.info("   Base URL: '{}'", baseUrl);
            log.info("   Path: '{}'", path);
        }

        String finalUrl = baseUrl + path;
        log.info("URL FINAL construída: '{}'", finalUrl);

        // Validações adicionais
        if (finalUrl.length() > 2000) {
            log.warn("⚠️ URL muito longa ({} chars), pode causar problemas!", finalUrl.length());
        }

        if ("android".equalsIgnoreCase(origem)) {
            if (!finalUrl.startsWith("capacitor://")) {
                log.error("❌ ERRO: URL Android não usa esquema capacitor://! URL: '{}'", finalUrl);
            } else {
                log.info("✅ URL Android usa esquema capacitor:// corretamente");
            }
        }

        return finalUrl;
    }
}
