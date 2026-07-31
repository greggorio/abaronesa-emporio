package com.baronesa.emporio.service.payment.mapper;

import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;

public interface PaymentStatusMapper {

    PaymentGatewayType gateway();

    PaymentStatusUpdate fromProviderPayload(String providerPaymentId, String providerStatus, String rawPayload);
}
