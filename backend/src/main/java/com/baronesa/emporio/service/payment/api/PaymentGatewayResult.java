package com.baronesa.emporio.service.payment.api;

import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentGatewayResult {
    private PaymentResponse response;
    private PaymentStatusUpdate statusUpdate;
}
