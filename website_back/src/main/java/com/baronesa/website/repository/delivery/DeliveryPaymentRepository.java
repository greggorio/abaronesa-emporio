package com.baronesa.website.repository.delivery;

import com.baronesa.website.entity.delivery.DeliveryPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryPaymentRepository extends JpaRepository<DeliveryPayment, Long> {
}
