package com.baronesa.website.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.WebpushConfig;
import com.google.firebase.messaging.WebpushFcmOptions;
import com.google.firebase.messaging.WebpushNotification;
import com.baronesa.website.entity.NotificationHistory;
import com.baronesa.website.entity.NotificationSubscription;
import com.baronesa.website.repository.NotificationHistoryRepository;
import com.baronesa.website.repository.NotificationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationHistoryRepository historyRepository;
    private final UserNotificationService userNotificationService;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional
    public void subscribe(String token, String deviceInfo) {
        int inserted = subscriptionRepository.insertIgnore(token, deviceInfo);
        if (inserted == 0) {
            subscriptionRepository.reactivateByToken(token);
        }
    }

    @Transactional
    public void subscribeWithUserId(String token, String deviceInfo, Long userId) {
        // Upsert pelo token (não duplicar)
        Optional<NotificationSubscription> existingSubscription = subscriptionRepository.findByToken(token);

        if (existingSubscription.isPresent()) {
            // Atualiza a subscription existente com userId e lastSeenAt
            NotificationSubscription subscription = existingSubscription.get();
            // Não sobrescrever userId existente se já estiver definido
            if (subscription.getUserId() == null) {
                subscription.setUserId(userId);
                subscription.setLastSeenAt(LocalDateTime.now());
                subscriptionRepository.save(subscription);
                log.info("Linked subscription to userId={} tokenSuffix={}", userId,
                         token.substring(Math.max(0, token.length() - 5)));
            }
        } else {
            // Cria nova subscription
            int inserted = subscriptionRepository.insertIgnore(token, deviceInfo);
            if (inserted > 0) {
                // Agora atualiza o userId e lastSeenAt
                Optional<NotificationSubscription> newSubscription = subscriptionRepository.findByToken(token);
                if (newSubscription.isPresent()) {
                    NotificationSubscription subscription = newSubscription.get();
                    subscription.setUserId(userId);
                    subscription.setLastSeenAt(LocalDateTime.now());
                    subscriptionRepository.save(subscription);
                    log.info("Linked subscription to userId={} tokenSuffix={}", userId,
                             token.substring(Math.max(0, token.length() - 5)));
                }
            } else {
                // Se não foi possível inserir (conflito), reativa e atualiza o userId
                subscriptionRepository.reactivateByToken(token);
                Optional<NotificationSubscription> updatedSubscription = subscriptionRepository.findByToken(token);
                if (updatedSubscription.isPresent()) {
                    NotificationSubscription subscription = updatedSubscription.get();
                    // Não sobrescrever userId existente se já estiver definido
                    if (subscription.getUserId() == null) {
                        subscription.setUserId(userId);
                        subscription.setLastSeenAt(LocalDateTime.now());
                        subscriptionRepository.save(subscription);
                        log.info("Linked subscription to userId={} tokenSuffix={}", userId,
                                 token.substring(Math.max(0, token.length() - 5)));
                    }
                }
            }
        }
    }

    public int countEligibleUsers() {
        List<Long> eligibleUserIds = subscriptionRepository.findDistinctActiveUserIds();
        return eligibleUserIds.size();
    }

    @Transactional
    public NotificationHistory sendNotification(String title, String body, String imageUrl) throws FirebaseMessagingException {
        List<NotificationSubscription> activeSubscriptions = subscriptionRepository.findByActiveTrue();

        if (activeSubscriptions.isEmpty()) {
            throw new RuntimeException("Nenhum dispositivo inscrito para receber notificações");
        }

        List<String> tokens = activeSubscriptions.stream()
                .map(NotificationSubscription::getToken)
                .toList();

        String fullDeeplink = buildFullUrl("/areacliente/notificacoes");
        String defaultIcon = buildFullUrl("/favicon-192.png");
        WebpushNotification webpushNotification = WebpushNotification.builder()
                .setTitle(title)
                .setBody(body)
                .setIcon(imageUrl != null ? imageUrl : defaultIcon)
                .setBadge(defaultIcon)
                .build();

        // Notificação nativa (Android/iOS) para apps nativos
        Notification nativeNotification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .setImage(imageUrl)
                .build();

        WebpushConfig webpushConfig = WebpushConfig.builder()
                .putHeader("TTL", "86400")
                .setNotification(webpushNotification)
                .setFcmOptions(WebpushFcmOptions.withLink(fullDeeplink))
                .build();

        MulticastMessage message = MulticastMessage.builder()
                .setNotification(nativeNotification)
                .setWebpushConfig(webpushConfig)
                // Dados redundantes para o service worker mostrar a notificação
                .putData("title", title)
                .putData("body", body)
                .putData("imageUrl", imageUrl != null ? imageUrl : "")
                .putData("url", fullDeeplink)
                .addAllTokens(tokens)
                .build();

        // Enviar notificação
        BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);

        // Processar respostas e desativar tokens inválidos
        List<String> invalidTokens = new ArrayList<>();
        for (int i = 0; i < response.getResponses().size(); i++) {
            SendResponse sendResponse = response.getResponses().get(i);
            if (!sendResponse.isSuccessful()) {
                String errorCode = sendResponse.getException() != null ?
                        sendResponse.getException().getErrorCode().name() : "UNKNOWN";

                // Desativar tokens inválidos ou não registrados
                if ("INVALID_ARGUMENT".equals(errorCode) || "NOT_FOUND".equals(errorCode) ||
                    "UNREGISTERED".equals(errorCode)) {
                    invalidTokens.add(tokens.get(i));
                }
            }
        }

        // Desativar tokens inválidos
        for (String invalidToken : invalidTokens) {
            subscriptionRepository.findByToken(invalidToken).ifPresent(sub -> {
                sub.setActive(false);
                subscriptionRepository.save(sub);
            });
        }

        // Registrar no inbox dos usuários vinculados
        List<Long> userIds = activeSubscriptions.stream()
                .filter(sub -> sub.getUserId() != null)
                .map(NotificationSubscription::getUserId)
                .distinct()
                .toList();

        for (Long userId : userIds) {
            userNotificationService.createNotification(
                userId,
                title,
                body,
                imageUrl,
                fullDeeplink,
                "MANUAL",
                null
            );
        }

        // Salvar histórico
        NotificationHistory history = new NotificationHistory();
        history.setTitle(title);
        history.setBody(body);
        history.setImageUrl(imageUrl);
        history.setRecipientsCount(response.getSuccessCount());

        return historyRepository.save(history);
    }

    @Transactional
    public NotificationHistory sendNotificationToTokens(List<String> tokens, String title, String body, String imageUrl) throws FirebaseMessagingException {
        return sendNotificationToTokens(tokens, title, body, imageUrl, "/areacliente/recompensas", "REWARD", null);
    }

    @Transactional
    public NotificationHistory sendNotificationToTokens(List<String> tokens, String title, String body, String imageUrl,
                                                      String deeplink, String source, String payloadJson) throws FirebaseMessagingException {
        if (tokens.isEmpty()) {
            throw new RuntimeException("Nenhum token fornecido para envio de notificação");
        }

        String fullDeeplink = buildFullUrl(deeplink);
        String defaultIcon = buildFullUrl("/favicon-192.png");
        WebpushNotification webpushNotification = WebpushNotification.builder()
                .setTitle(title)
                .setBody(body)
                .setIcon(imageUrl != null ? imageUrl : defaultIcon)
                .setBadge(defaultIcon)
                .build();

        // Notificação nativa (Android/iOS) para apps nativos
        Notification nativeNotification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .setImage(imageUrl)
                .build();

        WebpushConfig webpushConfig = WebpushConfig.builder()
                .putHeader("TTL", "86400")
                .setNotification(webpushNotification)
                .setFcmOptions(WebpushFcmOptions.withLink(fullDeeplink))
                .build();

        MulticastMessage message = MulticastMessage.builder()
                .setNotification(nativeNotification)
                .setWebpushConfig(webpushConfig)
                // Dados redundantes para o service worker mostrar a notificação
                .putData("title", title)
                .putData("body", body)
                .putData("imageUrl", imageUrl != null ? imageUrl : "")
                .putData("url", fullDeeplink)
                .addAllTokens(tokens)
                .build();

        // Enviar notificação
        BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);

        // Processar respostas e desativar tokens inválidos
        List<String> invalidTokens = new ArrayList<>();
        for (int i = 0; i < response.getResponses().size(); i++) {
            SendResponse sendResponse = response.getResponses().get(i);
            if (!sendResponse.isSuccessful()) {
                String errorCode = sendResponse.getException() != null ?
                        sendResponse.getException().getErrorCode().name() : "UNKNOWN";

                // Desativar tokens inválidos ou não registrados
                if ("INVALID_ARGUMENT".equals(errorCode) || "NOT_FOUND".equals(errorCode) ||
                    "UNREGISTERED".equals(errorCode)) {
                    invalidTokens.add(tokens.get(i));
                }
            }
        }

        // Desativar tokens inválidos
        for (String invalidToken : invalidTokens) {
            subscriptionRepository.findByToken(invalidToken).ifPresent(sub -> {
                sub.setActive(false);
                subscriptionRepository.save(sub);
            });
        }

        // Obter os userIds dos tokens para registrar notificações no inbox
        List<NotificationSubscription> subscriptions = subscriptionRepository.findByTokenIn(tokens);
        List<Long> userIds = subscriptions.stream()
                .filter(sub -> sub.getUserId() != null)
                .map(NotificationSubscription::getUserId)
                .distinct()
                .toList();

        // Registrar notificações no inbox dos usuários
        for (Long userId : userIds) {
            userNotificationService.createNotification(
                userId,
                title,
                body,
                imageUrl,
                fullDeeplink, // Deeplink absoluto baseado no app.frontend.url
                source, // Fonte parametrizada
                payloadJson // Payload parametrizado
            );
        }

        // Salvar histórico
        NotificationHistory history = new NotificationHistory();
        history.setTitle(title);
        history.setBody(body);
        history.setImageUrl(imageUrl);
        history.setRecipientsCount(response.getSuccessCount());

        return historyRepository.save(history);
    }

    public List<NotificationHistory> getHistory() {
        return historyRepository.findAllByOrderBySentAtDesc();
    }

    /**
     * Monta URL absoluta a partir da base configurada (app.frontend.url) e de um deeplink opcional.
     */
    private String buildFullUrl(String deeplink) {
        if (frontendUrl == null || frontendUrl.trim().isEmpty()) {
            throw new IllegalStateException("app.frontend.url não configurado");
        }

        String base = frontendUrl.trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }

        if (deeplink == null || deeplink.isBlank()) {
            return base + "/";
        }

        String normalized = deeplink.startsWith("/") ? deeplink : "/" + deeplink;
        return base + normalized;
    }
}
