package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.BirthdayNotificationLog;
import com.baronesa.emporio.entity.BirthdayNotificationLog.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BirthdayNotificationLogRepository extends JpaRepository<BirthdayNotificationLog, Long> {
    
    boolean existsByClienteIdAndAnoAndTipo(Long clienteId, Integer ano, NotificationType tipo);
}