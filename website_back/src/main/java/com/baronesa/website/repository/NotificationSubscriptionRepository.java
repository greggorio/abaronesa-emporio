package com.baronesa.website.repository;

import com.baronesa.website.entity.NotificationSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    Optional<NotificationSubscription> findByToken(String token);

    List<NotificationSubscription> findByActiveTrue();

    @Query("""
        select distinct ns.userId
        from NotificationSubscription ns
        where ns.active = true
          and ns.userId is not null
        """)
    List<Long> findDistinctActiveUserIds();

    List<NotificationSubscription> findByUserId(Long userId);

    List<NotificationSubscription> findByTokenIn(List<String> tokens);

    @Modifying
    @Query(value = """
        INSERT INTO notification_subscriptions (token, device_info, created_at, active)
        VALUES (?1, ?2, NOW(), true)
        ON CONFLICT (token) DO NOTHING
        """, nativeQuery = true)
    int insertIgnore(String token, String deviceInfo);

    @Modifying
    @Query(value = """
        UPDATE notification_subscriptions
        SET active = true
        WHERE token = ?1 AND active = false
        """, nativeQuery = true)
    int reactivateByToken(String token);
}
