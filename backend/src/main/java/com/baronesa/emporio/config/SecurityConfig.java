package com.baronesa.emporio.config;

import com.baronesa.emporio.security.JwtAuthenticationEntryPoint;
import com.baronesa.emporio.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/** Configuração de segurança - JWT com endpoints protegidos por padrão. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtEntryPoint;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService;
    private final AuthenticationSuccessHandler oAuth2SuccessHandler;
    private final AuthenticationFailureHandler oAuth2FailureHandler;

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(c -> c.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(jwtEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/release-control/identity/jwks"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/release-control/identity/token"
                        ).hasRole("SYSTEM")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/release-control/identity/deployer/jwks"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/release-control/identity/deployer/token"
                        ).hasRole("SYSTEM")
                        // URLs públicas OAuth2
                        .requestMatchers(
                                "/oauth2/**",
                                "/login/oauth2/**",
                                "/api/oauth2/**",
                                "/api/login/oauth2/**",
                                "/api/auth/**",
                                "/login/oauth2/code/**",
                                "/api/login/oauth2/code/**"
                        ).permitAll()
                        // Rotas públicas (cardápio/config) e documentação
                        .requestMatchers(
                                "/api/public/**",
                                "/api/website/themes/public/**",
                                "/api/events/**",
                                // Cardápio/Pedido em mesa (acesso livre via QR)
                                "/api/mesas/*/sessao",
                                "/api/mesas/*/convidados",
                                "/api/mesas/*/referencia",
                                // Pedidos públicos (mobile mesa)
                                "/api/pedidos/**",
                                // Conta e pagamentos/PIX acessados pelo app de mesa
                                "/api/conta/**",
                                "/api/pagamentos/intent",
                                "/api/pagamentos/webhook",
                                // Chamados e notificações dos convidados
                                "/api/chamados/**",
                                "/api/notificacoes/**",
                                // KDS (fila de preparo) - acesso aberto para monitores
                                // Delivery (checkout público)
                                "/api/delivery/**",
                                // Pagamentos (checkout público)
                                "/api/payments/**",
                                "/api/v1/payments/**",
                                "/api/webhooks/mercadopago/**",
                                "/api/webhooks/pagseguro/**",
                                // Mercado Pago (checkout público)
                                "/api/v1/mercadopago/**",
                                // Uber webhooks
                                "/api/uber/webhooks/**",
                                "/api/integration/**",
                                // Harmonizações (recomendações de produto)
                                "/api/produtos/*/harmonizacoes/**",
                                "/media/**",
                                "/uploads/signage/**",
                                // WebSocket de impressão (liberado neste checkpoint)
                                "/ws/print",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/swagger-ui.html",
                        "/webjars/**",
                        "/swagger-resources/**",
                        "/configuration/**",
                        "/error",
                        "/actuator/health",
                        // Integração espresso: saldos de gamificação via API key
                                "/api/admin/gamificacao/saldos"
                        ).permitAll()
                        // Pareamento do Print Agent (claim outbound)
                        .requestMatchers(
                                "/api/print-agent/pair/claim",
                                "/api/print-agent/status",
                                "/api/print-agent/reset"
                        ).permitAll()
                        // Rotas waiter/caixa para operação em mesas e catálogo
                        .requestMatchers(
                                "/api/admin/mesas/**",
                                "/api/admin/itens/**",
                                "/api/admin/cardapio/**",
                                "/api/admin/usuarios/options"
                        ).hasAnyRole("ADMIN", "SYSTEM", "WAITER", "CAIXA")
                        // Rotas administrativas
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SYSTEM")
                        // Demais rotas exigem autenticação
                        .anyRequest().authenticated()
                )
                /* ---- OAuth2 social login ---- */
                .oauth2Login(o -> o
                        .loginPage("/oauth2/authorization/google")
                        .userInfoEndpoint(u -> u.userService(oAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        // JWT filter antes do filtro padrão de autenticação
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(parseOrigins(allowedOrigins));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("Authorization", "Content-Disposition", "X-Total-Count"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    static List<String> parseOrigins(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalStateException("CORS sem origens configuradas");
        List<String> origins = Arrays.stream(raw.split(",", -1)).map(String::trim).toList();
        if (origins.stream().anyMatch(String::isBlank) || origins.stream().distinct().count() != origins.size()) {
            throw new IllegalStateException("CORS contem origem vazia ou duplicada");
        }
        for (String origin : origins) {
            URI uri;
            try { uri = URI.create(origin); } catch (RuntimeException e) {
                throw new IllegalStateException("CORS contem origem invalida");
            }
            String scheme = uri.getScheme();
            if (scheme == null || !List.of("http", "https", "capacitor", "ionic").contains(scheme.toLowerCase())
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || (uri.getPath() != null && !uri.getPath().isEmpty())
                    || "*".equals(uri.getHost())) {
                throw new IllegalStateException("CORS contem origem invalida");
            }
        }
        return origins;
    }
}
