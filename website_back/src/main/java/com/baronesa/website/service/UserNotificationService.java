package com.baronesa.website.service;

import com.baronesa.website.entity.UserNotification;
import com.baronesa.website.repository.UserNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserNotificationService {

    private final UserNotificationRepository userNotificationRepository;

    /**
     * Listar notificações do usuário
     */
    @Transactional(readOnly = true)
    public Page<UserNotification> getUserNotifications(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return userNotificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Contar notificações não lidas do usuário
     */
    @Transactional(readOnly = true)
    public int getUnreadCount(Long userId) {
        return userNotificationRepository.countUnreadByUserId(userId);
    }

    /**
     * Marcar notificação como lida
     */
    @Transactional
    public boolean markAsRead(Long notificationId, Long userId) {
        // Verificar se a notificação pertence ao usuário
        if (userNotificationRepository.countByIdAndUserId(notificationId, userId) == 0) {
            return false;
        }

        int rowsUpdated = userNotificationRepository.markAsReadByIdAndUserId(
            notificationId, 
            userId, 
            LocalDateTime.now()
        );
        return rowsUpdated > 0;
    }

    /**
     * Marcar todas as notificações do usuário como lidas
     */
    @Transactional
    public boolean markAllAsRead(Long userId) {
        int rowsUpdated = userNotificationRepository.markAllAsReadByUserId(userId, LocalDateTime.now());
        return rowsUpdated > 0;
    }

    /**
     * Criar uma nova notificação para um usuário
     */
    @Transactional
    public UserNotification createNotification(Long userId, String title, String body, String imageUrl, String deeplink, String source, String payloadJson) {
        UserNotification notification = new UserNotification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setImageUrl(imageUrl);
        notification.setDeeplink(deeplink);
        notification.setSource(source);
        notification.setPayloadJson(payloadJson);
        // createdAt será preenchido pelo @PrePersist

        return userNotificationRepository.save(notification);
    }

    /**
     * Obter uma notificação específica do usuário
     */
    @Transactional(readOnly = true)
    public Optional<UserNotification> getNotificationByIdAndUser(Long notificationId, Long userId) {
        return userNotificationRepository.findById(notificationId)
            .filter(notification -> notification.getUserId().equals(userId));
    }
}