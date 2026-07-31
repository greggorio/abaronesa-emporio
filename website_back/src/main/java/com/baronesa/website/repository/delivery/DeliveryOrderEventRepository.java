package com.baronesa.website.repository.delivery;

import com.baronesa.website.entity.delivery.DeliveryOrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryOrderEventRepository extends JpaRepository<DeliveryOrderEvent, Long> {
    List<DeliveryOrderEvent> findByDeliveryIdOrderByCreatedAtAsc(String deliveryId);
}
