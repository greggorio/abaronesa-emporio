package com.baronesa.website.repository.delivery;

import com.baronesa.website.entity.delivery.DeliveryOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {
    Optional<DeliveryOrder> findByUberDeliveryId(String uberDeliveryId);

    Optional<DeliveryOrder> findByExternalId(String externalId);

    Optional<DeliveryOrder> findByPaymentId(Long paymentId);
}
