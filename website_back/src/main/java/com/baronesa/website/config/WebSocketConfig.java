package com.baronesa.website.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuração do WebSocket com STOMP para quiz em tempo real
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final String allowedOrigins;

    public WebSocketConfig(@Value("${app.websocket.allowed-origins}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilita um broker simples de mensagens em memória
        // /topic - para broadcast (todos os inscritos de uma sessão)
        // /queue - para mensagens diretas (jogador específico)
        config.enableSimpleBroker("/topic", "/queue");

        // Prefixo para destinos de aplicação (mensagens do cliente para servidor)
        config.setApplicationDestinationPrefixes("/app");

        // Prefixo para destinos de usuário (mensagens para usuários específicos)
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Registra o endpoint WebSocket
        // Acessível via: ws://localhost:8085/ws (Villa Custom) ou wss://villacustom.com.br/ws (produção)
        registry.addEndpoint("/ws")
                .setAllowedOrigins(CorsConfig.parseOrigins(allowedOrigins).toArray(String[]::new))
                .withSockJS(); // Adiciona fallback SockJS para navegadores sem suporte nativo
    }
}
