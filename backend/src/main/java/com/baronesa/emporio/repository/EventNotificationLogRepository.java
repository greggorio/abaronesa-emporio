package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.EventNotificationLog;
import com.baronesa.emporio.entity.EventNotificationLog.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventNotificationLogRepository extends JpaRepository<EventNotificationLog, Long> {
    boolean existsByEventoIdAndAnoAndTipo(Long eventoId, Integer ano, NotificationType tipo);
}
