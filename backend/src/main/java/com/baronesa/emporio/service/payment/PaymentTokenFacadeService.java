package com.baronesa.emporio.service.payment;

import com.baronesa.emporio.service.payment.api.CardTokenRequest;
import com.baronesa.emporio.service.payment.api.CardTokenResponse;
import com.baronesa.emporio.service.payment.mercadopago.MercadoPagoTokenService;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.pagseguro.PagSeguroTokenClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentTokenFacadeService {

    private final PaymentSettingsService paymentSettingsService;
    private final MercadoPagoTokenService mercadoPagoTokenService;
    private final PagSeguroTokenClient pagSeguroTokenClient;

    public CardTokenResponse createToken(CardTokenRequest request) {
        PaymentGatewayType active = paymentSettingsService.getActiveGateway();
        if (active == PaymentGatewayType.PAGSEGURO) {
            return pagSeguroTokenClient.createCardToken(request);
        }
        // default Mercado Pago
        return mercadoPagoTokenService.createToken(request);
    }
}
