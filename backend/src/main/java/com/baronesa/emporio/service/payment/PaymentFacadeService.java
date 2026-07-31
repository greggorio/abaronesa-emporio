package com.baronesa.emporio.service.payment;

import com.baronesa.emporio.service.payment.api.CardPaymentRequest;
import com.baronesa.emporio.service.payment.api.PaymentGatewayResult;
import com.baronesa.emporio.service.payment.api.PaymentResponse;
import com.baronesa.emporio.service.payment.api.PixPaymentRequest;
import com.baronesa.emporio.service.payment.gateway.PaymentGateway;
import com.baronesa.emporio.service.payment.gateway.PaymentGatewayRegistry;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentFacadeService {

    private final PaymentSettingsService paymentSettingsService;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentStatusUpdater paymentStatusUpdater;

    public PaymentResponse createCardPayment(CardPaymentRequest request) {
        PaymentGatewayType active = paymentSettingsService.getActiveGateway();
        PaymentGateway gateway = gatewayRegistry.resolve(active);
        PaymentGatewayResult result = gateway.createCardPayment(request);
        if (result != null && result.getStatusUpdate() != null) {
            try {
                paymentStatusUpdater.onPaymentStatusUpdated(result.getStatusUpdate());
            } catch (Exception e) {
                // Não quebrar o fluxo de pagamento caso a persistência falhe.
            }
        }
        return result != null ? result.getResponse() : null;
    }

    public PaymentResponse createPixPayment(PixPaymentRequest request) {
        PaymentGatewayType active = paymentSettingsService.getActiveGateway();
        PaymentGateway gateway = gatewayRegistry.resolve(active);
        PaymentGatewayResult result = gateway.createPixPayment(request);
        if (result != null && result.getStatusUpdate() != null) {
            try {
                paymentStatusUpdater.onPaymentStatusUpdated(result.getStatusUpdate());
            } catch (Exception e) {
                // Não quebrar o fluxo de pagamento caso a persistência falhe.
            }
        }
        return result != null ? result.getResponse() : null;
    }
}
