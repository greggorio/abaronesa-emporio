package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.PaymentSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentSettingsRepository extends JpaRepository<PaymentSettings, Long> {
}
