package com.baronesa.emporio.repository;

import com.baronesa.emporio.entity.Payment;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByGatewayAndProviderPaymentId(PaymentGatewayType gateway, String providerPaymentId);

    Optional<Payment> findFirstByGatewayAndExternalReferenceOrderByIdDesc(PaymentGatewayType gateway, String externalReference);

    Optional<Payment> findFirstByExternalReferenceOrderByIdDesc(String externalReference);
}
