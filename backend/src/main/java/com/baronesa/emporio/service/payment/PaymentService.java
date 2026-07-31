package com.baronesa.emporio.service.payment;

import com.baronesa.emporio.entity.Payment;
import com.baronesa.emporio.repository.PaymentRepository;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public Payment upsertFromStatusUpdate(PaymentStatusUpdate update) {
        if (update == null || update.getGateway() == null) {
            throw new IllegalArgumentException("PaymentStatusUpdate inválido");
        }

        Optional<Payment> existing = Optional.empty();
        if (update.getProviderPaymentId() != null && !update.getProviderPaymentId().isBlank()) {
            existing = paymentRepository.findByGatewayAndProviderPaymentId(update.getGateway(), update.getProviderPaymentId());
        }

        Payment payment = existing.orElseGet(Payment::new);
        payment.setGateway(update.getGateway());
        payment.setMethod(update.getMethod());
        payment.setExternalReference(update.getExternalReference());
        payment.setProviderPaymentId(update.getProviderPaymentId());
        payment.setNormalizedStatus(update.getNormalizedStatus());
        payment.setProviderStatus(update.getProviderStatus());
        payment.setProviderStatusDetail(update.getProviderStatusDetail());
        payment.setAmount(update.getAmount());
        payment.setRawPayload(update.getRawPayload());
        payment.setPaidAt(toLocalDateTime(update.getPaidAt()));
        payment.setCanceledAt(toLocalDateTime(update.getCanceledAt()));
        payment.setExpiredAt(toLocalDateTime(update.getExpiredAt()));

        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> findByExternalReference(PaymentGatewayType gateway, String externalReference) {
        if (gateway != null) {
            return paymentRepository.findFirstByGatewayAndExternalReferenceOrderByIdDesc(gateway, externalReference);
        }
        return paymentRepository.findFirstByExternalReferenceOrderByIdDesc(externalReference);
    }

    private LocalDateTime toLocalDateTime(java.time.Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
