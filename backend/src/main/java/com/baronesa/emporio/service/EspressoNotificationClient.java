package com.baronesa.emporio.service;

import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EspressoNotificationClient {

    private final ConfigManager configManager;
    private final RestClient restClient = RestClient.create();

    /**
     * Envia notificação para um usuário específico via espresso_back
     * 
     * @param userId ID do usuário
     * @param title Título da notificação
     * @param body Corpo da notificação
     * @param imageUrl URL da imagem (pode ser nulo)
     * @param deeplink Deep link para onde a notificação deve direcionar
     * @param source Origem da notificação (ex: BIRTHDAY)
     * @param payloadJson Payload adicional em formato JSON
     */
    public boolean sendNotificationToUser(Long userId, String title, String body, String imageUrl,
                                          String deeplink, String source, String payloadJson) {
        try {
            String baseUrl = configManager.getConfig("espresso.sync.base-url", null);
            String apiKey = configManager.getConfig("espresso.sync.api-key", null);

            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                log.error("Base URL do espresso não configurada. Verifique a configuração 'espresso.sync.base-url'");
                return false;
            }

            if (apiKey == null || apiKey.trim().isEmpty()) {
                log.error("API Key do espresso não configurada. Verifique a configuração 'espresso.sync.api-key'");
                return false;
            }

            // Preparar payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", userId);
            payload.put("title", title);
            payload.put("body", body);
            payload.put("imageUrl", imageUrl);
            payload.put("deeplink", deeplink);
            payload.put("source", source);
            payload.put("payloadJson", payloadJson);

            // Enviar requisição para o espresso_back
            String responseBody = restClient.post()
                    .uri(baseUrl + "/api/notifications/send-to-user")
                    .header("X-ERP-KEY", apiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            log.info("NOTIFICATION_SENT_TO_USER userId={} title={} source={} status=200 response={}",
                    userId, title, source, responseBody);
            return true;

        } catch (RestClientResponseException e) {
            log.error("NOTIFICATION_SEND_ERROR userId={} status={} response={}",
                    userId, e.getRawStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("NOTIFICATION_SEND_ERROR userId={} error={}", userId, e.getMessage());
            return false;
        }
    }

    public boolean sendNotificationToBroadcast(String title, String body, String imageUrl,
                                               String deeplink, String source, String payloadJson) {
        try {
            String baseUrl = configManager.getConfig("espresso.sync.base-url", null);
            String apiKey = configManager.getConfig("espresso.sync.api-key", null);

            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                log.error("Base URL do espresso não configurada. Verifique a configuração 'espresso.sync.base-url'");
                return false;
            }

            if (apiKey == null || apiKey.trim().isEmpty()) {
                log.error("API Key do espresso não configurada. Verifique a configuração 'espresso.sync.api-key'");
                return false;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("title", title);
            payload.put("body", body);
            payload.put("imageUrl", imageUrl);
            payload.put("deeplink", deeplink);
            payload.put("source", source);
            payload.put("payloadJson", payloadJson);

            String responseBody = restClient.post()
                    .uri(baseUrl + "/api/notifications/send-broadcast")
                    .header("X-ERP-KEY", apiKey)
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .body(String.class);

            log.info("NOTIFICATION_SENT_BROADCAST title={} source={} status=200 response={}",
                    title, source, responseBody);
            return true;
        } catch (RestClientResponseException e) {
            log.error("NOTIFICATION_BROADCAST_ERROR status={} response={}",
                    e.getRawStatusCode(), e.getResponseBodyAsString());
            return false;
        } catch (Exception e) {
            log.error("NOTIFICATION_BROADCAST_ERROR error={}", e.getMessage());
            return false;
        }
    }
}
