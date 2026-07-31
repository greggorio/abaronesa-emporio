package com.baronesa.emporio.controller;

import com.baronesa.emporio.dto.ContaMesaResponse;
import com.baronesa.emporio.dto.PagamentoWebhookRequest;
import com.baronesa.emporio.dto.SelfCheckoutPaymentRequest;
import com.baronesa.emporio.dto.SelfCheckoutPaymentResponse;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.enums.StatusPagamento;
import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.enums.TipoMovimentoCaixa;
import com.baronesa.emporio.enums.TipoOperacao;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.repository.SessaoConvidadoRepository;
import com.baronesa.emporio.repository.SessaoMesaRepository;
import com.baronesa.emporio.service.ContaService;
import com.baronesa.emporio.service.MovimentoCaixaService;
import com.baronesa.emporio.service.SessaoMesaService;
import com.baronesa.emporio.service.payment.PaymentFacadeService;
import com.baronesa.emporio.service.payment.api.CardPaymentRequest;
import com.baronesa.emporio.service.payment.api.PaymentResponse;
import com.baronesa.emporio.service.payment.api.PixPaymentRequest;
import com.baronesa.emporio.service.payment.model.NormalizedPaymentStatus;
import com.baronesa.emporio.service.payment.model.PaymentGatewayType;
import lombok.RequiredArgsConstructor;
import com.baronesa.emporio.events.SseEventsService;
import com.baronesa.emporio.util.ConfigManager;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/pagamentos")
@RequiredArgsConstructor
public class PagamentosController {

    private final SessaoMesaRepository sessaoMesaRepository;
    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final SseEventsService eventsService;
    private final MovimentoCaixaService movimentoCaixaService;
    private final ContaService contaService;
    private final SessaoMesaService sessaoMesaService;
    private final PaymentFacadeService paymentFacadeService;
    private final ConfigManager configManager;

    @PostMapping("/intent")
    public ResponseEntity<SelfCheckoutPaymentResponse> criarIntent(
            @RequestBody SelfCheckoutPaymentRequest req,
            @RequestHeader(name = "X-Guest-Token", required = false) String guestToken
    ) {
        if (req == null || !StringUtils.hasText(req.getEscopo())) {
            throw new IllegalArgumentException("escopo é obrigatório (convidado|mesa)");
        }
        if (!StringUtils.hasText(req.getMetodo())) {
            throw new IllegalArgumentException("metodo é obrigatório (pix|card)");
        }
        if (!StringUtils.hasText(guestToken)) {
            throw new IllegalArgumentException("Cabeçalho X-Guest-Token é obrigatório");
        }

        SessaoConvidado pagante = sessaoConvidadoRepository.findByGuestToken(guestToken)
                .orElseThrow(() -> new NotFoundException("Convidado não encontrado para o token informado"));

        Long sessaoMesaId = req.getSessaoMesaId();
        if (sessaoMesaId == null || !pagante.getSessaoMesa().getId().equals(sessaoMesaId)) {
            throw new IllegalArgumentException("sessaoMesaId inválido para o convidado informado");
        }

        SessaoMesa sessaoMesa = pagante.getSessaoMesa();
        if (sessaoMesa.getStatus() == com.baronesa.emporio.enums.StatusSessao.CLOSED) {
            throw new IllegalStateException("Sessão de mesa já encerrada");
        }

        boolean escopoMesa = "mesa".equalsIgnoreCase(req.getEscopo());
        boolean escopoConvidado = "convidado".equalsIgnoreCase(req.getEscopo());
        if (!escopoMesa && !escopoConvidado) {
            throw new IllegalArgumentException("escopo inválido (use convidado|mesa)");
        }

        // Permissão para pagar mesa inteira
        if (escopoMesa && !Boolean.TRUE.equals(pagante.getHost())) {
            boolean mesaEnabled = configManager.getBooleanConfig("mesa_self_checkout_mesa_enabled", false);
            if (!mesaEnabled) {
                throw new IllegalStateException("Somente o anfitrião pode pagar a conta da mesa");
            }
        }

        Long beneficiarioId = escopoMesa ? null : req.getSessaoConvidadoId();
        if (escopoConvidado) {
            if (beneficiarioId == null) {
                throw new IllegalArgumentException("sessaoConvidadoId é obrigatório para escopo convidado");
            }
            if (!beneficiarioId.equals(pagante.getId())) {
                throw new IllegalArgumentException("Convidado só pode pagar sua própria conta");
            }
        }

        // Calcular valor devido e quebra (base/taxa/couvert)
        ContaMesaResponse contaMesa = contaService.contaMesa(sessaoMesa.getId());
        long valorDevidoCentavos = calcularDevido(contaMesa, beneficiarioId);
        if (valorDevidoCentavos <= 0) {
            throw new IllegalStateException("Nada a pagar no momento");
        }
        PaymentBreakdown breakdown = calcularBreakdown(contaMesa, beneficiarioId, valorDevidoCentavos);

        // Montar descrição e external reference
        String mesaLabel = Optional.ofNullable(sessaoMesa.getMesa()).map(com.baronesa.emporio.entity.Mesa::getRotulo).orElse("Mesa");
        String description = escopoMesa
                ? "Conta da mesa " + mesaLabel
                : "Conta de " + Optional.ofNullable(pagante.getNomeExibicao()).orElse("convidado");
        String externalReference = "mesa:" + sessaoMesa.getId() + (escopoConvidado ? (":guest:" + pagante.getId()) : "");

        // Chamar gateway
        PaymentResponse pr;
        if ("pix".equalsIgnoreCase(req.getMetodo())) {
            PixPaymentRequest pix = new PixPaymentRequest();
            pix.setAmount(BigDecimal.valueOf(valorDevidoCentavos, 2));
            pix.setExternalReference(externalReference);
            pix.setDescription(description);
            pix.setPayerName(required(req.getPayerName(), "payerName é obrigatório"));
            pix.setPayerEmail(required(req.getPayerEmail(), "payerEmail é obrigatório"));
            pix.setPayerTaxId(req.getPayerTaxId());
            pr = paymentFacadeService.createPixPayment(pix);
        } else if ("card".equalsIgnoreCase(req.getMetodo())) {
            CardPaymentRequest card = new CardPaymentRequest();
            card.setAmount(BigDecimal.valueOf(valorDevidoCentavos, 2));
            card.setExternalReference(externalReference);
            card.setDescription(description);
            card.setToken(required(req.getCardToken(), "cardToken é obrigatório"));
            card.setPaymentMethodId(required(req.getPaymentMethodId(), "paymentMethodId é obrigatório"));
            card.setInstallments(req.getInstallments() != null ? req.getInstallments() : 1);
            card.setPayerName(required(req.getPayerName(), "payerName é obrigatório"));
            card.setPayerEmail(required(req.getPayerEmail(), "payerEmail é obrigatório"));
            card.setPayerTaxId(req.getPayerTaxId());
            pr = paymentFacadeService.createCardPayment(card);
        } else {
            throw new IllegalArgumentException("metodo inválido (pix|card)");
        }

        // Persistir pagamento local
        BigDecimal taxaPercentual = breakdown.taxaCentavos() > 0 ? resolveTaxaServicoPercentual() : null;
        Pagamento pagamento = Pagamento.builder()
                .sessaoMesa(sessaoMesa)
                .sessaoConvidado(escopoConvidado ? pagante : null)
                .pagante(pagante)
                .metodo(req.getMetodo().toLowerCase(Locale.ROOT))
                .status(mapStatus(pr != null ? pr.getStatus() : null))
                .valor(BigDecimal.valueOf(valorDevidoCentavos, 2))
                .valorBase(BigDecimal.valueOf(breakdown.baseCentavos(), 2))
                .valorTaxaServico(BigDecimal.valueOf(breakdown.taxaCentavos(), 2))
                .valorCouvert(BigDecimal.valueOf(breakdown.couvertCentavos(), 2))
                .percentualTaxaServico(taxaPercentual)
                .incluiTaxaServico(breakdown.taxaCentavos() > 0)
                .selfCheckoutOrigem("SELF_CHECKOUT")
                .selfCheckoutResolvido(Boolean.FALSE)
                .qrPayload(pr != null ? firstNonBlank(pr.getPixQrCode(), pr.getPixQrCodeBase64()) : null)
                .providerRef(pr != null ? pr.getProviderPaymentId() : null)
                .pagoEm(pr != null && pr.getStatus() == NormalizedPaymentStatus.PAID ? java.time.LocalDateTime.now() : null)
                .build();

        pagamento = pagamentoRepository.save(pagamento);

        SelfCheckoutPaymentResponse resp = SelfCheckoutPaymentResponse.builder()
                .pagamentoId(pagamento.getId())
                .gateway(pr != null ? pr.getGateway() : PaymentGatewayType.MERCADOPAGO) // default
                .status(pr != null ? pr.getStatus() : NormalizedPaymentStatus.PENDING)
                .providerPaymentId(pr != null ? pr.getProviderPaymentId() : null)
                .message(pr != null ? pr.getMessage() : null)
                .friendlyMessage(pr != null ? pr.getFriendlyMessage() : null)
                .pixQrCode(pr != null ? pr.getPixQrCode() : null)
                .pixQrCodeBase64(pr != null ? pr.getPixQrCodeBase64() : null)
                .expiresAt(toIso(pr != null ? pr.getExpiresAt() : null))
                .amountCentavos(valorDevidoCentavos)
                .build();

        try {
            eventsService.publishWaiter("payment.updated", buildWaiterPayload(pagamento));
        } catch (Exception ignored) {}

        if (pr != null && pr.getStatus() == NormalizedPaymentStatus.PAID) {
            tryCloseMesaSeQuitada(sessaoMesa.getId());
        }

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> webhook(@RequestBody PagamentoWebhookRequest req) {
        if (!"payment.paid".equalsIgnoreCase(req.evento())) {
            return ResponseEntity.ok().build();
        }
        Pagamento pagamento = pagamentoRepository.findById(req.pagamentoId())
                .orElseThrow(() -> new NotFoundException("Pagamento não encontrado"));
        pagamento.setStatus(StatusPagamento.PAID);
        pagamento.setProviderRef(req.referenciaProvedor());
        pagamento.setPagoEm(java.time.LocalDateTime.now());
        pagamentoRepository.save(pagamento);

        // Registrar movimento de caixa
        try {
            TipoFormaPagamento formaPagamento = mapearMetodoPagamento(pagamento.getMetodo(), pagamento.getCartaoTipo());
            movimentoCaixaService.registrar(
                TipoMovimentoCaixa.PAGAMENTO_MESA,
                pagamento.getValor(),
                formaPagamento,
                true, // afeta caixa
                "PAGAMENTO",
                pagamento.getId(),
                null, // sem responsável (pagamento automático via webhook)
                TipoOperacao.ENTRADA,
                "Pagamento de mesa #" + pagamento.getSessaoMesa().getMesa().getRotulo()
            );
        } catch (Exception e) {
            // Log do erro mas não falha o pagamento
            System.err.println("Erro ao registrar movimento de caixa: " + e.getMessage());
        }

        try {
            var payload = java.util.Map.of(
                    "pagamentoId", pagamento.getId(),
                    "status", pagamento.getStatus().name().toLowerCase()
            );
            eventsService.publish(pagamento.getSessaoMesa().getId(), "payment.updated", payload);
        } catch (Exception ignored) {}

        try {
            eventsService.publishWaiter("payment.updated", buildWaiterPayload(pagamento));
        } catch (Exception ignored) {}

        tryCloseMesaSeQuitada(pagamento.getSessaoMesa().getId());

        return ResponseEntity.ok(java.util.Map.of("success", true));
    }

    /**
     * Mapeia o método de pagamento (pix, card, cash) para TipoFormaPagamento, considerando o tipo de cartão.
     */
    private TipoFormaPagamento mapearMetodoPagamento(String metodo, String cartaoTipo) {
        if (metodo == null) return TipoFormaPagamento.OUTROS;

        return switch (metodo.toLowerCase(Locale.ROOT)) {
            case "pix" -> TipoFormaPagamento.PIX;
            case "card" -> isCartaoDebito(cartaoTipo) ? TipoFormaPagamento.CARTAO_DEBITO : TipoFormaPagamento.CARTAO_CREDITO;
            case "cash" -> TipoFormaPagamento.DINHEIRO;
            case "voucher" -> TipoFormaPagamento.VOUCHER;
            default -> TipoFormaPagamento.OUTROS;
        };
    }

    private boolean isCartaoDebito(String cartaoTipo) {
        if (cartaoTipo == null) return false;
        String normalized = cartaoTipo.toLowerCase(Locale.ROOT);
        return normalized.contains("debito") || normalized.contains("debit");
    }

    private long calcularDevido(ContaMesaResponse contaMesa, Long sessaoConvidadoId) {
        if (sessaoConvidadoId == null) {
            return contaMesa.devidoTotalCentavos();
        }
        return contaMesa.pessoas().stream()
                .filter(p -> sessaoConvidadoId.equals(p.sessaoConvidadoId()))
                .findFirst()
                .map(ContaMesaResponse.Pessoa::devidoTotalCentavos)
                .orElseThrow(() -> new NotFoundException("Convidado não encontrado na mesa"));
    }

    private PaymentBreakdown calcularBreakdown(ContaMesaResponse contaMesa, Long sessaoConvidadoId, long totalDevidoCentavos) {
        long base = 0;
        long taxa = 0;
        long couvert = 0;
        if (sessaoConvidadoId == null) {
            base = contaMesa.devidoCentavos();
            taxa = contaMesa.taxaServicoPendenteCentavos();
            couvert = contaMesa.devidoCouvertCentavos();
        } else {
            ContaMesaResponse.Pessoa pessoa = contaMesa.pessoas().stream()
                    .filter(p -> sessaoConvidadoId.equals(p.sessaoConvidadoId()))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("Convidado não encontrado na mesa"));
            base = pessoa.devidoCentavos();
            taxa = pessoa.taxaServicoPendenteCentavos();
            couvert = pessoa.devidoCouvertCentavos();
        }
        long soma = base + taxa + couvert;
        if (soma != totalDevidoCentavos) {
            base = Math.max(0, base + (totalDevidoCentavos - soma));
        }
        return new PaymentBreakdown(base, taxa, couvert);
    }

    private BigDecimal resolveTaxaServicoPercentual() {
        try {
            String valor = configManager.getConfig("taxa_servico_percentual", "");
            if (!StringUtils.hasText(valor)) return null;
            return new BigDecimal(valor.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private record PaymentBreakdown(long baseCentavos, long taxaCentavos, long couvertCentavos) {}

    private StatusPagamento mapStatus(NormalizedPaymentStatus status) {
        if (status == null) return StatusPagamento.PENDING;
        return switch (status) {
            case PAID -> StatusPagamento.PAID;
            case FAILED, CANCELED, EXPIRED -> StatusPagamento.FAILED;
            default -> StatusPagamento.PENDING;
        };
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (StringUtils.hasText(v)) return v;
        }
        return null;
    }

    private String toIso(Instant instant) {
        if (instant == null) return null;
        return instant.atOffset(ZoneOffset.UTC).toString();
    }

    private void tryCloseMesaSeQuitada(Long sessaoMesaId) {
        try {
            var conta = contaService.contaMesa(sessaoMesaId);
            if (conta.devidoTotalCentavos() == 0) {
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
            // mesa já encerrada
        } catch (Exception ignored) {
            // não bloquear o fluxo do pagamento
        }
    }

    private java.util.Map<String, Object> buildWaiterPayload(Pagamento pagamento) {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("pagamentoId", pagamento.getId());
        payload.put("status", pagamento.getStatus().name().toLowerCase());
        payload.put("metodo", pagamento.getMetodo());
        payload.put("valor", pagamento.getValor());
        payload.put("criadoEm", pagamento.getCriadoEm());
        payload.put("pagoEm", pagamento.getPagoEm());
        if (pagamento.getSessaoMesa() != null) {
            payload.put("sessaoMesaId", pagamento.getSessaoMesa().getId());
            if (pagamento.getSessaoMesa().getMesa() != null) {
                payload.put("mesaSlug", pagamento.getSessaoMesa().getMesa().getSlug());
                payload.put("mesaRotulo", pagamento.getSessaoMesa().getMesa().getRotulo());
            }
        }
        if (pagamento.getSessaoConvidado() != null) {
            payload.put("sessaoConvidadoId", pagamento.getSessaoConvidado().getId());
            payload.put("convidado", pagamento.getSessaoConvidado().getNomeExibicao());
        }
        if (pagamento.getPagante() != null) {
            payload.put("pagante", pagamento.getPagante().getNomeExibicao());
        }
        return payload;
    }
}
