package com.baronesa.emporio.repository;

import java.util.List;
import java.util.Optional;

import com.baronesa.emporio.entity.Notification;
import com.baronesa.emporio.entity.NotificationRecipient;
import com.baronesa.emporio.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, Long> {
    
    Optional<NotificationRecipient> findByNotificationAndUser(Notification notification, Usuario user);
    
    List<NotificationRecipient> findByNotification(Notification notification);
    
    List<NotificationRecipient> findByUser(Usuario user);
    
    /**
     * Verifica se existe recipient não lido para uma notificação
     */
    boolean existsByNotificationAndReadAtIsNull(Notification notification);
}