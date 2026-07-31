package com.baronesa.emporio.service.payment;

import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementação stub que apenas guarda alterações em memória.
 * Serve como desacoplamento enquanto não existe integração real com o domínio de vendas.
 */
@Slf4j
@Component
public class InMemoryPaymentStatusUpdater implements PaymentStatusUpdater {

    private final List<Map<String, Object>> updates = new CopyOnWriteArrayList<>();

    @Override
    public void onPaymentStatusUpdated(String providerPaymentId, String status, String rawPayload) {
        log.info("Stub PaymentStatusUpdater: pagamento {} -> status {}", providerPaymentId, status);
        updates.add(Map.of(
                "providerPaymentId", providerPaymentId,
                "status", status,
                "payload", rawPayload,
                "updatedAt", LocalDateTime.now()
        ));
    }

    public List<Map<String, Object>> getUpdates() {
        return updates;
    }

    @Override
    public void onPaymentStatusUpdated(com.baronesa.emporio.service.payment.model.PaymentStatusUpdate update) {
        onPaymentStatusUpdated(update.getProviderPaymentId(), update.getProviderStatus(), update.getRawPayload());
    }
}
