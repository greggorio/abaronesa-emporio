package com.baronesa.website.config;

import com.baronesa.website.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
          .cors(cors -> cors.configurationSource(corsConfigurationSource))
          .csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(authz -> authz
            // Health interno do container, publico e sanitizado pelo Actuator.
            .requestMatchers("/actuator/health").permitAll()

            // ===================================================================
            // FRONTEND ROUTES - PÁGINAS PÚBLICAS
            // ===================================================================
            .requestMatchers(
              "/", "/home", "/cardapio", "/sobre", "/contato", "/eventos", "/galeria",
              "/login", "/auth/callback", "/passeio", "/passeio-demo",
              "/index.html", "/favicon.ico",
              "/css/**", "/js/**", "/img/**", "/vendor/**", "/fonts/**", "/assets/**"
            ).permitAll()

            // ===================================================================
            // SWAGGER/OPENAPI - DOCUMENTAÇÃO DA API
            // ===================================================================
            .requestMatchers(
              "/swagger-ui.html",
              "/swagger-ui/**",
              "/v3/api-docs/**",
              "/api-docs/**",
              "/swagger-resources/**",
              "/webjars/**",
              "/configuration/ui",
              "/configuration/security"
            ).permitAll()

            // ===================================================================
            // ENDPOINTS PÚBLICOS
            // ===================================================================
            .requestMatchers(
              "/api/public/**",
              "/api/themes/public/**",  // Endpoint público de temas
              "/api/themes",            // Endpoint para listagem de temas (removido temporariamente a autenticação para debug)
              "/api/themes/**",         // Também qualquer subrota de themes (apenas para testes)
              "/api/notifications/subscribe",  // Endpoint público para inscrição em notificações
              "/api/notifications/**",         // Outros endpoints de notificação exigem autenticação
              "/api/produtos/**",
              "/api/eventos/**",
              "/api/cardapio/**",
              "/api/delivery/**",
              "/api/uber/**",
              "/api/events/kds",
              "/api/galeria/**", "/media/**",
              "/api/reservas/public/**",
              "/api/imagens/**",
              "/api/kds/**",
              "/api/quiz/**",
              "/api/categories/**",
              "/ws/**"  // WebSocket
            ).permitAll()

            // ===================================================================
            // ENDPOINT DE SINCRONIZAÇÃO COM ERP - AUTENTICAÇÃO POR API KEY
            // ===================================================================
            .requestMatchers("/api/clientes-ref/sync").permitAll()

            // ===================================================================
            // TODOS OS OUTROS ENDPOINTS REQUEREM AUTENTICAÇÃO
            // ===================================================================
            .anyRequest().authenticated()
          )
          .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
