package com.baronesa.emporio.service.payment.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baronesa.emporio.dto.mercadopago.MercadoPagoCustomer;
import com.baronesa.emporio.dto.mercadopago.MercadoPagoPaymentRequest;
import com.baronesa.emporio.dto.mercadopago.MercadoPagoPaymentResponse;
import com.baronesa.emporio.service.MercadoPagoService;
import com.baronesa.emporio.service.payment.api.CardPaymentRequest;
import com.baronesa.emporio.service.payment.api.PaymentGatewayResult;
import com.baronesa.emporio.service.payment.api.PaymentResponse;
import com.baronesa.emporio.service.payment.api.PixPaymentRequest;
import com.baronesa.emporio.service.payment.mapper.MercadoPagoStatusMapper;
import com.baronesa.emporio.service.payment.model.NormalizedPaymentStatus;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import com.baronesa.emporio.service.payment.PaymentFriendlyMessageProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class MercadoPagoPaymentGateway implements PaymentGateway {

    private final MercadoPagoService mercadoPagoService;
    private final MercadoPagoStatusMapper mercadoPagoStatusMapper;
    private final ObjectMapper objectMapper;
    private final PaymentFriendlyMessageProvider friendlyMessageProvider;

    @Override
    public PaymentGatewayType gateway() {
        return PaymentGatewayType.MERCADOPAGO;
    }

    @Override
    public PaymentGatewayResult createCardPayment(CardPaymentRequest request) {
        try {
            MercadoPagoPaymentRequest mpReq = toMpRequest(request);
            MercadoPagoPaymentResponse resp = mercadoPagoService.createPayment(mpReq);
            Object rawPayload = resp.getMetadata() != null ? resp.getMetadata().get("raw") : resp;
            String raw = objectMapper.writeValueAsString(rawPayload);
            PaymentStatusUpdate update = mercadoPagoStatusMapper.fromProviderPayload(
                    resp.getId(),
                    resp.getStatus(),
                    raw
            );
            if (update != null) {
                update.setAmount(request.getAmount());
                if (!StringUtils.hasText(update.getExternalReference())) {
                    update.setExternalReference(request.getExternalReference());
                }
            }
            String friendlyMessage = friendlyMessageProvider.resolve(resp.getStatusDetail(), raw);
            PaymentResponse response = PaymentResponse.builder()
                    .gateway(gateway())
                    .status(update != null ? update.getNormalizedStatus() : NormalizedPaymentStatus.PENDING)
                    .providerPaymentId(resp.getId())
                    .message(resp.getStatusDetail())
                    .friendlyMessage(friendlyMessage)
                    .pixQrCode(resp.getPixQrCode())
                    .pixQrCodeBase64(resp.getPixQrCodeBase64())
                    .expiresAt(resp.getDateOfExpiration() != null ? resp.getDateOfExpiration().toInstant() : null)
                    .build();
            return PaymentGatewayResult.builder()
                    .response(response)
                    .statusUpdate(update)
                    .build();
        } catch (Exception e) {
            log.error("Erro ao processar pagamento via Mercado Pago", e);
            String friendlyMessage = friendlyMessageProvider.resolve(e.getMessage(), null);
            PaymentResponse response = PaymentResponse.builder()
                    .gateway(gateway())
                    .status(NormalizedPaymentStatus.FAILED)
                    .providerPaymentId(null)
                    .message("Erro ao processar pagamento: " + e.getMessage())
                    .friendlyMessage(friendlyMessage)
                    .build();
            return PaymentGatewayResult.builder()
                    .response(response)
                    .statusUpdate(null)
                    .build();
        }
    }

    private MercadoPagoPaymentRequest toMpRequest(CardPaymentRequest request) {
        BigDecimal amount = request.getAmount();
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount obrigatório");
        }
        MercadoPagoPaymentRequest mpReq = new MercadoPagoPaymentRequest();
        mpReq.setAmount(amount);
        mpReq.setPaymentMethodId(StringUtils.hasText(request.getPaymentMethodId()) ? request.getPaymentMethodId() : "credit_card");
        mpReq.setInstallments(request.getInstallments() != null ? request.getInstallments() : 1);
        mpReq.setToken(request.getToken());
        mpReq.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription() : "Pagamento");
        mpReq.setExternalReference(request.getExternalReference());

        MercadoPagoCustomer cust = new MercadoPagoCustomer();
        cust.setEmail(request.getPayerEmail());
        cust.setName(request.getPayerName());
        mpReq.setCustomer(cust);

        return mpReq;
    }

    @Override
    public PaymentGatewayResult createPixPayment(PixPaymentRequest request) {
        try {
            MercadoPagoPaymentRequest mpReq = new MercadoPagoPaymentRequest();
            mpReq.setAmount(request.getAmount());
            mpReq.setPaymentMethodId("pix");
            mpReq.setInstallments(1);
            mpReq.setToken(null);
            mpReq.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription() : "Pagamento PIX");
            mpReq.setExternalReference(request.getExternalReference());

            MercadoPagoCustomer cust = new MercadoPagoCustomer();
            cust.setEmail(request.getPayerEmail());
            cust.setName(request.getPayerName());
            cust.setCpf(request.getPayerTaxId());
            mpReq.setCustomer(cust);

            MercadoPagoPaymentResponse resp = mercadoPagoService.createPayment(mpReq);
            Object rawPayload = resp.getMetadata() != null ? resp.getMetadata().get("raw") : resp;
            String raw = objectMapper.writeValueAsString(rawPayload);
            PaymentStatusUpdate update = mercadoPagoStatusMapper.fromProviderPayload(
                    resp.getId(),
                    resp.getStatus(),
                    raw
            );
            if (update != null) {
                update.setAmount(request.getAmount());
                if (!StringUtils.hasText(update.getExternalReference())) {
                    update.setExternalReference(request.getExternalReference());
                }
            }
            String friendlyMessage = friendlyMessageProvider.resolve(resp.getStatusDetail(), raw);
            PaymentResponse response = PaymentResponse.builder()
                    .gateway(gateway())
                    .status(update != null ? update.getNormalizedStatus() : NormalizedPaymentStatus.PENDING)
                    .providerPaymentId(resp.getId())
                    .message(resp.getStatusDetail())
                    .friendlyMessage(friendlyMessage)
                    .pixQrCode(resp.getPixQrCode())
                    .pixQrCodeBase64(resp.getPixQrCodeBase64())
                    .expiresAt(resp.getDateOfExpiration() != null ? resp.getDateOfExpiration().toInstant() : null)
                    .build();
            return PaymentGatewayResult.builder()
                    .response(response)
                    .statusUpdate(update)
                    .build();
        } catch (Exception e) {
            log.error("Erro ao processar pagamento PIX via Mercado Pago", e);
            String friendlyMessage = friendlyMessageProvider.resolve(e.getMessage(), null);
            PaymentResponse response = PaymentResponse.builder()
                    .gateway(gateway())
                    .status(NormalizedPaymentStatus.FAILED)
                    .providerPaymentId(null)
                    .message("Erro ao processar pagamento PIX: " + e.getMessage())
                    .friendlyMessage(friendlyMessage)
                    .build();
            return PaymentGatewayResult.builder()
                    .response(response)
                    .statusUpdate(null)
                    .build();
        }
    }
}
