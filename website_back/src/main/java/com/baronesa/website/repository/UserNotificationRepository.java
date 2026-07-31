package com.baronesa.website.repository;

import com.baronesa.website.entity.UserNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

    // Listar notificações do usuário
    Page<UserNotification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // Listar notificações do usuário (sem paginação)
    List<UserNotification> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Contar notificações não lidas do usuário
    @Query("SELECT COUNT(n) FROM UserNotification n WHERE n.userId = :userId AND n.readAt IS NULL")
    int countUnreadByUserId(@Param("userId") Long userId);

    // Marcar notificação como lida (por ID e usuário)
    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = :readAt WHERE n.id = :id AND n.userId = :userId")
    int markAsReadByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    // Marcar todas as notificações do usuário como lidas
    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = :readAt WHERE n.userId = :userId AND n.readAt IS NULL")
    int markAllAsReadByUserId(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);

    // Verificar se a notificação pertence ao usuário
    @Query("SELECT COUNT(n) FROM UserNotification n WHERE n.id = :id AND n.userId = :userId")
    int countByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}