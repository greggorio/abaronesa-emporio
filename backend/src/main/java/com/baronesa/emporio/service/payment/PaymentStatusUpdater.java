package com.baronesa.emporio.service.payment;

import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;

/**
 * Contrato para notificar mudanças de status de pagamentos vindos do gateway.
 * Implementações podem atualizar domínios de venda, emitir eventos, etc.
 */
public interface PaymentStatusUpdater {

    /**
     * Notifica mudança de status para um pagamento externo.
     *
     * @param providerPaymentId ID do pagamento no provedor (ex: Mercado Pago)
     * @param status            Status retornado pelo provedor
     * @param rawPayload        Payload completo (JSON) retornado pelo provedor
     */
    void onPaymentStatusUpdated(String providerPaymentId, String status, String rawPayload);

    void onPaymentStatusUpdated(PaymentStatusUpdate update);
}
