package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.DeliveryOrder;
import com.baronesa.emporio.enums.DeliveryOrderStatus;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {
    Optional<DeliveryOrder> findByProviderGatewayAndProviderPaymentId(PaymentGatewayType gateway, String providerPaymentId);
    Optional<DeliveryOrder> findByMpPaymentId(String mpPaymentId);
    Optional<DeliveryOrder> findByExternalReference(String externalReference);
    Optional<DeliveryOrder> findTopByClienteIdAndStatusNotInOrderByCreatedAtDesc(Long clienteId, Collection<DeliveryOrderStatus> ignored);

    @Query("select distinct o from DeliveryOrder o left join fetch o.itens where o.id = :orderId")
    Optional<DeliveryOrder> findWithItems(Long orderId);
}
