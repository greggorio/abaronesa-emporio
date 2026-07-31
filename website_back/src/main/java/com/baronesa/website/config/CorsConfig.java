package com.baronesa.website.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);
        config.setAllowedOrigins(parseOrigins(allowedOrigins));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setMaxAge(3600L);

        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public CorsFilter corsFilter(UrlBasedCorsConfigurationSource source) {
        return new CorsFilter(source);
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
