package com.baronesa.emporio.repository;

import java.util.List;

import com.baronesa.emporio.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    // Buscar notificações para um usuário (incluindo lidas)
    @Query(value = "SELECT n.id, n.titulo, n.mensagem, n.tipo, n.importancia, " +
                  "n.data_criacao, n.data_expiracao, n.link, u.nome as created_by_name, nr.read_at " +
                  "FROM notifications n " +
                  "LEFT JOIN usuarios u ON n.created_by = u.id " +
                  "LEFT JOIN notification_recipients nr ON n.id = nr.notification_id AND nr.user_id = :userId " +
                  "WHERE n.ativo = true " +
                  "AND (n.data_expiracao IS NULL OR CAST(n.data_expiracao AS TIMESTAMP) >= CURRENT_TIMESTAMP) " +
                  "AND (nr.deleted_at IS NULL OR nr.deleted_at IS NULL) " +
                  "AND (" +
                  "    (n.tipo = 'GERAL') OR " +
                  "    (n.tipo = 'INDIVIDUAL' AND n.user_id = :userId)" +
                  ") " +
                  "ORDER BY " +
                  "    CASE WHEN n.importancia = 'URGENTE' THEN 1 " +
                  "         WHEN n.importancia = 'ALTA' THEN 2 " +
                  "         WHEN n.importancia = 'MEDIA' THEN 3 " +
                  "         ELSE 4 END, " +
                  "    n.data_criacao DESC " +
                  "LIMIT :limit OFFSET :offset", 
           nativeQuery = true)
    List<Object[]> findNotificationsForUserIncludingRead(@Param("userId") Long userId,
                                                         @Param("limit") int limit,
                                                         @Param("offset") int offset);

    // Buscar apenas notificações não lidas
    @Query(value = "SELECT n.id, n.titulo, n.mensagem, n.tipo, n.importancia, " +
                  "n.data_criacao, n.data_expiracao, n.link, u.nome as created_by_name, nr.read_at " +
                  "FROM notifications n " +
                  "LEFT JOIN usuarios u ON n.created_by = u.id " +
                  "LEFT JOIN notification_recipients nr ON n.id = nr.notification_id AND nr.user_id = :userId " +
                  "WHERE n.ativo = true " +
                  "AND (n.data_expiracao IS NULL OR CAST(n.data_expiracao AS TIMESTAMP) >= CURRENT_TIMESTAMP) " +
                  "AND (nr.deleted_at IS NULL) " +
                  "AND (nr.read_at IS NULL) " +
                  "AND (" +
                  "    (n.tipo = 'GERAL') OR " +
                  "    (n.tipo = 'INDIVIDUAL' AND n.user_id = :userId)" +
                  ") " +
                  "ORDER BY " +
                  "    CASE WHEN n.importancia = 'URGENTE' THEN 1 " +
                  "         WHEN n.importancia = 'ALTA' THEN 2 " +
                  "         WHEN n.importancia = 'MEDIA' THEN 3 " +
                  "         ELSE 4 END, " +
                  "    n.data_criacao DESC " +
                  "LIMIT :limit OFFSET :offset", 
           nativeQuery = true)
    List<Object[]> findUnreadNotificationsForUser(@Param("userId") Long userId,
                                                  @Param("limit") int limit,
                                                  @Param("offset") int offset);

    // Contar total de notificações
    @Query(value = "SELECT COUNT(n.id) " +
                  "FROM notifications n " +
                  "LEFT JOIN notification_recipients nr ON n.id = nr.notification_id AND nr.user_id = :userId " +
                  "WHERE n.ativo = true " +
                  "AND (n.data_expiracao IS NULL OR CAST(n.data_expiracao AS TIMESTAMP) >= CURRENT_TIMESTAMP) " +
                  "AND (nr.deleted_at IS NULL OR nr.deleted_at IS NULL) " +
                  "AND (" +
                  "    (n.tipo = 'GERAL') OR " +
                  "    (n.tipo = 'INDIVIDUAL' AND n.user_id = :userId)" +
                  ")", 
           nativeQuery = true)
    Long countNotificationsForUser(@Param("userId") Long userId);

    // Contar notificações não lidas
    @Query(value = "SELECT COUNT(n.id) " +
                  "FROM notifications n " +
                  "LEFT JOIN notification_recipients nr ON n.id = nr.notification_id AND nr.user_id = :userId " +
                  "WHERE n.ativo = true " +
                  "AND (n.data_expiracao IS NULL OR CAST(n.data_expiracao AS TIMESTAMP) >= CURRENT_TIMESTAMP) " +
                  "AND (nr.deleted_at IS NULL) " +
                  "AND (nr.read_at IS NULL) " +
                  "AND (" +
                  "    (n.tipo = 'GERAL') OR " +
                  "    (n.tipo = 'INDIVIDUAL' AND n.user_id = :userId)" +
                  ")", 
           nativeQuery = true)
    Long countUnreadNotificationsForUser(@Param("userId") Long userId);

    // Buscar notificações por role específica
    @Query(value = "SELECT n.id, n.titulo, n.mensagem, n.tipo, n.importancia, " +
                  "n.data_criacao, n.data_expiracao, n.link, u.nome as created_by_name " +
                  "FROM notifications n " +
                  "LEFT JOIN usuarios u ON n.created_by = u.id " +
                  "WHERE n.ativo = true " +
                  "AND (n.data_expiracao IS NULL OR CAST(n.data_expiracao AS TIMESTAMP) >= CURRENT_TIMESTAMP) " +
                  "AND n.tipo = 'ROLE' " +
                  "AND n.role = :role " +
                  "ORDER BY " +
                  "    CASE WHEN n.importancia = 'URGENTE' THEN 1 " +
                  "         WHEN n.importancia = 'ALTA' THEN 2 " +
                  "         WHEN n.importancia = 'MEDIA' THEN 3 " +
                  "         ELSE 4 END, " +
                  "    n.data_criacao DESC", 
           nativeQuery = true)
    List<Object[]> findNotificationsByRole(@Param("role") String role);
    
    // Buscar atividades recentes por títulos específicos para o sistema de beleza
    @Query(value = "SELECT n.id, n.titulo, n.mensagem, n.tipo, n.importancia, " +
                  "n.data_criacao, n.data_expiracao, n.link, u.nome as created_by_name " +
                  "FROM notifications n " +
                  "LEFT JOIN usuarios u ON n.created_by = u.id " +
                  "WHERE n.ativo = true " +
                  "AND (n.data_expiracao IS NULL OR CAST(n.data_expiracao AS TIMESTAMP) >= CURRENT_TIMESTAMP) " +
                  "AND n.tipo = 'ROLE' " +
                  "AND n.role = :role " +
                  "AND n.titulo IN (:titulos) " +
                  "ORDER BY n.data_criacao DESC " +
                  "LIMIT :limit", 
           nativeQuery = true)
    List<Object[]> findRecentActivitiesByTitles(@Param("titulos") List<String> titulos,
                                               @Param("role") String role,
                                               @Param("limit") int limit);

    /**
     * Busca notificações por título e mensagem contendo termos específicos
     */
    List<Notification> findByTituloContainingAndMensagemContaining(String titulo, String mensagem);
}