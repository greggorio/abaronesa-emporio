package com.baronesa.website.repository;

import com.baronesa.website.entity.Reward;
import com.baronesa.website.dto.RewardWithCustomerName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RewardRepository extends JpaRepository<Reward, Long> {

    List<Reward> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Reward> findByIdAndUserId(Long id, Long userId);

    @Query("""
        SELECT new com.baronesa.website.dto.RewardWithCustomerName(
            r.id,
            r.userId,
            COALESCE(cr.nome, CONCAT('Usuário ', r.userId)),
            r.title,
            r.description,
            r.imageUrl,
            r.validUntil,
            r.status,
            r.createdAt,
            r.redeemedAt,
            r.notificationHistoryId
        )
        FROM Reward r
        LEFT JOIN ClienteRef cr ON r.userId = cr.id
        ORDER BY r.createdAt DESC
        """)
    List<RewardWithCustomerName> findAllWithCustomerNames();

    @Query("""
        SELECT new com.baronesa.website.dto.RewardWithCustomerName(
            r.id,
            r.userId,
            COALESCE(cr.nome, CONCAT('Usuário ', r.userId)),
            r.title,
            r.description,
            r.imageUrl,
            r.validUntil,
            r.status,
            r.createdAt,
            r.redeemedAt,
            r.notificationHistoryId
        )
        FROM Reward r
        LEFT JOIN ClienteRef cr ON r.userId = cr.id
        WHERE r.userId = :userId
        ORDER BY r.createdAt DESC
        """)
    List<RewardWithCustomerName> findByUserIdWithCustomerName(@Param("userId") Long userId);
}
