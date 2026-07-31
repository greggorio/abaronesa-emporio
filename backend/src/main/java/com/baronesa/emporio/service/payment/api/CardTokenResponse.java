package com.baronesa.emporio.service.payment.api;

import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class CardTokenResponse {
    private boolean success;
    private PaymentGatewayType gateway;
    private String token;
    private Map<String, Object> cardInfo;
    private String error;
}
