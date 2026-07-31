package com.baronesa.website.repository.delivery;

import com.baronesa.website.entity.delivery.DeliveryOrderItem;
import com.baronesa.website.enums.delivery.DeliveryItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DeliveryOrderItemRepository extends JpaRepository<DeliveryOrderItem, Long> {
    List<DeliveryOrderItem> findByStatusIn(Collection<DeliveryItemStatus> statuses);

    List<DeliveryOrderItem> findByStatusInAndKdsVisibleTrue(Collection<DeliveryItemStatus> statuses);
}
