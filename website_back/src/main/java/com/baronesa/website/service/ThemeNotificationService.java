package com.baronesa.website.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ThemeNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Notifica todos os clientes que estão escutando o tenant informado para
     * atualizar o tema ativo.
     */
    public void broadcastThemeChange(String tenantId) {
        String destination = "/topic/theme/" + tenantId + "/refresh";
        messagingTemplate.convertAndSend(destination, Map.of(
                "tenantId", tenantId
        ));
        log.info("[WS] Notificação de tema enviado para {}", destination);
    }
}
