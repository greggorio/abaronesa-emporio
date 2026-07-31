package com.baronesa.emporio.service.payment.gateway;

import com.baronesa.emporio.service.payment.api.CardPaymentRequest;
import com.baronesa.emporio.service.payment.api.PaymentGatewayResult;
import com.baronesa.emporio.service.payment.api.PixPaymentRequest;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;

public interface PaymentGateway {

    PaymentGatewayType gateway();

    PaymentGatewayResult createCardPayment(CardPaymentRequest request);

    /**
     * Implementações podem sobrescrever para suportar PIX.
     * Default: lança UnsupportedOperationException.
     */
    default PaymentGatewayResult createPixPayment(PixPaymentRequest request) {
        throw new UnsupportedOperationException("PIX não suportado para " + gateway());
    }
}
