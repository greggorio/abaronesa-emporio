package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.DeliveryOrderItem;
import com.baronesa.emporio.enums.DeliveryOrderItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryOrderItemRepository extends JpaRepository<DeliveryOrderItem, Long> {
    List<DeliveryOrderItem> findByStatusIn(List<DeliveryOrderItemStatus> statuses);
    List<DeliveryOrderItem> findByStatusInAndKdsArchivedFalse(List<DeliveryOrderItemStatus> statuses);
    List<DeliveryOrderItem> findByDeliveryOrderId(Long deliveryOrderId);
}
