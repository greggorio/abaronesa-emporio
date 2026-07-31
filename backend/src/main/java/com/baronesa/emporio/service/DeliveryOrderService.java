package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.delivery.DeliveryCardPaymentRequest;
import com.baronesa.emporio.dto.delivery.DeliveryOrderRequest;
import com.baronesa.emporio.dto.delivery.DeliveryOrderItemView;
import com.baronesa.emporio.dto.delivery.DeliveryOrderView;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.enums.DeliveryOrderItemStatus;
import com.baronesa.emporio.enums.DeliveryOrderStatus;
import com.baronesa.emporio.enums.LocalPreparacao;
import com.baronesa.emporio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryOrderService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final DeliveryOrderItemRepository deliveryOrderItemRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoSKURepository produtoSKURepository;
    private final CardapioService cardapioService;
    private final MercadoPagoCardService mercadoPagoCardService;
    private final DeliveryKdsService deliveryKdsService;
    private final com.baronesa.emporio.events.SseEventsService eventsService;

    @Transactional
    public DeliveryOrder createOrder(DeliveryOrderRequest request, Long clienteId) {
        DeliveryOrder order = DeliveryOrder.builder()
                .tipo(request.getTipo())
                .status(DeliveryOrderStatus.PENDING_PAYMENT)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .customerEmail(request.getCustomerEmail())
                .customerCpf(request.getCustomerCpf())
                .dropoffAddress(request.getDropoffAddress())
                .dropoffNotes(request.getDropoffNotes())
                .deliveryFeeCents(safeCents(request.getDeliveryFeeCents()))
                .currency("BRL")
                .clienteId(clienteId)
                .build();

        order = deliveryOrderRepository.save(order);

        int itemsTotal = 0;
        for (DeliveryOrderRequest.Item it : request.getItems()) {
            Produto produto = produtoRepository.findById(it.getProdutoId())
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + it.getProdutoId()));

            ProdutoSKU sku = null;
            if (it.getSkuId() != null) {
                sku = produtoSKURepository.findById(it.getSkuId())
                        .orElseThrow(() -> new RuntimeException("SKU não encontrado: " + it.getSkuId()));
            }

            BigDecimal preco = cardapioService.calcularPrecoAtualParaPedido(produto, sku, null);
            int precoCents = toCents(preco);
            int qtd = it.getQuantidade() != null && it.getQuantidade() > 0 ? it.getQuantidade() : 1;
            itemsTotal += precoCents * qtd;

            DeliveryOrderItem item = DeliveryOrderItem.builder()
                    .deliveryOrder(order)
                    .produtoId(produto.getId())
                    .skuId(sku != null ? sku.getId() : null)
                    .nome(produto.getNome())
                    .variacao(sku != null ? sku.getVariacao() : null)
                    .quantidade(qtd)
                    .precoUnitCents(precoCents)
                    .observacoes(it.getObservacoes())
                    .estacao(resolveEstacao(produto))
                    .status(DeliveryOrderItemStatus.QUEUED)
                    .build();
            deliveryOrderItemRepository.save(item);
        }

        order.setItemsTotalCents(itemsTotal);
        order.setTotalCents(itemsTotal + safeCents(request.getDeliveryFeeCents()));
        order.setExternalReference(String.valueOf(order.getId()));
        order.setExternalId("delivery-" + order.getId());
        return deliveryOrderRepository.save(order);
    }

    @Transactional
    public DeliveryOrder payWithCard(Long orderId, DeliveryCardPaymentRequest request) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery order não encontrado"));

        BigDecimal amount = BigDecimal.valueOf(order.getTotalCents(), 2);

        com.baronesa.emporio.dto.MercadoPagoCardRequest mpReq = new com.baronesa.emporio.dto.MercadoPagoCardRequest();
        mpReq.setAmount(amount.doubleValue());
        mpReq.setToken(request.getToken());
        mpReq.setInstallments(request.getInstallments());
        mpReq.setPaymentMethodId(request.getPaymentMethodId());
        mpReq.setDescription(request.getDescription() != null ? request.getDescription() : "Pedido delivery #" + order.getId());
        mpReq.setExternalReference(order.getExternalReference());

        com.baronesa.emporio.dto.MercadoPagoCardRequest.CustomerData cust = new com.baronesa.emporio.dto.MercadoPagoCardRequest.CustomerData();
        cust.setEmail(order.getCustomerEmail() != null ? order.getCustomerEmail() : "cliente@example.com");
        cust.setName(order.getCustomerName() != null ? order.getCustomerName() : "Cliente");
        cust.setCpf(order.getCustomerCpf());
        cust.setPhone(order.getCustomerPhone());
        mpReq.setCustomer(cust);

        var response = mercadoPagoCardService.processCardPayment(mpReq);

        if (Boolean.TRUE.equals(response.get("success"))
                && "approved".equalsIgnoreCase(String.valueOf(response.get("status")))) {
            order.setStatus(DeliveryOrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            publishDeliveryItemsToKds(order);
        }

        order.setMpPaymentId(String.valueOf(response.get("id")));
        order.setMpStatus(String.valueOf(response.get("status")));
        order.setMpStatusDetail(String.valueOf(response.get("status_detail")));
        order.setMpPaymentMethod(String.valueOf(response.get("payment_method")));
        order.setMpRawResponse(safeJson(response));

        return deliveryOrderRepository.save(order);
    }

    private void publishDeliveryItemsToKds(DeliveryOrder order) {
        if (order == null) return;
        var items = deliveryOrderItemRepository.findByDeliveryOrderId(order.getId());
        if (items == null || items.isEmpty()) return;
        items.forEach(item -> {
            try {
                var payload = deliveryKdsService.toKdsTicket(item);
                eventsService.publishKds("kds.new_item", payload);
            } catch (Exception e) {
                log.warn("Falha ao publicar item delivery no KDS itemId={}", item.getId(), e);
            }
        });
    }

    @Transactional
    public DeliveryOrder updateOrder(Long orderId, DeliveryOrderRequest request, Long clienteId) {
        DeliveryOrder order = deliveryOrderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery order não encontrado"));

        if (order.getStatus() != null && order.getStatus() != DeliveryOrderStatus.PENDING_PAYMENT) {
            throw new RuntimeException("Pedido não pode ser alterado neste status");
        }

        order.setTipo(request.getTipo());
        order.setCustomerName(request.getCustomerName());
        order.setCustomerPhone(request.getCustomerPhone());
        order.setCustomerEmail(request.getCustomerEmail());
        order.setCustomerCpf(request.getCustomerCpf());
        order.setDropoffAddress(request.getDropoffAddress());
        order.setDropoffNotes(request.getDropoffNotes());
        order.setDeliveryFeeCents(safeCents(request.getDeliveryFeeCents()));
        if (clienteId != null) {
            order.setClienteId(clienteId);
        }

        if (order.getItens() != null && !order.getItens().isEmpty()) {
            deliveryOrderItemRepository.deleteAll(order.getItens());
            order.getItens().clear();
        }

        int itemsTotal = 0;
        for (DeliveryOrderRequest.Item it : request.getItems()) {
            Produto produto = produtoRepository.findById(it.getProdutoId())
                    .orElseThrow(() -> new RuntimeException("Produto não encontrado: " + it.getProdutoId()));

            ProdutoSKU sku = null;
            if (it.getSkuId() != null) {
                sku = produtoSKURepository.findById(it.getSkuId())
                        .orElseThrow(() -> new RuntimeException("SKU não encontrado: " + it.getSkuId()));
            }

            BigDecimal preco = cardapioService.calcularPrecoAtualParaPedido(produto, sku, null);
            int precoCents = toCents(preco);
            int qtd = it.getQuantidade() != null && it.getQuantidade() > 0 ? it.getQuantidade() : 1;
            itemsTotal += precoCents * qtd;

            DeliveryOrderItem item = DeliveryOrderItem.builder()
                    .deliveryOrder(order)
                    .produtoId(produto.getId())
                    .skuId(sku != null ? sku.getId() : null)
                    .nome(produto.getNome())
                    .variacao(sku != null ? sku.getVariacao() : null)
                    .quantidade(qtd)
                    .precoUnitCents(precoCents)
                    .observacoes(it.getObservacoes())
                    .estacao(resolveEstacao(produto))
                    .status(DeliveryOrderItemStatus.QUEUED)
                    .build();
            deliveryOrderItemRepository.save(item);
            order.addItem(item);
        }

        order.setItemsTotalCents(itemsTotal);
        order.setTotalCents(itemsTotal + safeCents(request.getDeliveryFeeCents()));

        return deliveryOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Optional<DeliveryOrder> findActiveForUser(Long clienteId) {
        if (clienteId == null) return Optional.empty();
        List<DeliveryOrderStatus> ignored = List.of(DeliveryOrderStatus.CANCELED, DeliveryOrderStatus.EXPIRED);
        return deliveryOrderRepository.findTopByClienteIdAndStatusNotInOrderByCreatedAtDesc(clienteId, ignored)
                .flatMap(order -> deliveryOrderRepository.findWithItems(order.getId()));
    }

    @Transactional(readOnly = true)
    public Optional<DeliveryOrder> findWithItems(Long orderId) {
        if (orderId == null) return Optional.empty();
        return deliveryOrderRepository.findWithItems(orderId);
    }

    public DeliveryOrderView toView(DeliveryOrder order) {
        if (order == null) return null;
        List<DeliveryOrderItemView> items = order.getItens() != null
                ? order.getItens().stream()
                .map(it -> DeliveryOrderItemView.builder()
                        .nome(it.getNome())
                        .quantidade(it.getQuantidade())
                        .observacoes(it.getObservacoes())
                        .build())
                .toList()
                : List.of();

        return DeliveryOrderView.builder()
                .id(order.getId())
                .externalId(order.getExternalId() != null ? order.getExternalId() : order.getExternalReference())
                .deliveryId(order.getUberDeliveryId())
                .status(order.getStatus())
                .uberStatus(order.getUberStatus())
                .trackingUrl(order.getUberTrackingUrl())
                .dropoffEta(order.getUberDropoffEta())
                .pickupEta(order.getUberPickupEta())
                .dropoffAddress(order.getDropoffAddress())
                .pickupAddress(order.getUberPickupAddress())
                .updatedAt(order.getUpdatedAt())
                .items(items)
                .build();
    }

    private String resolveEstacao(Produto produto) {
        LocalPreparacao escolhido = produto != null ? produto.getLocalPreparacao() : null;
        if (escolhido == null) {
            return "kitchen";
        }
        return escolhido == LocalPreparacao.BAR ? "bar" : "kitchen";
    }

    private int toCents(BigDecimal valor) {
        if (valor == null) return 0;
        return valor.setScale(2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .intValue();
    }

    private int safeCents(Integer cents) {
        return cents != null ? cents : 0;
    }

    private String safeJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
