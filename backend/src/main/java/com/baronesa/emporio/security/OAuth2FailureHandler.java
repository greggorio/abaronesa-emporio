package com.baronesa.emporio.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Value("${erp.app.base-url:http://localhost:8084}")
    private String erpBaseUrl;

    @Value("${ecommerce.app.base-url:http://localhost:8081}")
    private String ecommerceBaseUrl;

    @Value("${website.app.base-url:http://localhost:5173}")
    private String villaBaseUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {

        log.error("OAuth2 login falhou: {}", exception.getMessage(), exception);
        log.error("Request URI: {}", request.getRequestURI());
        log.error("Query String: {}", request.getQueryString());

        // Detectar origem
        String origem = detectarOrigem(request);
        log.info("Origem OAuth2 detectada: '{}'", origem);

        // Construir URL de redirecionamento para o frontend
        String errorMessage = exception.getMessage();
        if (errorMessage == null || errorMessage.isEmpty()) {
            errorMessage = "Erro na autenticação OAuth2";
        }

        String baseUrl;
        if ("erp".equalsIgnoreCase(origem)) {
            baseUrl = erpBaseUrl;
        } else if ("villa".equalsIgnoreCase(origem)) {
            baseUrl = villaBaseUrl;
        } else {
            baseUrl = ecommerceBaseUrl;
        }

        String errorUrl = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/#/oauth2/handler")
                .queryParam("error", "true")
                .queryParam("message", URLEncoder.encode(errorMessage, StandardCharsets.UTF_8))
                .build()
                .toUriString();

        log.info("Redirecionando para URL de erro: {}", errorUrl);
        getRedirectStrategy().sendRedirect(request, response, errorUrl);
    }

    private String detectarOrigem(HttpServletRequest request) {
        // Verificar sessão
        if (request.getSession() != null) {
            Object sessionOrigin = request.getSession().getAttribute("oauth_origin");
            if (sessionOrigin != null) {
                return sessionOrigin.toString();
            }
        }

        // Verificar cookies
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("oauth_origin".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // Verificar referer
        String referer = request.getHeader("Referer");
        if (referer != null) {
            if (referer.contains(erpBaseUrl) || referer.contains("erp")) {
                return "erp";
            } else if (referer.contains(villaBaseUrl) || referer.contains("villa")) {
                return "villa";
            } else if (referer.contains(ecommerceBaseUrl) || referer.contains("ecommerce")) {
                return "ecommerce";
            }
        }

        // Default: ecommerce
        return "ecommerce";
    }
}
