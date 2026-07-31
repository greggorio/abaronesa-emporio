package com.baronesa.emporio.security;

import com.baronesa.emporio.entity.Usuario;
import com.baronesa.emporio.repository.UsuarioRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Pattern COMPACT_JWT_PATTERN = Pattern.compile("^[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+$");

    private final JwtTokenProvider tokenProvider;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        final String requestPath = request.getRequestURI();
        log.debug("JwtFilter - Processing request: {}", requestPath);

        if (shouldSkipAuthentication(requestPath)) {
            log.trace("JwtFilter - Skipping authentication for: {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        String token = getJwtFromRequest(request);

        if (!StringUtils.hasText(token)) {
            log.debug("JwtFilter - No Authorization header (Bearer ...) found for {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        // Validação
        boolean valido = tokenProvider.validateToken(token);
        if (!valido) {
            log.debug("JwtFilter - Invalid JWT for {}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        // Token válido → extrair claims
        Claims claims = tokenProvider.parse(token);
        String email = claims.getSubject();

        if (!StringUtils.hasText(email)) {
            log.warn("JwtFilter - Token sem subject (email) válido. URI={}", requestPath);
            filterChain.doFilter(request, response);
            return;
        }

        // Carregar usuário
        try {
            Usuario usuario = usuarioRepository.findByEmail(email)
                    .orElse(null);

            if (usuario == null) {
                log.warn("JwtFilter - Usuário não encontrado para email={} (URI={})", email, requestPath);
                filterChain.doFilter(request, response);
                return;
            }

            UserPrincipal userPrincipal = UserPrincipal.create(usuario);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userPrincipal,
                            null,
                            userPrincipal.getAuthorities()
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.trace("JwtFilter - Autenticado email={} para {}", email, requestPath);

        } catch (Exception ex) {
            log.error("JwtFilter - Erro ao autenticar token para email={} (URI={}): {}", email, requestPath, ex.getMessage(), ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String jwt = bearerToken.substring(7).trim();
            if (COMPACT_JWT_PATTERN.matcher(jwt).matches()) {
                return jwt;
            }
            log.trace("JwtFilter - Authorization header present but token is not a compact JWT");
        }
        return null;
    }

    private boolean shouldSkipAuthentication(String path) {
        return path.contains("/swagger-ui") ||
                path.contains("/v3/api-docs") ||
                path.contains("/swagger-resources") ||
                path.contains("/webjars") ||
                path.contains("/configuration") ||
                path.equals("/swagger-ui.html") ||
                path.equals("/error");
    }
}
