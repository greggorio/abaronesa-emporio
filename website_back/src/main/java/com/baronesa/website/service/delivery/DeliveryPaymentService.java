package com.baronesa.website.service.delivery;

import com.baronesa.website.dto.delivery.CreateDeliveryPaymentIntentRequest;
import com.baronesa.website.dto.delivery.CreateDeliveryPaymentIntentResponse;
import com.baronesa.website.dto.delivery.CreateDeliveryQuoteRequest;
import com.baronesa.website.dto.delivery.CreateDeliveryQuoteResponse;
import com.baronesa.website.dto.delivery.DeliveryPaymentWebhookRequest;
import com.baronesa.website.entity.delivery.DeliveryPayment;
import com.baronesa.website.entity.delivery.DeliveryOrder;
import com.baronesa.website.enums.delivery.DeliveryPaymentStatus;
import com.baronesa.website.enums.delivery.FulfillmentMode;
import com.baronesa.website.repository.delivery.DeliveryPaymentRepository;
import com.baronesa.website.repository.delivery.DeliveryOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryPaymentService {

    private final DeliveryPaymentRepository paymentRepository;
    private final DeliveryOrderRepository orderRepository;
    private final DeliveryOrderCalculator orderCalculator;
    private final UberDirectClient uberDirectClient;
    private final UberConfigService uberConfigService;
    private final DeliveryOrderService orderService;

    @Transactional
    public CreateDeliveryPaymentIntentResponse createPaymentIntent(CreateDeliveryPaymentIntentRequest request) {
        FulfillmentMode serviceMode = FulfillmentMode.from(request.serviceMode());

        if (serviceMode == FulfillmentMode.DELIVERY && !StringUtils.hasText(request.dropoffAddress())) {
            throw new IllegalArgumentException("Endereco de entrega nao informado");
        }

        var calculation = orderCalculator.calculate(request.items());
        int feeCents = 0;
        String currency = null;
        String quoteId = null;

        if (serviceMode == FulfillmentMode.DELIVERY) {
            var quoteRequest = buildUberRequest(
                    request.customerName(),
                    request.customerPhone(),
                    request.dropoffAddress(),
                    request.dropoffNotes(),
                    calculation
            );
            var quoteResponse = uberDirectClient.createQuote(quoteRequest);
            feeCents = quoteResponse == null || quoteResponse.getFee() == null ? 0 : quoteResponse.getFee();
            currency = quoteResponse == null ? null : quoteResponse.getCurrency();
            quoteId = quoteResponse == null ? null : quoteResponse.getId();
        }
        int totalCents = calculation.totalCents() + feeCents;

        DeliveryPayment payment = new DeliveryPayment();
        payment.setAmountCents(totalCents);
        payment.setFeeCents(feeCents);
        payment.setCurrency(currency);
        payment.setQuoteId(quoteId);
        payment.setStatus(DeliveryPaymentStatus.pending);
        payment.setQrPayload("PAYMENT:" + System.currentTimeMillis());
        payment.setFulfillmentMode(serviceMode);

        paymentRepository.save(payment);

        orderService.createDraft(request, payment);

        return new CreateDeliveryPaymentIntentResponse(
                payment.getId(),
                payment.getStatus().name(),
                payment.getAmountCents(),
                payment.getFeeCents(),
                payment.getCurrency(),
                payment.getQuoteId(),
                payment.getQrPayload(),
                payment.getFulfillmentMode()
        );
    }

    @Transactional(readOnly = true)
    public CreateDeliveryQuoteResponse createQuote(CreateDeliveryQuoteRequest request) {
        FulfillmentMode serviceMode = FulfillmentMode.from(request.serviceMode());

        if (serviceMode == FulfillmentMode.DELIVERY && !StringUtils.hasText(request.dropoffAddress())) {
            throw new IllegalArgumentException("Endereco de entrega nao informado");
        }

        var calculation = orderCalculator.calculate(request.items());

        if (serviceMode == FulfillmentMode.PICKUP) {
            return new CreateDeliveryQuoteResponse(
                    null,
                    0,
                    null,
                    null,
                    serviceMode
            );
        }

        var quoteRequest = buildUberRequest(
                request.customerName(),
                request.customerPhone(),
                request.dropoffAddress(),
                request.dropoffNotes(),
                calculation
        );
        var quoteResponse = uberDirectClient.createQuote(quoteRequest);
        if (quoteResponse == null) {
            throw new IllegalStateException("Falha ao obter quote da Uber");
        }
        return new CreateDeliveryQuoteResponse(
                quoteResponse.getId(),
                quoteResponse.getFee(),
                quoteResponse.getCurrency(),
                quoteResponse.getExpiresAt(),
                serviceMode
        );
    }

    @Transactional
    public void handleWebhook(DeliveryPaymentWebhookRequest request) {
        DeliveryPayment payment = paymentRepository.findById(request.paymentId())
                .orElseThrow(() -> new IllegalArgumentException("Pagamento não encontrado"));

        payment.setProviderReference(request.referenciaProvedor());

        if ("payment.paid".equalsIgnoreCase(request.evento())) {
            payment.setStatus(DeliveryPaymentStatus.paid);
        } else if ("payment.failed".equalsIgnoreCase(request.evento())) {
            payment.setStatus(DeliveryPaymentStatus.failed);
        }

        paymentRepository.save(payment);
        log.info("Pagamento delivery atualizado id={} status={}", payment.getId(), payment.getStatus());

        if (payment.getStatus() == DeliveryPaymentStatus.paid) {
            DeliveryOrder order = orderRepository.findByPaymentId(payment.getId()).orElse(null);
            if (order != null) {
                orderService.publishOrderToKds(order);
            } else {
                log.warn("Pedido delivery não encontrado para pagamento id={}", payment.getId());
            }
        }
    }

    private com.baronesa.website.dto.uber.UberDeliveryRequest buildUberRequest(
            String customerName,
            String customerPhone,
            String dropoffAddress,
            String dropoffNotes,
            DeliveryOrderCalculator.CalculationResult calculation
    ) {
        var config = uberConfigService.getConfig();
        return com.baronesa.website.dto.uber.UberDeliveryRequest.builder()
                .pickupAddress(config.pickupAddress())
                .pickupName(config.pickupName())
                .pickupPhoneNumber(config.pickupPhone())
                .pickupNotes(config.pickupNotes())
                .dropoffAddress(dropoffAddress)
                .dropoffName(customerName)
                .dropoffPhoneNumber(customerPhone)
                .dropoffNotes(dropoffNotes)
                .deliverableAction("deliverable_action_meet_at_door")
                .manifestItems(calculation.items().stream()
                        .map(item -> com.baronesa.website.dto.uber.UberDeliveryRequest.ManifestItem.builder()
                                .name(item.nome())
                                .quantity(item.quantidade())
                                .size(item.size() == null ? "small" : item.size())
                                .mustBeUpright(false)
                                .build())
                        .toList())
                .build();
    }
}
