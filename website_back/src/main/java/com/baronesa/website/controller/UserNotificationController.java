package com.baronesa.website.controller;

import com.baronesa.website.entity.UserNotification;
import com.baronesa.website.security.CustomUserPrincipal;
import com.baronesa.website.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class UserNotificationController {

    private final UserNotificationService userNotificationService;

    /**
     * Listar notificações do usuário logado
     */
    @GetMapping("/my")
    public ResponseEntity<Page<UserNotification>> getUserNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        // Limitar o tamanho máximo da paginação para evitar abusos
        int maxSize = 100;
        int actualSize = Math.min(size, maxSize);

        Page<UserNotification> notifications = userNotificationService.getUserNotifications(userId, page, actualSize);
        return ResponseEntity.ok(notifications);
    }

    /**
     * Contar notificações não lidas do usuário logado
     */
    @GetMapping("/my/unread-count")
    public ResponseEntity<Map<String, Integer>> getUnreadCount() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        int unreadCount = userNotificationService.getUnreadCount(userId);
        Map<String, Integer> response = new HashMap<>();
        response.put("unreadCount", unreadCount);
        return ResponseEntity.ok(response);
    }

    /**
     * Marcar notificação específica como lida
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<?> markAsRead(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        boolean success = userNotificationService.markAsRead(id, userId);
        if (success) {
            return ResponseEntity.ok().build();
        } else {
            log.warn("Tentativa de marcar notificação como lida falhou - notificação {} não pertence ao usuário {}", id, userId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Marcar todas as notificações do usuário como lidas
     */
    @PatchMapping("/my/read-all")
    public ResponseEntity<?> markAllAsRead() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }

        boolean success = userNotificationService.markAllAsRead(userId);
        // O método é idempotente - retorna 200 mesmo se não houver notificações para marcar como lidas
        return ResponseEntity.ok().build();
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
            !(authentication.getPrincipal() instanceof CustomUserPrincipal principal)) {
            return null;
        }

        try {
            return Long.parseLong(principal.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}