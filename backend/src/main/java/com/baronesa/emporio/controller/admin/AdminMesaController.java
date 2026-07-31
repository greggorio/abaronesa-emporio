package com.baronesa.emporio.controller.admin;

import com.baronesa.emporio.dto.ContaMesaResponse;
import com.baronesa.emporio.dto.PagamentoHistoricoResponse;
import com.baronesa.emporio.dto.MesaAdminOptionDTO;
import com.baronesa.emporio.dto.RegistrarPagamentoRequest;
import com.baronesa.emporio.dto.RegistrarPagamentosMultiplosRequest;
import com.baronesa.emporio.dto.ContaReceberRequest;
import com.baronesa.emporio.dto.ContaReceberParcelaRequest;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.entity.SessaoCobranca;
import com.baronesa.emporio.entity.TipoReceita;
import com.baronesa.emporio.enums.StatusSessao;
import com.baronesa.emporio.enums.StatusCobranca;
import com.baronesa.emporio.enums.TipoCobranca;
import com.baronesa.emporio.repository.SessaoConvidadoRepository;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.repository.TipoReceitaRepository;
import com.baronesa.emporio.entity.Pagamento;
import com.baronesa.emporio.enums.StatusPagamento;
import com.baronesa.emporio.repository.SessaoMesaRepository;
import com.baronesa.emporio.repository.SessaoCobrancaRepository;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import com.baronesa.emporio.service.ContaService;
import com.baronesa.emporio.service.ContaReceberService;
import com.baronesa.emporio.service.SessaoMesaService;
import com.baronesa.emporio.service.MovimentoCaixaService;
import com.baronesa.emporio.service.EventoEspressoService;
import com.baronesa.emporio.service.PedidoService;
import com.baronesa.emporio.util.ConfigManager;
import com.baronesa.emporio.enums.TipoFormaPagamento;
import com.baronesa.emporio.enums.TipoMovimentoCaixa;
import com.baronesa.emporio.enums.TipoOperacao;
import com.baronesa.emporio.entity.ItemPedido;
import com.baronesa.emporio.enums.StatusItem;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import com.baronesa.emporio.events.SseEventsService;
import com.baronesa.emporio.exception.NotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/mesas")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('ADMIN','SYSTEM','WAITER','CAIXA')")
public class AdminMesaController {

    private final SessaoMesaRepository sessaoMesaRepository;
    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final ContaService contaService;
    private final SessaoMesaService sessaoMesaService;
    private final PagamentoRepository pagamentoRepository;
    private final SseEventsService eventsService;
    private final MovimentoCaixaService movimentoCaixaService;
    private final SessaoCobrancaRepository sessaoCobrancaRepository;
    private final EventoEspressoService eventoEspressoService;
    private final ConfigManager configManager;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PedidoService pedidoService;
    private final com.baronesa.emporio.repository.PagamentoAlocacaoRepository pagamentoAlocacaoRepository;
    private final com.baronesa.emporio.repository.ConfiguracaoRepository configuracaoRepository;
    private final com.baronesa.emporio.repository.MesaRepository mesaRepository;
    private final ContaReceberService contaReceberService;
    private final TipoReceitaRepository tipoReceitaRepository;

    @GetMapping("/sessoes")
    public ResponseEntity<Map<String, Object>> listarSessoes(@RequestParam(defaultValue = "open") String status) {
        StatusSessao st = "closed".equalsIgnoreCase(status) ? StatusSessao.CLOSED : StatusSessao.OPEN;
        List<SessaoMesa> sessoes = sessaoMesaRepository.findAll().stream()
                .filter(s -> s.getStatus() == st)
                .collect(Collectors.toList());

        List<Map<String, Object>> data = sessoes.stream().map(s -> {
            long pessoas = sessaoConvidadoRepository.countBySessaoMesa_Id(s.getId());
            ContaMesaResponse conta = contaService.contaMesa(s.getId());
            boolean assistida = s.getObservacoes() != null && s.getObservacoes().toLowerCase(Locale.ROOT).contains("assistida");
            String hostNome = sessaoConvidadoRepository.findBySessaoMesa_Id(s.getId()).stream()
                    .filter(c -> Boolean.TRUE.equals(c.getHost()))
                    .map(c -> c.getNomeExibicao() != null ? c.getNomeExibicao() : "Anfitrião")
                    .findFirst()
                    .orElse("Anfitrião");
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("sessaoMesaId", s.getId());
            m.put("mesaSlug", s.getMesa() != null ? s.getMesa().getSlug() : null);
            m.put("abertaEm", s.getAbertaEm());
            m.put("pessoas", pessoas);
            m.put("totalMesaCentavos", conta.totalMesaCentavos());
            m.put("pagoCentavos", conta.pagoCentavos());
            m.put("devidoCentavos", conta.devidoCentavos());
            m.put("assistida", assistida);
            m.put("hostNome", hostNome);
            return m;
        }).collect(Collectors.toList());

        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("sessoes", data);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> listarMesasOptions() {
        var abertas = sessaoMesaRepository.findAll().stream()
                .filter(s -> s.getStatus() == StatusSessao.OPEN)
                .map(s -> s.getMesa() != null ? s.getMesa().getId() : null)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        var list = mesaRepository.findAll().stream()
                .filter(m -> !abertas.contains(m.getId()))
                .map(m -> new MesaAdminOptionDTO(m.getId(), m.getSlug(), m.getRotulo(), m.getReferencia()))
                .toList();
        return ResponseEntity.ok(Map.of("data", list));
    }

    @PostMapping("/sessoes/{sessaoMesaId}/fechar")
    public ResponseEntity<?> fecharSessaoAdmin(@PathVariable Long sessaoMesaId) {
        ContaMesaResponse conta = contaService.contaMesa(sessaoMesaId);
        if (conta.devidoCentavos() != 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "mesa_em_aberto", "message", "Existem valores em aberto para a mesa")
            ));
        }
        sessaoMesaService.fecharSessao(sessaoMesaId);
        try {
            var payload = java.util.Map.of(
                    "status", "closed",
                    "sessaoMesaId", sessaoMesaId,
                    "fechadaEm", java.time.LocalDateTime.now().toString()
            );
            eventsService.publish(sessaoMesaId, "table.closed", payload);
        } catch (Exception ignored) {}
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/sessoes/{sessaoMesaId}/mover")
    public ResponseEntity<?> moverSessao(@PathVariable Long sessaoMesaId, @RequestBody MoveSessaoRequest body) {
        if (body == null || (body.mesaDestinoSlug == null && body.mesaDestinoId == null)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "destino_requerido", "message", "Informe mesaDestinoSlug ou mesaDestinoId")
            ));
        }

        var sessao = sessaoMesaRepository.findById(sessaoMesaId).orElse(null);
        if (sessao == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Sessão de mesa não encontrada")
            ));
        }

        String slugAntigo = sessao.getMesa() != null ? sessao.getMesa().getSlug() : null;
        String rotuloAntigo = sessao.getMesa() != null ? sessao.getMesa().getRotulo() : null;

        var mesaDestino = body.mesaDestinoId != null
                ? mesaRepository.findById(body.mesaDestinoId).orElse(null)
                : mesaRepository.findBySlug(body.mesaDestinoSlug).orElse(null);

        if (mesaDestino == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "destino_nao_encontrado", "message", "Mesa de destino não encontrada")
            ));
        }

        try {
            SessaoMesa movida = sessaoMesaService.moverSessaoMesa(sessaoMesaId, mesaDestino);

            var payload = Map.of(
                    "sessaoMesaId", movida.getId(),
                    "mesaSlugNova", movida.getMesa() != null ? movida.getMesa().getSlug() : null,
                    "mesaRotuloNovo", movida.getMesa() != null ? movida.getMesa().getRotulo() : null,
                    "mesaSlugAntigo", slugAntigo,
                    "mesaRotuloAntigo", rotuloAntigo,
                    "movedAt", java.time.LocalDateTime.now().toString()
            );

            eventsService.publish(movida.getId(), "table.moved", payload);
            eventsService.publishWaiter("table.moved", payload);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "mesaSlugNova", payload.get("mesaSlugNova"),
                    "mesaRotuloNovo", payload.get("mesaRotuloNovo")
            ));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "invalido", "message", e.getMessage())
            ));
        } catch (NotFoundException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", e.getMessage())
            ));
        }
    }

    @PostMapping("/{mesaSlug}/assistida")
    public ResponseEntity<?> iniciarSessaoAssistida(@PathVariable String mesaSlug,
                                                    @RequestBody AssistidaRequest body) {
        try {
            var convidado = sessaoMesaService.iniciarSessaoAssistida(
                    mesaSlug,
                    body != null ? body.clienteId() : null,
                    body != null ? body.nome() : null
            );
            SessaoMesa sessao = convidado.getSessaoMesa();
            return ResponseEntity.ok(Map.of(
                    "sessaoMesaId", sessao.getId(),
                    "sessaoConvidadoId", convidado.getId(),
                    "mesaSlug", mesaSlug
            ));
        } catch (NotFoundException e) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", e.getMessage())
            ));
        }
    }

    public static class AdminPagamentoRequest {
        public String escopo; // mesa|convidado
        public Long sessaoConvidadoId; // quando escopo=convidado
        public Long valorCentavos;
        public String metodo; // cash|card|pix
        public String cartaoTipo; // credito|debito (opcional, válido somente para card)
        public Long valorCouvertCentavos; // opcional
    }

    @PostMapping("/sessoes/{sessaoMesaId}/pagamentos")
    public ResponseEntity<?> registrarPagamento(@PathVariable Long sessaoMesaId,
                                                @RequestBody AdminPagamentoRequest body) {
        if (body == null || body.valorCentavos == null || body.valorCentavos <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of("code", "valor_invalido", "message", "Informe um valor válido em centavos")));
        }

        var sessaoMesa = sessaoMesaRepository.findById(sessaoMesaId).orElse(null);
        if (sessaoMesa == null) {
            return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "not_found", "message", "Sessão de mesa não encontrada")));
        }

        long couvertCent = body.valorCouvertCentavos != null ? body.valorCouvertCentavos : 0L;
        var pagamento = Pagamento.builder()
                .sessaoMesa(sessaoMesa)
                .sessaoConvidado(null)
                .metodo(body.metodo != null ? body.metodo : "cash")
                .status(StatusPagamento.PAID)
                .valor(java.math.BigDecimal.valueOf(body.valorCentavos + couvertCent, 2))
                .valorBase(java.math.BigDecimal.valueOf(body.valorCentavos, 2)) // consumo
                .valorTaxaServico(java.math.BigDecimal.ZERO)
                .valorCouvert(java.math.BigDecimal.valueOf(couvertCent, 2))
                .percentualTaxaServico(null)
                .incluiTaxaServico(false)
                .providerRef("admin")
                .cartaoTipo(normalizeCartaoTipo(body.cartaoTipo))
                .build();

        if ("convidado".equalsIgnoreCase(body.escopo)) {
            if (body.sessaoConvidadoId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", Map.of("code", "convidado_requerido", "message", "sessaoConvidadoId é obrigatório para escopo=convidado")));
            }
            var convidado = sessaoConvidadoRepository.findById(body.sessaoConvidadoId).orElse(null);
            if (convidado == null) {
                return ResponseEntity.status(404).body(Map.of("error", Map.of("code", "not_found", "message", "Convidado não encontrado")));
            }
            pagamento.setSessaoConvidado(convidado);
        }

        pagamento.setPagoEm(java.time.LocalDateTime.now());
        pagamento = pagamentoRepository.save(pagamento);

        // Registrar movimento de caixa
        try {
            TipoFormaPagamento formaPagamento = mapearMetodoPagamento(pagamento.getMetodo(), pagamento.getCartaoTipo());
            String mesaRotulo = pagamento.getSessaoMesa().getMesa() != null
                ? pagamento.getSessaoMesa().getMesa().getRotulo()
                : "N/A";
            movimentoCaixaService.registrar(
                TipoMovimentoCaixa.PAGAMENTO_MESA,
                pagamento.getValor(),
                formaPagamento,
                true,
                "PAGAMENTO",
                pagamento.getId(),
                null,
                TipoOperacao.ENTRADA,
                "Pagamento de mesa #" + mesaRotulo
            );
        } catch (Exception e) {
            System.err.println("Erro ao registrar movimento de caixa: " + e.getMessage());
        }

        var contaAtualizada = contaService.contaMesa(sessaoMesaId);
        java.util.Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("success", true);
        resp.put("devidoCentavos", contaAtualizada.devidoCentavos());
        resp.put("pagoCentavos", contaAtualizada.pagoCentavos());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/sessoes/{sessaoMesaId}/pagamentos/multiplo")
    public ResponseEntity<?> registrarPagamentosMultiplos(
            @PathVariable Long sessaoMesaId,
            @RequestBody RegistrarPagamentosMultiplosRequest request) {

        if (request == null || request.pagamentos() == null || request.pagamentos().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "pagamentos_vazio", "message", "Informe ao menos um pagamento")
            ));
        }

        SessaoMesa sessaoMesa = sessaoMesaRepository.findById(sessaoMesaId).orElse(null);
        if (sessaoMesa == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Sessão de mesa não encontrada")
            ));
        }

        List<Pagamento> pagamentosCriados = new ArrayList<>();
        String batchRef = "admin-multi-" + UUID.randomUUID();

        for (RegistrarPagamentoRequest pagReq : request.pagamentos()) {
            // Verificar e processar itens com status 'queued' apenas dos convidados que estão sendo pagos
            Set<Long> convidadosSendoPagos = new HashSet<>();

            // Identificar quais convidados estão sendo pagos
            if (pagReq.sessaoConvidadoId() != null) {
                // Pagamento direto para um convidado específico
                convidadosSendoPagos.add(pagReq.sessaoConvidadoId());
            } else if (pagReq.alocacoes() != null) {
                // Pagamento com alocação entre convidados
                for (var alocacao : pagReq.alocacoes()) {
                    if (alocacao != null && alocacao.sessaoConvidadoId() != null) {
                        convidadosSendoPagos.add(alocacao.sessaoConvidadoId());
                    }
                }
            }

            // Se houver convidados sendo pagos, buscar apenas itens com status 'queued' desses convidados específicos
            if (!convidadosSendoPagos.isEmpty()) {
                List<ItemPedido> itensPendentes = itemPedidoRepository.findByPedido_SessaoConvidadoIdInAndStatus(convidadosSendoPagos, StatusItem.QUEUED);

                if (!itensPendentes.isEmpty()) {
                    log.info("Encontrados {} itens com status 'queued' dos convidados sendo pagos {}. Processando aceitação automática.",
                             itensPendentes.size(), convidadosSendoPagos);

                    for (ItemPedido item : itensPendentes) {
                        try {
                            // Aceitar automaticamente o item para acionar a baixa de estoque
                            pedidoService.atualizarStatusItem(item.getId(), StatusItem.ACCEPTED, "AUTOMATICO_PAGAMENTO",
                                    "Item automaticamente aceito ao registrar pagamento para garantir baixa de estoque");
                            log.info("Item {} (convidado {}) automaticamente aceito para baixa de estoque",
                                    item.getId(), item.getPedido().getSessaoConvidado().getId());
                        } catch (Exception e) {
                            log.error("Erro ao aceitar automaticamente item {} para baixa de estoque: {}", item.getId(), e.getMessage());
                            // Continuar com o processamento mesmo se um item falhar
                        }
                    }
                }
            }
            // Validações
            if (pagReq.valorCentavos() == null || pagReq.valorCentavos() <= 0) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", Map.of("code", "valor_invalido", "message", "Valor deve ser maior que zero")
                ));
            }

            if (pagReq.metodo() == null || pagReq.metodo().isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", Map.of("code", "metodo_invalido", "message", "Método de pagamento é obrigatório")
                ));
            }

            // Buscar beneficiário se especificado
            SessaoConvidado beneficiario = null;
            if (pagReq.sessaoConvidadoId() != null) {
                beneficiario = sessaoConvidadoRepository.findById(pagReq.sessaoConvidadoId()).orElse(null);
                if (beneficiario == null) {
                    return ResponseEntity.status(404).body(Map.of(
                            "error", Map.of("code", "beneficiario_nao_encontrado",
                                    "message", "Beneficiário com ID " + pagReq.sessaoConvidadoId() + " não encontrado")
                    ));
                }
            }

            // Buscar pagante se especificado
            SessaoConvidado pagante = null;
            if (pagReq.paganteId() != null) {
                pagante = sessaoConvidadoRepository.findById(pagReq.paganteId()).orElse(null);
                if (pagante == null) {
                    return ResponseEntity.status(404).body(Map.of(
                            "error", Map.of("code", "pagante_nao_encontrado",
                                    "message", "Pagante com ID " + pagReq.paganteId() + " não encontrado")
                    ));
                }
            }

            // Criar pagamento
            long baseCent = pagReq.valorCentavos();
            boolean incluiTaxa = Boolean.TRUE.equals(pagReq.incluiTaxaServico());
            long taxaCent = 0;
            java.math.BigDecimal pct = null;
            if (incluiTaxa) {
                taxaCent = calcularTaxaServico(baseCent, pagReq.valorTaxaServicoCentavos());
                pct = obterPercentualTaxaServico();
            }
            long couvertCent = pagReq.valorCouvertCentavos() != null ? pagReq.valorCouvertCentavos() : 0L;

            Pagamento pagamento = Pagamento.builder()
                    .sessaoMesa(sessaoMesa)
                    .sessaoConvidado(beneficiario)
                    .pagante(pagante)
                    .metodo(pagReq.metodo().toLowerCase())
                    .cartaoTipo(normalizeCartaoTipo(pagReq.cartaoTipo()))
                    .status(StatusPagamento.PAID)
                    .valor(java.math.BigDecimal.valueOf(baseCent + taxaCent + couvertCent, 2))
                    .valorBase(java.math.BigDecimal.valueOf(baseCent, 2))
                    .valorTaxaServico(java.math.BigDecimal.valueOf(taxaCent, 2))
                    .valorCouvert(java.math.BigDecimal.valueOf(couvertCent, 2))
                    .percentualTaxaServico(pct)
                    .incluiTaxaServico(incluiTaxa)
                    .providerRef(batchRef)
                    .pagoEm(java.time.LocalDateTime.now())
                    .build();

            pagamento = pagamentoRepository.save(pagamento);

            // Criar alocações (quando pagamento é de mesa toda com alocações informadas)
            if ((pagReq.sessaoConvidadoId() == null) && pagReq.alocacoes() != null && !pagReq.alocacoes().isEmpty()) {
                for (var al : pagReq.alocacoes()) {
                    if (al == null || al.sessaoConvidadoId() == null || al.valorCentavos() == null || al.valorCentavos() <= 0) continue;
                    SessaoConvidado beneficiarioAlocado = sessaoConvidadoRepository.findById(al.sessaoConvidadoId()).orElse(null);
                    if (beneficiarioAlocado == null) continue;
                    var aloc = com.baronesa.emporio.entity.PagamentoAlocacao.builder()
                            .pagamento(pagamento)
                            .sessaoConvidado(beneficiarioAlocado)
                            .valor(java.math.BigDecimal.valueOf(al.valorCentavos(), 2))
                            .build();
                    pagamentoAlocacaoRepository.save(aloc);
                }
            }
            pagamentosCriados.add(pagamento);

            // Registrar movimento de caixa
            try {
                TipoFormaPagamento formaPagamento = mapearMetodoPagamento(pagamento.getMetodo(), pagamento.getCartaoTipo());
                String mesaRotulo = sessaoMesa.getMesa() != null
                    ? sessaoMesa.getMesa().getRotulo()
                    : "N/A";
                movimentoCaixaService.registrar(
                    TipoMovimentoCaixa.PAGAMENTO_MESA,
                    pagamento.getValor(),
                    formaPagamento,
                    true,
                    "PAGAMENTO",
                    pagamento.getId(),
                    null,
                    TipoOperacao.ENTRADA,
                    "Pagamento de mesa #" + mesaRotulo
                );
            } catch (Exception e) {
                System.err.println("Erro ao registrar movimento de caixa para pagamento " + pagamento.getId() + ": " + e.getMessage());
            }
        }

        // Publicar evento SSE
        try {
            ContaMesaResponse contaAtualizada = contaService.contaMesa(sessaoMesaId);
            Map<String, Object> payload = Map.of(
                    "sessaoMesaId", sessaoMesaId,
                    "totalPago", contaAtualizada.pagoCentavos(),
                    "devidoRestante", contaAtualizada.devidoCentavos(),
                    "quantidadePagamentos", pagamentosCriados.size()
            );
            eventsService.publish(sessaoMesaId, "payment.registered", payload);
        } catch (Exception e) {
            System.err.println("Erro ao publicar payment.registered: " + e.getMessage());
        }

        ContaMesaResponse contaAtualizada = contaService.contaMesa(sessaoMesaId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "registrados", pagamentosCriados.size(),
                "pagamentoIds", pagamentosCriados.stream().map(Pagamento::getId).toList(),
                "contaAtualizada", Map.of(
                        "totalMesaCentavos", contaAtualizada.totalMesaCentavos(),
                        "pagoCentavos", contaAtualizada.pagoCentavos(),
                        "devidoCentavos", contaAtualizada.devidoCentavos()
                )
        ));
    }

    @GetMapping("/sessoes/{sessaoMesaId}/conta")
    public ResponseEntity<ContaMesaResponse> obterContaMesa(@PathVariable Long sessaoMesaId) {
        garantirCouvertsSessao(sessaoMesaId);
        ContaMesaResponse conta = contaService.contaMesa(sessaoMesaId);
        return ResponseEntity.ok(conta);
    }

    @GetMapping("/sessoes/{sessaoMesaId}/pagamentos")
    public ResponseEntity<?> listarPagamentos(@PathVariable Long sessaoMesaId) {
        SessaoMesa sessaoMesa = sessaoMesaRepository.findById(sessaoMesaId).orElse(null);
        if (sessaoMesa == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Sessão de mesa não encontrada")
            ));
        }

        List<Pagamento> pagamentos = pagamentoRepository.findBySessaoMesa(sessaoMesa);

        List<PagamentoHistoricoResponse> historico = pagamentos.stream()
                .map(pag -> {
                    String beneficiario = pag.getSessaoConvidado() != null
                            ? pag.getSessaoConvidado().getNomeExibicao()
                            : "Mesa toda";
                    Long beneficiarioId = pag.getSessaoConvidado() != null
                            ? pag.getSessaoConvidado().getId()
                            : null;

                    String pagante = pag.getPagante() != null
                            ? pag.getPagante().getNomeExibicao()
                            : null;
                    Long paganteId = pag.getPagante() != null
                            ? pag.getPagante().getId()
                            : null;

                    return new PagamentoHistoricoResponse(
                            pag.getId(),
                            beneficiario,
                            beneficiarioId,
                            pagante,
                            paganteId,
                            pag.getValor().movePointRight(2).longValue(),
                            valorBaseCentavos(pag),
                            valorCouvertCentavos(pag),
                            valorTaxaCentavos(pag),
                            pag.getPercentualTaxaServico(),
                            pag.getIncluiTaxaServico(),
                            pag.getMetodo(),
                            pag.getStatus().name().toLowerCase(),
                            pag.getCriadoEm(),
                            pag.getPagoEm()
                    );
                })
                .collect(Collectors.toList());

        // Verificar elegibilidade para faturamento mensalista
        Map<String, Object> elegibilidade = new java.util.HashMap<>();
        elegibilidade.put("possivel", false);
        elegibilidade.put("motivoBloqueio", null);

        // Regra 1: Sem pagamentos prévios
        if (!pagamentos.isEmpty()) {
            elegibilidade.put("motivoBloqueio", "Já existem pagamentos registrados para esta mesa.");
        } else {
            // Regra 2: Único convidado e é host
            List<SessaoConvidado> convidados = sessaoConvidadoRepository.findBySessaoMesa_Id(sessaoMesaId);
            if (convidados.size() != 1) {
                elegibilidade.put("motivoBloqueio", "Mesa deve ter apenas 1 convidado (Host) para faturamento mensal.");
            } else {
                SessaoConvidado host = convidados.get(0);
                if (!Boolean.TRUE.equals(host.getHost())) {
                    elegibilidade.put("motivoBloqueio", "O único convidado não está marcado como anfitrião.");
                } else {
                    // Regra 3: Cliente é mensalista
                    if (host.getUsuario() != null && host.getUsuario().getPerfilCliente() != null &&
                        Boolean.TRUE.equals(host.getUsuario().getPerfilCliente().getMensalista())) {
                        elegibilidade.put("possivel", true);
                        elegibilidade.put("hostNome", host.getUsuario().getNome());
                        elegibilidade.put("hostId", host.getUsuario().getId()); // ID do Usuario para buscar clienteId depois
                    } else {
                        elegibilidade.put("motivoBloqueio", "O cliente anfitrião não possui perfil de mensalista ativo.");
                    }
                }
            }
        }

        return ResponseEntity.ok(Map.of(
            "pagamentos", historico,
            "elegibilidadeMensalista", elegibilidade
        ));
    }

    /**
     * Mapeia o método de pagamento (pix, card, cash) para TipoFormaPagamento.
     * Param cartaoTipo aceita valores como "credito" ou "debito" (case insensitive).
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

    private String normalizeCartaoTipo(String cartaoTipo) {
        if (cartaoTipo == null) return null;
        return cartaoTipo.trim().toLowerCase(Locale.ROOT);
    }

    private long calcularTaxaServico(long baseCentavos, Long taxaInformadaCentavos) {
        if (taxaInformadaCentavos != null && taxaInformadaCentavos > 0) {
            return taxaInformadaCentavos;
        }
        java.math.BigDecimal pct = obterPercentualTaxaServico();
        if (pct == null) return 0;
        java.math.BigDecimal base = java.math.BigDecimal.valueOf(baseCentavos, 2);
        java.math.BigDecimal fee = base.multiply(pct).divide(java.math.BigDecimal.valueOf(100));
        return fee.movePointRight(2).longValue();
    }

    private java.math.BigDecimal obterPercentualTaxaServico() {
        try {
            String valor = configuracaoRepository.findValorByChave("taxa_servico_percentual");
            if (valor == null || valor.isBlank()) return null;
            return new java.math.BigDecimal(valor.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private long valorBaseCentavos(Pagamento p) {
        java.math.BigDecimal base = p.getValorBase() != null ? p.getValorBase() : p.getValor();
        return base != null ? base.movePointRight(2).longValue() : 0L;
    }

    private long valorTaxaCentavos(Pagamento p) {
        java.math.BigDecimal taxa = p.getValorTaxaServico();
        return taxa != null ? taxa.movePointRight(2).longValue() : 0L;
    }

    private long valorCouvertCentavos(Pagamento p) {
        java.math.BigDecimal couvert = p.getValorCouvert();
        return couvert != null ? couvert.movePointRight(2).longValue() : 0L;
    }


    @PostMapping("/sessoes/{sessaoMesaId}/faturar-mensalista")
    public ResponseEntity<?> faturarMensalista(@PathVariable Long sessaoMesaId) {
        // 1. Validações Básicas
        SessaoMesa sessao = sessaoMesaRepository.findById(sessaoMesaId).orElse(null);
        if (sessao == null) return ResponseEntity.notFound().build();

        ContaMesaResponse conta = contaService.contaMesa(sessaoMesaId);
        if (conta.pagoCentavos() > 0) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", "Mesa já possui pagamentos parciais.")));
        }
        if (conta.devidoCentavos() <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", "Mesa não possui valor a faturar.")));
        }

        // 2. Validação Host Mensalista
        List<SessaoConvidado> convidados = sessaoConvidadoRepository.findBySessaoMesa_Id(sessaoMesaId);
        if (convidados.size() != 1) return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", "Mesa deve ter apenas 1 convidado.")));

        SessaoConvidado host = convidados.get(0);
        if (!Boolean.TRUE.equals(host.getHost())) return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", "Convidado não é host.")));

        if (host.getUsuario() == null || host.getUsuario().getPerfilCliente() == null || !Boolean.TRUE.equals(host.getUsuario().getPerfilCliente().getMensalista())) {
            return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", "Cliente não é mensalista.")));
        }

        // 3. Preparar Dados Financeiros
        // Busca TipoReceita "Mensalistas" (ID 2 fixo conforme convenção do projeto)
        TipoReceita tipoReceita = tipoReceitaRepository.findById(2L).orElse(null);
        if (tipoReceita == null) {
             return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", "Tipo de Receita 'Mensalistas' não configurado (ID 2).")));
        }

        // Data Vencimento: 1o dia util proximo mês
        LocalDate hoje = LocalDate.now();
        LocalDate vencimento = hoje.plusMonths(1).withDayOfMonth(1);
        while (vencimento.getDayOfWeek() == DayOfWeek.SATURDAY || vencimento.getDayOfWeek() == DayOfWeek.SUNDAY) {
            vencimento = vencimento.plusDays(1);
        }

        String mesaSlug = sessao.getMesa() != null ? sessao.getMesa().getSlug() : "balcao";
        String numDoc = "MESA-" + mesaSlug + "-" + System.currentTimeMillis();

        // 4. Criar Contas a Receber
        var parcelaReq = new ContaReceberParcelaRequest(
            null, // id
            1, // numeroParcela
            java.math.BigDecimal.valueOf(conta.devidoCentavos(), 2), // valor
            vencimento, // dataVencimento
            null, null, null, null, null, null, false, false, null
        );

        var contaReq = new ContaReceberRequest(
             host.getUsuario().getId(),
             tipoReceita.getId(),
             numDoc,
             "Consumo Mesa " + mesaSlug + " (Mensalista)",
             java.math.BigDecimal.valueOf(conta.devidoCentavos(), 2),
             1, // n parcelas
             "Gerado automaticamente pelo fechamento de mesa.",
             false, // recorrente
             List.of(parcelaReq)
        );

        try {
            contaReceberService.criar(contaReq);
        } catch (Exception e) {
             return ResponseEntity.badRequest().body(Map.of("error", Map.of("message", "Erro ao criar conta a receber: " + e.getMessage())));
        }

        // 5. Registrar Pagamento na Mesa para Baixar
        var pagamento = Pagamento.builder()
                .sessaoMesa(sessao)
                .sessaoConvidado(host)
                .metodo("outros")
                .status(StatusPagamento.PAID)
                .valor(java.math.BigDecimal.valueOf(conta.devidoCentavos(), 2))
                .valorBase(java.math.BigDecimal.valueOf(conta.devidoCentavos(), 2))
                .valorTaxaServico(java.math.BigDecimal.ZERO)
                .valorCouvert(java.math.BigDecimal.ZERO)
                .incluiTaxaServico(false)
                .providerRef("faturamento_mensal")
                .pagoEm(java.time.LocalDateTime.now())
                .build();

        pagamentoRepository.save(pagamento);

        // 6. Fechar Sessão
        sessaoMesaService.fecharSessao(sessaoMesaId);

        // Notificar fechamento
        try {
            var payload = java.util.Map.of(
                    "status", "closed",
                    "sessaoMesaId", sessaoMesaId,
                    "fechadaEm", java.time.LocalDateTime.now().toString(),
                    "faturadoMensalista", true
            );
            eventsService.publish(sessaoMesaId, "table.closed", payload);
        } catch (Exception ignored) {}

        // 7. Retornar Sucesso
        return ResponseEntity.ok(Map.of(
            "success", true,
            "mensagem", "Faturamento registrado com vencimento em " + vencimento.toString()
        ));
    }

    // Endpoint para adicionar convidado extra
    @PostMapping("/sessoes/{sessaoMesaId}/convidados")
    public ResponseEntity<?> adicionarConvidadoExtra(@PathVariable Long sessaoMesaId,
                                                     @RequestBody(required = false) AdicionarConvidadoRequest request) {
        SessaoMesa sessaoMesa = sessaoMesaRepository.findById(sessaoMesaId).orElse(null);
        if (sessaoMesa == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Sessão de mesa não encontrada")
            ));
        }

        // Usar o serviço para adicionar convidado extra com guestToken válido
        SessaoConvidado convidado = sessaoMesaService.adicionarConvidadoExtra(
                sessaoMesaId,
                request != null ? request.nome() : null
        );

        Map<String, Object> response = Map.of(
                "sessaoConvidadoId", convidado.getId(),
                "nomeExibicao", convidado.getNomeExibicao(),
                "sessaoMesaId", sessaoMesaId
        );

        return ResponseEntity.ok(response);
    }

    // Endpoint para lançar couvert
    @PostMapping("/sessoes/{sessaoMesaId}/couverts")
    public ResponseEntity<?> lancarCouvert(@PathVariable Long sessaoMesaId,
                                           @RequestBody(required = false) LancarCouvertRequest request) {
        // Validar configuração de couvert
        boolean couvertAtivo = configManager.getBooleanConfig("couvert_artistico_ativo", false);
        if (!couvertAtivo) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "couvert_nao_ativo", "message", "Couvert artístico não está ativo")
            ));
        }

        SessaoMesa sessaoMesa = sessaoMesaRepository.findById(sessaoMesaId).orElse(null);
        if (sessaoMesa == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Sessão de mesa não encontrada")
            ));
        }

        List<SessaoConvidado> convidados;
        if (request != null && request.sessaoConvidadoId() != null) {
            // Apenas para um convidado específico
            SessaoConvidado convidado = sessaoConvidadoRepository.findById(request.sessaoConvidadoId()).orElse(null);
            if (convidado == null || !convidado.getSessaoMesa().getId().equals(sessaoMesaId)) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", Map.of("code", "not_found", "message", "Convidado não encontrado na sessão")
                ));
            }
            convidados = List.of(convidado);
        } else {
            // Para todos os convidados da sessão (exceto host mensalista)
            convidados = sessaoConvidadoRepository.findBySessaoMesa_Id(sessaoMesaId);
        }

        // Buscar valor do couvert no espresso (busca o primeiro evento ativo)
        BigDecimal valorCouvert = eventoEspressoService.buscarValorCouvertAtivo();
        if (valorCouvert == null) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "valor_nao_encontrado", "message", "Valor do couvert não encontrado no evento ou evento gratuito")
            ));
        }

        // Buscar evento ativo para obter o ID
        Long eventoId = eventoEspressoService.buscarEventoAtivo();
        if (eventoId == null) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "evento_nao_encontrado", "message", "Evento ativo não encontrado no sistema espresso")
            ));
        }

        List<SessaoCobranca> cobrancasCriadas = new ArrayList<>();
        java.util.Set<Long> convidadosComCouvert = sessaoCobrancaRepository.findBySessaoMesaIdAndStatus(sessaoMesaId, StatusCobranca.ATIVA)
                .stream()
                .filter(c -> c.getTipo() == TipoCobranca.COUVERT_ARTISTICO)
                .map(SessaoCobranca::getSessaoConvidadoId)
                .collect(java.util.stream.Collectors.toSet());
        for (SessaoConvidado convidado : convidados) {
            if (convidadosComCouvert.contains(convidado.getId())) {
                continue;
            }
            // Verificar se o convidado é host mensalista (isento de couvert)
            boolean isento = false;
            String motivoIsencao = null;
            if (Boolean.TRUE.equals(convidado.getHost()) &&
                convidado.getUsuario() != null &&
                convidado.getUsuario().getPerfilCliente() != null &&
                Boolean.TRUE.equals(convidado.getUsuario().getPerfilCliente().getMensalista())) {
                isento = true;
                motivoIsencao = "Host mensalista";
            }

            // Criar cobrança de couvert
            SessaoCobranca cobranca = new SessaoCobranca(
                    sessaoMesaId,
                    convidado.getId(),
                    TipoCobranca.COUVERT_ARTISTICO,
                    valorCouvert,
                    eventoId,
                    isento,
                    motivoIsencao,
                    "admin" // Criado por admin
            );

            SessaoCobranca salva = sessaoCobrancaRepository.save(cobranca);
            cobrancasCriadas.add(salva);
        }

        Map<String, Object> response = Map.of(
                "success", true,
                "quantidadeCriada", cobrancasCriadas.size(),
                "cobrancas", cobrancasCriadas.stream().map(c -> Map.of(
                        "id", c.getId(),
                        "sessaoConvidadoId", c.getSessaoConvidadoId(),
                        "valor", c.getValor().movePointRight(2).longValue(),
                        "isento", c.getIsento(),
                        "motivoIsencao", c.getMotivoIsencao()
                )).toList()
        );

        return ResponseEntity.ok(response);
    }

    // Endpoint para cancelar couvert
    @DeleteMapping("/sessoes/{sessaoMesaId}/couverts/{id}")
    public ResponseEntity<?> cancelarCouvert(@PathVariable Long sessaoMesaId,
                                             @PathVariable Long id) {
        SessaoCobranca cobranca = sessaoCobrancaRepository.findById(id).orElse(null);
        if (cobranca == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", Map.of("code", "not_found", "message", "Cobrança não encontrada")
            ));
        }

        if (!cobranca.getSessaoMesaId().equals(sessaoMesaId)) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "sessao_invalida", "message", "Cobrança não pertence à sessão informada")
            ));
        }

        if (cobranca.getStatus() == StatusCobranca.CANCELADA) {
            return ResponseEntity.status(400).body(Map.of(
                    "error", Map.of("code", "ja_cancelada", "message", "Cobrança já está cancelada")
            ));
        }

        // Atualizar status para CANCELADA
        cobranca.setStatus(StatusCobranca.CANCELADA);
        sessaoCobrancaRepository.save(cobranca);

        Map<String, Object> response = Map.of(
                "success", true,
                "mensagem", "Cobrança de couvert cancelada com sucesso"
        );

        return ResponseEntity.ok(response);
    }

    public record AdicionarConvidadoRequest(
            String nome
    ) {}

    public record MoveSessaoRequest(
            String mesaDestinoSlug,
            Long mesaDestinoId
    ) {}

    public record LancarCouvertRequest(
            Long sessaoConvidadoId
    ) {}

    public record AssistidaRequest(
            Long clienteId,
            String nome
    ) {}

    private void garantirCouvertsSessao(Long sessaoMesaId) {
        boolean couvertAtivo = configManager.getBooleanConfig("couvert_artistico_ativo", false);
        if (!couvertAtivo) {
            return;
        }
        Long eventoId = eventoEspressoService.buscarEventoAtivo();
        BigDecimal valorCouvert = eventoEspressoService.buscarValorCouvertAtivo();
        if (eventoId == null || valorCouvert == null) {
            return;
        }
        List<SessaoConvidado> convidados = sessaoConvidadoRepository.findBySessaoMesa_Id(sessaoMesaId);
        if (convidados.isEmpty()) {
            return;
        }
        java.util.Set<Long> convidadosComCouvert = sessaoCobrancaRepository.findBySessaoMesaIdAndStatus(sessaoMesaId, StatusCobranca.ATIVA)
                .stream()
                .filter(c -> c.getTipo() == TipoCobranca.COUVERT_ARTISTICO)
                .map(SessaoCobranca::getSessaoConvidadoId)
                .collect(java.util.stream.Collectors.toSet());
        for (SessaoConvidado convidado : convidados) {
            if (convidadosComCouvert.contains(convidado.getId())) {
                continue;
            }
            boolean isento = false;
            String motivoIsencao = null;
            if (Boolean.TRUE.equals(convidado.getHost()) &&
                convidado.getUsuario() != null &&
                convidado.getUsuario().getPerfilCliente() != null &&
                Boolean.TRUE.equals(convidado.getUsuario().getPerfilCliente().getMensalista())) {
                isento = true;
                motivoIsencao = "Host mensalista";
            }
            SessaoCobranca cobranca = new SessaoCobranca(
                    sessaoMesaId,
                    convidado.getId(),
                    TipoCobranca.COUVERT_ARTISTICO,
                    valorCouvert,
                    eventoId,
                    isento,
                    motivoIsencao,
                    "system"
            );
            sessaoCobrancaRepository.save(cobranca);
        }
    }
}
