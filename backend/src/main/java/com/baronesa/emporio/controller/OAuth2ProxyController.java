package com.baronesa.emporio.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/auth/oauth2")
public class OAuth2ProxyController {

    @GetMapping("/google/{origin}")
    public void googleLogin(
            @PathVariable String origin,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,
            @RequestHeader(value = "X-Capacitor-App", required = false) String capacitorHeader,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session) throws IOException {
        log.info("=== OAUTH2 PROXY DEBUG ===");
        log.info("Origin: {}, User-Agent: {}", origin, userAgent);
        log.info("Redirecionando para: /oauth2/authorization/google");
        log.info("OAuth2 proxy login - Origin: {}, User-Agent: {}, Capacitor: {}", 
                origin, userAgent, capacitorHeader);

        // Salvar origem na sessão
        session.setAttribute("oauth_origin", origin);
        
        // Também salvar em cookie para maior confiabilidade
        Cookie originCookie = new Cookie("oauth_origin", origin);
        originCookie.setPath("/");
        originCookie.setMaxAge(600); // 10 minutos (aumentado para maior segurança)
        originCookie.setHttpOnly(false); // Permitir acesso via JavaScript para debugging
        originCookie.setSecure(false); // Para desenvolvimento local
        response.addCookie(originCookie);
        
        // Se for mobile/capacitor, adicionar header indicativo
        if ("android".equalsIgnoreCase(origin) || "true".equals(capacitorHeader)) {
            Cookie capacitorCookie = new Cookie("capacitor_app", "true");
            capacitorCookie.setPath("/");
            capacitorCookie.setMaxAge(600); // 10 minutos (aumentado)
            capacitorCookie.setHttpOnly(false); // Permitir acesso via JavaScript
            capacitorCookie.setSecure(false); // Para desenvolvimento local
            response.addCookie(capacitorCookie);
        }

        // Redirecionar para o OAuth2 real
        response.sendRedirect("/oauth2/authorization/google");
    }
    
    /**
     * Endpoint alternativo para mobile que retorna o URL de autenticação
     * ao invés de redirecionar (útil para InAppBrowser)
     */
    @GetMapping("/google/url/{origin}")
    @ResponseBody
    public AuthUrlResponse getGoogleAuthUrl(
            @PathVariable String origin,
            HttpServletRequest request,
            HttpSession session) {
        
        // Salvar origem na sessão
        session.setAttribute("oauth_origin", origin);
        
        // Construir URL completo
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        String contextPath = request.getContextPath();
        
        String authUrl = scheme + "://" + serverName;
        if ((scheme.equals("http") && serverPort != 80) || 
            (scheme.equals("https") && serverPort != 443)) {
            authUrl += ":" + serverPort;
        }
        authUrl += contextPath + "/oauth2/authorization/google";
        
        log.info("Retornando URL de autenticação para mobile: {}", authUrl);
        
        return new AuthUrlResponse(authUrl);
    }
    
    /**
     * Response DTO para o URL de autenticação
     */
    public static class AuthUrlResponse {
        private final String authUrl;
        
        public AuthUrlResponse(String authUrl) {
            this.authUrl = authUrl;
        }
        
        public String getAuthUrl() {
            return authUrl;
        }
    }
}