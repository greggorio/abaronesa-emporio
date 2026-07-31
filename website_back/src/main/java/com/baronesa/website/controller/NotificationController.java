package com.baronesa.website.controller;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.baronesa.website.entity.NotificationHistory;
import com.baronesa.website.entity.NotificationSubscription;
import com.baronesa.website.repository.NotificationSubscriptionRepository;
import com.baronesa.website.security.CustomUserPrincipal;
import com.baronesa.website.service.NotificationService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@CrossOrigin(
    origins = {
        "http://localhost:5173",
        "https://localhost", // Capacitor/WebView usa https://localhost
        "https://monicadepilacoes.com.br",
        "https://www.monicadepilacoes.com.br"
    },
    allowCredentials = "true"
)
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSubscriptionRepository notificationSubscriptionRepository;

    @Value("${website.sync.api-key:default-key-for-dev}")
    private String apiKey;

    @PostMapping("/subscribe")
    public ResponseEntity<String> subscribe(@RequestBody SubscribeRequest request) {
        try {
            // Detectar usuário autenticado
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                // Se autenticado, atualizar a subscription do token com userId e lastSeenAt
                try {
                    Long userId = Long.parseLong(principal.getUserId());
                    notificationService.subscribeWithUserId(request.getToken(), request.getDeviceInfo(), userId);
                } catch (NumberFormatException e) {
                    // Se o userId não for um número válido, usar o fluxo normal
                    notificationService.subscribe(request.getToken(), request.getDeviceInfo());
                }
            } else {
                // Se não autenticado, continuar funcionando como hoje (user_id fica null)
                notificationService.subscribe(request.getToken(), request.getDeviceInfo());
            }
            return ResponseEntity.ok("Inscrito com sucesso!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao inscrever: " + e.getMessage());
        }
    }

    @PostMapping("/send-to-user")
    public ResponseEntity<String> sendNotificationToUser(
            @RequestHeader("X-ERP-KEY") String apiKeyHeader,
            @RequestBody SendNotificationToUserRequest request) {

        // Validar API key
        if (!apiKey.equals(apiKeyHeader)) {
            return ResponseEntity.status(401).body("API Key inválida");
        }

        try {
            // Buscar tokens por userId
            List<NotificationSubscription> subscriptions = notificationSubscriptionRepository.findByUserId(request.getUserId());
            List<String> tokens = subscriptions.stream()
                    .filter(NotificationSubscription::getActive)
                    .map(NotificationSubscription::getToken)
                    .toList();

            if (tokens.isEmpty()) {
                return ResponseEntity.ok("Nenhum token ativo encontrado para o usuário");
            }

            // Enviar notificação via NotificationService
            notificationService.sendNotificationToTokens(
                    tokens,
                    request.getTitle(),
                    request.getBody(),
                    request.getImageUrl(),
                    request.getDeeplink(),
                    request.getSource(),
                    request.getPayloadJson()
            );

            return ResponseEntity.ok("Notificação enviada com sucesso para o usuário: " + request.getUserId());
        } catch (FirebaseMessagingException e) {
            return ResponseEntity.badRequest().body("Erro ao enviar notificação: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao enviar notificação: " + e.getMessage());
        }
    }

    @PostMapping("/send-broadcast")
    public ResponseEntity<String> sendNotificationBroadcast(
            @RequestHeader("X-ERP-KEY") String apiKeyHeader,
            @RequestBody SendNotificationBroadcastRequest request) {

        if (!apiKey.equals(apiKeyHeader)) {
            return ResponseEntity.status(401).body("API Key inválida");
        }

        try {
            List<NotificationSubscription> subscriptions = notificationSubscriptionRepository.findByActiveTrue();
            List<String> tokens = subscriptions.stream()
                    .map(NotificationSubscription::getToken)
                    .toList();

            if (tokens.isEmpty()) {
                return ResponseEntity.ok("Nenhum token ativo encontrado");
            }

            notificationService.sendNotificationToTokens(
                    tokens,
                    request.getTitle(),
                    request.getBody(),
                    request.getImageUrl(),
                    request.getDeeplink(),
                    request.getSource(),
                    request.getPayloadJson()
            );

            return ResponseEntity.ok("Notificação enviada para todos os tokens ativos");
        } catch (FirebaseMessagingException e) {
            return ResponseEntity.badRequest().body("Erro ao enviar notificação: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao enviar notificação: " + e.getMessage());
        }
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendNotification(@RequestBody SendNotificationRequest request) {
        try {
            NotificationHistory history = notificationService.sendNotification(
                    request.getTitle(),
                    request.getBody(),
                    request.getImageUrl()
            );
            return ResponseEntity.ok(history);
        } catch (FirebaseMessagingException e) {
            return ResponseEntity.badRequest().body("Erro ao enviar notificação: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/history")
    public ResponseEntity<List<NotificationHistory>> getHistory() {
        return ResponseEntity.ok(notificationService.getHistory());
    }

    @GetMapping("/eligible-users/count")
    public ResponseEntity<?> getEligibleUsersCount() {
        try {
            // Verificar se o usuário está autenticado e tem role ADMIN
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body("Usuário não autenticado");
            }

            if (authentication.getPrincipal() instanceof CustomUserPrincipal principal) {
                if (!principal.hasRole("ROLE_ADMIN") && !principal.hasRole("ROLE_SYSTEM") && !principal.hasRole("ROLE_FUNCIONARIO")) {
                    return ResponseEntity.status(403).body("Acesso negado: permissão insuficiente");
                }
            } else {
                return ResponseEntity.status(401).body("Token inválido");
            }

            int eligibleUsers = notificationService.countEligibleUsers();
            return ResponseEntity.ok().body(new EligibleUsersResponse(eligibleUsers));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Erro ao contar usuários elegíveis: " + e.getMessage());
        }
    }

    @Data
    static class SubscribeRequest {
        private String token;
        private String deviceInfo;
    }

    @Data
    static class SendNotificationRequest {
        private String title;
        private String body;
        private String imageUrl;
    }

    @Data
    static class SendNotificationToUserRequest {
        private Long userId;
        private String title;
        private String body;
        private String imageUrl;
        private String deeplink;
        private String source;
        private String payloadJson;
    }

    @Data
    static class SendNotificationBroadcastRequest {
        private String title;
        private String body;
        private String imageUrl;
        private String deeplink;
        private String source;
        private String payloadJson;
    }

    @Data
    static class EligibleUsersResponse {
        private int eligibleUsers;

        public EligibleUsersResponse(int eligibleUsers) {
            this.eligibleUsers = eligibleUsers;
        }
    }
}
