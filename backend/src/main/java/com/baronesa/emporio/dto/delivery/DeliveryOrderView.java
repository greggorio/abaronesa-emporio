package com.baronesa.emporio.dto.delivery;

import com.baronesa.emporio.enums.DeliveryOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderView {
    private Long id;
    private String externalId;
    private String deliveryId;
    private DeliveryOrderStatus status;
    private String uberStatus;
    private String trackingUrl;
    private LocalDateTime dropoffEta;
    private LocalDateTime pickupEta;
    private String dropoffAddress;
    private String pickupAddress;
    private LocalDateTime updatedAt;
    private List<DeliveryOrderItemView> items;
}
