package com.baronesa.emporio.service.payment;

import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import com.baronesa.emporio.service.payment.model.PaymentStatusUpdate;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.enums.StatusPagamento;
import com.baronesa.emporio.events.SseEventsService;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.repository.SessaoConvidadoRepository;
import com.baronesa.emporio.repository.SessaoMesaRepository;
import com.baronesa.emporio.service.ContaService;
import com.baronesa.emporio.service.SessaoMesaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DeliveryPaymentStatusUpdater implements PaymentStatusUpdater {

    private final DeliveryOrderPaymentSync sync;
    private final DeliveryPaidSideEffects sideEffects;
    private final PaymentService paymentService;
    private final PagamentoRepository pagamentoRepository;
    private final SessaoMesaRepository sessaoMesaRepository;
    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final ContaService contaService;
    private final SessaoMesaService sessaoMesaService;
    private final SseEventsService eventsService;

    @Override
    public void onPaymentStatusUpdated(String providerPaymentId, String status, String rawPayload) {
        PaymentSyncResult result = sync.sync(PaymentGatewayType.MERCADOPAGO, providerPaymentId, status, rawPayload);
        if (result.getOrder() == null) {
            return;
        }
        if (result.isBecamePaid()) {
            sideEffects.handlePaid(result.getOrder());
        }
    }

    @Override
    public void onPaymentStatusUpdated(PaymentStatusUpdate update) {
        if (update == null) {
            return;
        }
        try {
            paymentService.upsertFromStatusUpdate(update);
        } catch (Exception e) {
            log.warn("Falha ao persistir payment update gateway={} providerId={}", update.getGateway(), update.getProviderPaymentId(), e);
        }
        try {
            handleSelfCheckout(update);
        } catch (Exception e) {
            log.warn("Falha ao processar self-checkout para externalRef={} providerId={}", update.getExternalReference(), update.getProviderPaymentId(), e);
        }
        syncPayment(update);
    }

    private void syncPayment(PaymentStatusUpdate update) {
        PaymentSyncResult result = sync.sync(update.getGateway(), update.getProviderPaymentId(), update.getProviderStatus(), update.getRawPayload());
        if (result.getOrder() == null) {
            return;
        }
        if (result.isBecamePaid()) {
            sideEffects.handlePaid(result.getOrder());
        }
    }

    private void handleSelfCheckout(PaymentStatusUpdate update) {
        String ref = update.getExternalReference();
        if (!StringUtils.hasText(ref) || !ref.toLowerCase().startsWith("mesa:")) {
            return;
        }
        Long mesaId = parseMesaId(ref);
        Long guestId = parseGuestId(ref);
        if (mesaId == null) {
            return;
        }

        Pagamento pagamento = null;
        if (StringUtils.hasText(update.getProviderPaymentId())) {
            pagamento = pagamentoRepository.findFirstByProviderRef(update.getProviderPaymentId()).orElse(null);
        }
        if (pagamento == null) {
            SessaoMesa sessaoMesa = sessaoMesaRepository.findById(mesaId).orElse(null);
            if (sessaoMesa != null) {
                if (guestId != null) {
                    SessaoConvidado convidado = sessaoConvidadoRepository.findById(guestId).orElse(null);
                    if (convidado != null) {
                        pagamento = pagamentoRepository.findFirstBySessaoConvidadoAndStatusOrderByIdDesc(convidado, StatusPagamento.PENDING).orElse(null);
                    }
                }
                if (pagamento == null) {
                    pagamento = pagamentoRepository.findFirstBySessaoMesaAndStatusOrderByIdDesc(sessaoMesa, StatusPagamento.PENDING).orElse(null);
                }
            }
        }
        if (pagamento == null) {
            log.debug("Nenhum pagamento local encontrado para externalRef={} providerId={}", ref, update.getProviderPaymentId());
            return;
        }

        StatusPagamento novoStatus = mapStatus(update.getNormalizedStatus());
        pagamento.setProviderRef(StringUtils.hasText(update.getProviderPaymentId()) ? update.getProviderPaymentId() : pagamento.getProviderRef());
        pagamento.setStatus(novoStatus);
        if (novoStatus == StatusPagamento.PAID) {
            pagamento.setPagoEm(java.time.LocalDateTime.now());
        }
        pagamentoRepository.save(pagamento);

        try {
            eventsService.publish(pagamento.getSessaoMesa().getId(), "payment.updated", java.util.Map.of(
                    "pagamentoId", pagamento.getId(),
                    "status", pagamento.getStatus().name().toLowerCase()
            ));
        } catch (Exception e) {
            log.warn("Falha ao publicar payment.updated mesaId={} pagamentoId={}", pagamento.getSessaoMesa().getId(), pagamento.getId(), e);
        }
        try {
            java.util.Map<String, Object> waiterPayload = new java.util.LinkedHashMap<>();
            waiterPayload.put("pagamentoId", pagamento.getId());
            waiterPayload.put("status", pagamento.getStatus().name().toLowerCase());
            waiterPayload.put("metodo", pagamento.getMetodo());
            waiterPayload.put("valor", pagamento.getValor());
            waiterPayload.put("criadoEm", pagamento.getCriadoEm());
            waiterPayload.put("pagoEm", pagamento.getPagoEm());
            waiterPayload.put("sessaoMesaId", pagamento.getSessaoMesa().getId());
            waiterPayload.put("mesaSlug", pagamento.getSessaoMesa().getMesa() != null ? pagamento.getSessaoMesa().getMesa().getSlug() : null);
            waiterPayload.put("mesaRotulo", pagamento.getSessaoMesa().getMesa() != null ? pagamento.getSessaoMesa().getMesa().getRotulo() : null);
            waiterPayload.put("sessaoConvidadoId", pagamento.getSessaoConvidado() != null ? pagamento.getSessaoConvidado().getId() : null);
            waiterPayload.put("convidado", pagamento.getSessaoConvidado() != null ? pagamento.getSessaoConvidado().getNomeExibicao() : null);
            eventsService.publishWaiter("payment.updated", waiterPayload);
        } catch (Exception ignored) {}

        if (novoStatus == StatusPagamento.PAID) {
            tryCloseMesaSeQuitada(pagamento.getSessaoMesa().getId());
        }
    }

    private void tryCloseMesaSeQuitada(Long sessaoMesaId) {
        try {
            var conta = contaService.contaMesa(sessaoMesaId);
            if (conta.devidoCentavos() == 0) {
                sessaoMesaService.fecharSessao(sessaoMesaId);
                try {
                    eventsService.publish(sessaoMesaId, "table.closed", java.util.Map.of(
                            "status", "closed",
                            "sessaoMesaId", sessaoMesaId,
                            "fechadaEm", java.time.LocalDateTime.now().toString()
                    ));
                } catch (Exception ignored) {}
            }
        } catch (com.baronesa.emporio.exception.SessionClosedException ignored) {
            // Mesa já encerrada; não tratar como erro.
        } catch (Exception e) {
            log.warn("Falha ao tentar fechar mesa {} apos pagamento", sessaoMesaId, e);
        }
    }

    private Long parseMesaId(String ref) {
        try {
            String[] parts = ref.split(":");
            if (parts.length >= 2) {
                return Long.parseLong(parts[1]);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Long parseGuestId(String ref) {
        if (ref == null) return null;
        int idx = ref.indexOf(":guest:");
        if (idx < 0) return null;
        try {
            return Long.parseLong(ref.substring(idx + 7));
        } catch (Exception ignored) {
            return null;
        }
    }

    private StatusPagamento mapStatus(com.baronesa.emporio.service.payment.model.NormalizedPaymentStatus status) {
        if (status == null) return StatusPagamento.PENDING;
        return switch (status) {
            case PAID -> StatusPagamento.PAID;
            case FAILED, CANCELED, EXPIRED -> StatusPagamento.FAILED;
            default -> StatusPagamento.PENDING;
        };
    }
}
