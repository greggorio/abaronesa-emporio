package com.baronesa.emporio.service.payment.gateway;

import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PaymentGatewayRegistry {

    private final List<PaymentGateway> gateways;

    public PaymentGatewayRegistry(List<PaymentGateway> gateways) {
        this.gateways = gateways;
    }

    public PaymentGateway resolve(PaymentGatewayType type) {
        return gateways.stream()
                .filter(g -> g.gateway() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Gateway não registrado: " + type));
    }
}
