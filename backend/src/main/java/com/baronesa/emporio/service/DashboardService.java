package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.DashboardPedidosPreparacaoDTO;
import com.baronesa.emporio.dto.DashboardVendasDTO;
import com.baronesa.emporio.dto.PedidosDiariosDTO;
import com.baronesa.emporio.dto.PedidosLocalDTO;
import com.baronesa.emporio.dto.ProdutoDesempenhoDTO;
import com.baronesa.emporio.dto.dashboard.PendenciasDTO;
import com.baronesa.emporio.enums.StatusItem;
import com.baronesa.emporio.enums.StatusSessao;
import com.baronesa.emporio.enums.TipoPrecificacao;
import com.baronesa.emporio.dto.dashboard.MovimentoCaixaResumoDTO;
import com.baronesa.emporio.dto.dashboard.VendasPeriodoDTO;
import com.baronesa.emporio.dto.dashboard.FinanceiroResumoDTO;
import com.baronesa.emporio.dto.dashboard.ConsumoVoucherDTO;
import com.baronesa.emporio.dto.dashboard.VendasHistoricoDTO;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import com.baronesa.emporio.repository.ContaReceberRepository;
import com.baronesa.emporio.repository.ContaPagarRepository;
import com.baronesa.emporio.repository.MesaRepository;
import com.baronesa.emporio.repository.PedidoRepository;
import com.baronesa.emporio.repository.SessaoMesaRepository;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.repository.MovimentoCaixaRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import com.baronesa.emporio.enums.LocalPreparacao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ItemPedidoRepository itemPedidoRepository;
    private final MesaRepository mesaRepository;
    private final SessaoMesaRepository sessaoMesaRepository;
    private final PagamentoRepository pagamentoRepository;
    private final MovimentoCaixaRepository movimentoCaixaRepository;
    private final PedidoRepository pedidoRepository;
    private final ContaReceberRepository contaReceberRepository;
    private final ContaPagarRepository contaPagarRepository;
    private final ProdutoRepository produtoRepository;

    @Transactional(readOnly = true)
    public DashboardPedidosPreparacaoDTO getPedidosPreparacao() {
        log.info("Buscando métricas de pedidos em preparação");

        // Contar TODOS os itens ativos no BAR (mesma lógica do KDS)
        Long queuedBar = itemPedidoRepository.countByProdutoLocalPreparacaoAndStatus(LocalPreparacao.BAR, StatusItem.QUEUED);
        Long acceptedBar = itemPedidoRepository.countByProdutoLocalPreparacaoAndStatus(LocalPreparacao.BAR, StatusItem.ACCEPTED);
        Long preparingBar = itemPedidoRepository.countByProdutoLocalPreparacaoAndStatus(LocalPreparacao.BAR, StatusItem.PREPARING);
        Long readyBar = itemPedidoRepository.countByProdutoLocalPreparacaoAndStatus(LocalPreparacao.BAR, StatusItem.READY);

        Long pedidosNoBar = queuedBar + acceptedBar + preparingBar + readyBar;
        Long aguardandoPreparoBar = queuedBar;

        // Contar TODOS os itens ativos na COZINHA (mesma lógica do KDS)
        Long queuedCozinha = itemPedidoRepository.countByProdutoLocalPreparacaoAndStatus(LocalPreparacao.COZINHA, StatusItem.QUEUED);
        Long acceptedCozinha = itemPedidoRepository.countByProdutoLocalPreparacaoAndStatus(LocalPreparacao.COZINHA, StatusItem.ACCEPTED);
        Long preparingCozinha = itemPedidoRepository.countByProdutoLocalPreparacaoAndStatus(LocalPreparacao.COZINHA, StatusItem.PREPARING);
        Long readyCozinha = itemPedidoRepository.countByProdutoLocalPreparacaoAndStatus(LocalPreparacao.COZINHA, StatusItem.READY);

        Long pedidosNaCozinha = queuedCozinha + acceptedCozinha + preparingCozinha + readyCozinha;
        Long emPreparoCozinha = queuedCozinha;

        // Pedidos prontos (aguardando entrega) - soma de todos os READY
        Long pedidosEmEspera = itemPedidoRepository.countByStatus(StatusItem.READY);
        Long aguardandoEntrega = pedidosEmEspera;

        return DashboardPedidosPreparacaoDTO.builder()
                .pedidosNoBar(pedidosNoBar)
                .aguardandoPreparoBar(aguardandoPreparoBar)
                .pedidosNaCozinha(pedidosNaCozinha)
                .emPreparoCozinha(emPreparoCozinha)
                .pedidosEmEspera(pedidosEmEspera)
                .aguardandoEntrega(aguardandoEntrega)
                .build();
    }

    @Transactional(readOnly = true)
    public DashboardVendasDTO getVendas() {
        log.info("Buscando métricas de vendas do dia");

        // Calcular início e fim do dia atual
        LocalDate hoje = LocalDate.now();
        LocalDateTime inicioHoje = hoje.atStartOfDay();
        LocalDateTime fimHoje = hoje.plusDays(1).atStartOfDay();

        // Mesas ocupadas
        Long totalMesas = mesaRepository.count();
        Long mesasOcupadas = sessaoMesaRepository.countByStatus(StatusSessao.OPEN);
        Double percentualOcupacao = totalMesas > 0 ? (mesasOcupadas.doubleValue() / totalMesas.doubleValue()) * 100 : 0.0;

        // Vendas do dia (com base em pagamentos efetivados)
        java.math.BigDecimal totalVendas = pagamentoRepository.sumValorPagoByDate(inicioHoje, fimHoje);
        Long totalVendasCentavos = totalVendas != null ? totalVendas.movePointRight(2).longValue() : 0L;
        Long quantidadeVendas = pagamentoRepository.countVendasByDate(inicioHoje, fimHoje);

        // Ticket médio por venda
        Long ticketMedioCentavos = (quantidadeVendas != null && quantidadeVendas > 0)
                ? totalVendasCentavos / quantidadeVendas
                : 0L;

        return DashboardVendasDTO.builder()
                .totalMesas(totalMesas)
                .mesasOcupadas(mesasOcupadas)
                .percentualOcupacao(percentualOcupacao)
                .totalVendasCentavos(totalVendasCentavos)
                .ticketMedioCentavos(ticketMedioCentavos)
                .quantidadeVendas(quantidadeVendas != null ? quantidadeVendas : 0L)
                .build();
    }

    @Transactional(readOnly = true)
    public List<ProdutoDesempenhoDTO> getTopProdutos(String ordenarPor, String periodo) {
        log.info("Buscando top produtos ordenados por: {} no período: {}", ordenarPor, periodo);

        // Calcular período baseado no parâmetro
        LocalDateTime fim = LocalDateTime.now();
        LocalDateTime inicio;

        switch (periodo != null ? periodo.toLowerCase() : "7d") {
            case "hoje":
                inicio = LocalDate.now().atStartOfDay();
                break;
            case "30d":
                inicio = fim.minusDays(30);
                break;
            case "7d":
            default:
                inicio = fim.minusDays(7);
                break;
        }

        List<Object[]> resultados;

        // Escolher query baseada no critério de ordenação
        if ("quantidade".equalsIgnoreCase(ordenarPor)) {
            resultados = itemPedidoRepository.findTopProdutosByQuantidade(inicio, fim);
        } else {
            // Default: ordenar por valor
            resultados = itemPedidoRepository.findTopProdutosByValor(inicio, fim);
        }

        // Converter Object[] para DTO
        List<ProdutoDesempenhoDTO> produtos = new ArrayList<>();
        for (Object[] row : resultados) {
            ProdutoDesempenhoDTO dto = ProdutoDesempenhoDTO.builder()
                    .produtoId(((Number) row[0]).longValue())
                    .nome((String) row[1])
                    .quantidade(((Number) row[2]).longValue())
                    .valor((BigDecimal) row[3])
                    .build();
            produtos.add(dto);
        }

        return produtos;
    }

    @Transactional(readOnly = true)
    public MovimentoCaixaResumoDTO getMovimentoCaixa() {
        log.info("Buscando movimento de caixa do dia");

        try {
            LocalDate hoje = LocalDate.now();

            // Buscar resumo usando o método do repository
            MovimentoCaixaResumoDTO resumo = movimentoCaixaRepository.findResumoHoje(hoje);

            // Se retornou zeros, tentar com CURRENT_DATE
            if (resumo.entradas().compareTo(BigDecimal.ZERO) == 0 &&
                    resumo.saidas().compareTo(BigDecimal.ZERO) == 0) {
                log.warn("Método com LocalDate retornou zeros. Tentando com CURRENT_DATE...");
                resumo = movimentoCaixaRepository.findResumoHojeUsandoCurrentDate();
            }

            // Mapear tipos de pagamento para o frontend
            Map<String, BigDecimal> detalhesPorTipo = resumo.detalhesPorTipo();

            // Normalizar chaves para uppercase para evitar problemas de case sensitive
            Map<String, BigDecimal> detalhesNormalizados = new HashMap<>();
            if (detalhesPorTipo != null) {
                detalhesPorTipo.forEach((k, v) -> {
                    if (k != null) detalhesNormalizados.put(k.toUpperCase(), v);
                });
            }

            Map<String, BigDecimal> detalhesMapeados = new HashMap<>();

            // Mapear PIX
            if (detalhesNormalizados.containsKey("PIX")) {
                detalhesMapeados.put("PIX", detalhesNormalizados.get("PIX"));
            }

            // Mapear DINHEIRO (considerando "DINHEIRO" ou "CASH")
            BigDecimal valorDinheiro = BigDecimal.ZERO;
            if (detalhesNormalizados.containsKey("DINHEIRO")) {
                valorDinheiro = valorDinheiro.add(detalhesNormalizados.get("DINHEIRO"));
            }
            if (detalhesNormalizados.containsKey("CASH")) {
                valorDinheiro = valorDinheiro.add(detalhesNormalizados.get("CASH"));
            }
            if (valorDinheiro.compareTo(BigDecimal.ZERO) > 0) {
                detalhesMapeados.put("DINHEIRO", valorDinheiro);
            }

            // Mapear CARTAO_CREDITO
            if (detalhesNormalizados.containsKey("CARTAO_CREDITO")) {
                detalhesMapeados.put("CARTAO_CREDITO", detalhesNormalizados.get("CARTAO_CREDITO"));
            }

            // Mapear CARTAO_DEBITO
            if (detalhesNormalizados.containsKey("CARTAO_DEBITO")) {
                detalhesMapeados.put("CARTAO_DEBITO", detalhesNormalizados.get("CARTAO_DEBITO"));
            }

            // Mapear VOUCHER
            if (detalhesNormalizados.containsKey("VOUCHER")) {
                detalhesMapeados.put("VOUCHER", detalhesNormalizados.get("VOUCHER"));
            }

            // Retornar novo DTO com tipos mapeados
            return new MovimentoCaixaResumoDTO(
                    resumo.entradas(),
                    resumo.saidas(),
                    resumo.saldo(),
                    detalhesMapeados
            );

        } catch (Exception e) {
            log.error("Erro ao buscar movimento de caixa", e);
            // Retornar valores zerados em caso de erro
            return new MovimentoCaixaResumoDTO(
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new HashMap<>()
            );
        }
    }

    @Transactional(readOnly = true)
    public List<ConsumoVoucherDTO> getConsumoVoucherFuncionariosMesAtual() {
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        LocalDateTime inicio = inicioMes.atStartOfDay();
        LocalDateTime fim = inicio.plusMonths(1);

        List<Object[]> raw = pagamentoRepository.findConsumoVoucherFuncionarios(inicio, fim);
        List<ConsumoVoucherDTO> result = new ArrayList<>();

        for (Object[] row : raw) {
            Long usuarioId = row[0] != null ? ((Number) row[0]).longValue() : null;
            String nome = (String) row[1];
            java.math.BigDecimal total = (java.math.BigDecimal) row[2];
            java.math.BigDecimal voucher = (java.math.BigDecimal) row[3];
            java.math.BigDecimal excedente = java.math.BigDecimal.ZERO;
            if (voucher != null && total != null && total.compareTo(voucher) > 0) {
                excedente = total.subtract(voucher);
            }
            result.add(new ConsumoVoucherDTO(
                    usuarioId,
                    nome,
                    total != null ? total : java.math.BigDecimal.ZERO,
                    voucher,
                    excedente
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public VendasPeriodoDTO getVendasPeriodo(String periodo, String dateFrom, String dateTo) {
        log.info("Buscando vendas do período: {} (custom from: {} to: {})", periodo, dateFrom, dateTo);

        try {
            LocalDateTime fim;
            LocalDateTime inicio;

            if (dateFrom != null && !dateFrom.isBlank() && dateTo != null && !dateTo.isBlank()) {
                // Custom period
                inicio = LocalDate.parse(dateFrom, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
                fim = LocalDate.parse(dateTo, DateTimeFormatter.ISO_LOCAL_DATE).atTime(23, 59, 59, 999_000_000);
            } else {
                // Predefined periods
                fim = LocalDateTime.now();
                switch (periodo != null ? periodo.toLowerCase() : "7d") {
                    case "hoje":
                        inicio = LocalDate.now().atStartOfDay();
                        break;
                    case "30d":
                        inicio = fim.minusDays(30);
                        break;
                    case "7d":
                    default:
                        inicio = fim.minusDays(7);
                        break;
                }
            }

            // Buscar total de vendas e quantidade
            BigDecimal faturamento = pagamentoRepository.sumValorPagoByDate(inicio, fim);
            if (faturamento == null) faturamento = BigDecimal.ZERO;

            Long quantidadeVendas = pagamentoRepository.countVendasByDate(inicio, fim);
            if (quantidadeVendas == null) quantidadeVendas = 0L;

            // Calcular ticket médio
            BigDecimal ticketMedio = BigDecimal.ZERO;
            if (quantidadeVendas > 0 && faturamento.compareTo(BigDecimal.ZERO) > 0) {
                ticketMedio = faturamento.divide(BigDecimal.valueOf(quantidadeVendas), 2, RoundingMode.HALF_UP);
            }

            // Buscar faturamento base e taxa de serviço
            BigDecimal faturamentoBase = pagamentoRepository.sumValorBaseByDate(inicio, fim);
            if (faturamentoBase == null) faturamentoBase = BigDecimal.ZERO;
            BigDecimal taxaServico = pagamentoRepository.sumValorTaxaServicoByDate(inicio, fim);
            if (taxaServico == null) taxaServico = BigDecimal.ZERO;


            // Buscar valores por método de pagamento
            List<Object[]> valoresPorMetodo = pagamentoRepository.findValoresPorMetodoByDate(inicio, fim);

            // Mapear métodos de pagamento (pix, card→cartao, cash→dinheiro)
            BigDecimal pix = BigDecimal.ZERO;
            BigDecimal cartao = BigDecimal.ZERO;
            BigDecimal cartaoCredito = BigDecimal.ZERO;
            BigDecimal cartaoDebito = BigDecimal.ZERO;
            BigDecimal voucher = BigDecimal.ZERO;
            BigDecimal dinheiro = BigDecimal.ZERO;

            for (Object[] row : valoresPorMetodo) {
                String metodo = (String) row[0];
                String cartaoTipo = (String) row[1];
                BigDecimal valor = (BigDecimal) row[2];

                if (metodo == null || valor == null) continue;

                switch (metodo.toLowerCase()) {
                    case "pix":
                        pix = pix.add(valor);
                        break;
                    case "card":
                        cartao = cartao.add(valor);
                        if (cartaoTipo != null && (cartaoTipo.toLowerCase().contains("debito") || cartaoTipo.toLowerCase().contains("debit"))) {
                            cartaoDebito = cartaoDebito.add(valor);
                        } else {
                            cartaoCredito = cartaoCredito.add(valor);
                        }
                        break;
                    case "cash":
                        dinheiro = dinheiro.add(valor);
                        break;
                    case "voucher":
                        voucher = voucher.add(valor);
                        break;
                }
            }

            return VendasPeriodoDTO.builder()
                    .faturamento(faturamento)
                    .quantidadeVendas(quantidadeVendas)
                    .ticketMedio(ticketMedio)
                    .pix(pix)
                    .cartao(cartao)
                    .cartaoCredito(cartaoCredito)
                    .cartaoDebito(cartaoDebito)
                    .voucher(voucher)
                    .dinheiro(dinheiro)
                    .faturamentoBase(faturamentoBase) // Added
                    .taxaServico(taxaServico)     // Added
                    .build();

        } catch (Exception e) {
            log.error("Erro ao buscar vendas do período", e);
            // Retornar valores zerados em caso de erro
            return VendasPeriodoDTO.builder()
                    .faturamento(BigDecimal.ZERO)
                    .quantidadeVendas(0L)
                    .ticketMedio(BigDecimal.ZERO)
                    .pix(BigDecimal.ZERO)
                    .cartao(BigDecimal.ZERO)
                    .cartaoCredito(BigDecimal.ZERO)
                    .cartaoDebito(BigDecimal.ZERO)
                    .voucher(BigDecimal.ZERO)
                    .dinheiro(BigDecimal.ZERO)
                    .faturamentoBase(BigDecimal.ZERO) // Added
                    .taxaServico(BigDecimal.ZERO)     // Added
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public List<VendasHistoricoDTO> getVendasHistorico(String periodo) {
        log.info("Buscando histórico diário de vendas para período: {}", periodo);

        // Determinar intervalo
        LocalDate hoje = LocalDate.now();
        LocalDate dataFinal = hoje;
        LocalDate dataInicial;

        switch (periodo != null ? periodo.toLowerCase() : "30d") {
            case "hoje":
                dataInicial = hoje;
                break;
            case "7d":
                dataInicial = hoje.minusDays(6);
                break;
            case "30d":
            default:
                dataInicial = hoje.minusDays(29);
                break;
        }

        LocalDateTime inicio = dataInicial.atStartOfDay();
        LocalDateTime fim = dataFinal.plusDays(1).atStartOfDay();

        List<Object[]> raw = pagamentoRepository.findVendasAgrupadasPorDia(inicio, fim);
        Map<LocalDate, VendasHistoricoDTO> agregados = new HashMap<>();

        for (Object[] row : raw) {
            LocalDate dia = row[0] instanceof java.sql.Date
                    ? ((java.sql.Date) row[0]).toLocalDate()
                    : (LocalDate) row[0];

            Long quantidade = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            BigDecimal total = row[2] != null ? (BigDecimal) row[2] : BigDecimal.ZERO;

            agregados.put(dia, new VendasHistoricoDTO(dia, total, quantidade));
        }

        List<VendasHistoricoDTO> serie = new ArrayList<>();
        LocalDate cursor = dataInicial;
        while (!cursor.isAfter(dataFinal)) {
            VendasHistoricoDTO ponto = agregados.getOrDefault(
                    cursor,
                    new VendasHistoricoDTO(cursor, BigDecimal.ZERO, 0L)
            );
            serie.add(ponto);
            cursor = cursor.plusDays(1);
        }

        return serie;
    }

    @Transactional(readOnly = true)
    public PedidosLocalDTO getPedidosPorLocal(Integer diasRetroativos) {
        log.info("Buscando pedidos por local dos últimos {} dias", diasRetroativos);

        LocalDateTime dataCorte = LocalDateTime.now().minusDays(diasRetroativos);

        try {
            List<PedidosDiariosDTO> pedidosDiarios = pedidoRepository.findPedidosPorLocal(dataCorte);

            // Reverter a lista para ordem cronológica (a query retorna DESC)
            Collections.reverse(pedidosDiarios);

            if (pedidosDiarios.isEmpty()) {
                log.warn("Nenhum pedido encontrado no período");
            } else {
                log.info("Encontrados pedidos para {} dias", pedidosDiarios.size());
            }

            return PedidosLocalDTO.builder()
                    .pedidosDiarios(pedidosDiarios)
                    .diasAnalisados(diasRetroativos)
                    .build();

        } catch (Exception e) {
            log.error("Erro ao buscar pedidos por local", e);
            throw new RuntimeException("Erro ao buscar pedidos por local: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public FinanceiroResumoDTO getFinanceiro() {
        log.info("Buscando resumo financeiro do dia");

        try {
            LocalDate hoje = LocalDate.now();

            // Recebimentos: parcelas com vencimento hoje
            BigDecimal totalRecebimentosHoje = contaReceberRepository.findValorVencimentoHoje(hoje);
            if (totalRecebimentosHoje == null) totalRecebimentosHoje = BigDecimal.ZERO;

            // Total já recebido hoje
            BigDecimal totalRecebidoHoje = contaReceberRepository.findTotalRecebidoHoje(hoje);
            if (totalRecebidoHoje == null) totalRecebidoHoje = BigDecimal.ZERO;

            // Pendências de recebimento: parcelas vencidas não recebidas
            BigDecimal pendenciasRecebimento = contaReceberRepository.findValorPendente(hoje);
            if (pendenciasRecebimento == null) pendenciasRecebimento = BigDecimal.ZERO;

            // Pagamentos: parcelas com vencimento hoje
            BigDecimal totalPagamentosHoje = contaPagarRepository.findValorVencimentoHoje(hoje);
            if (totalPagamentosHoje == null) totalPagamentosHoje = BigDecimal.ZERO;

            // Total já pago hoje
            BigDecimal totalPagoHoje = contaPagarRepository.findTotalPagoHoje(hoje);
            if (totalPagoHoje == null) totalPagoHoje = BigDecimal.ZERO;

            // Pendências de pagamento: parcelas vencidas não pagas
            BigDecimal pendenciasPagamento = contaPagarRepository.findValorPendente(hoje);
            if (pendenciasPagamento == null) pendenciasPagamento = BigDecimal.ZERO;

            return FinanceiroResumoDTO.builder()
                    .recebimentos(totalRecebimentosHoje)
                    .total_recebido(totalRecebidoHoje)
                    .pagamentos(totalPagamentosHoje)
                    .total_pago(totalPagoHoje)
                    .pendencias_recebimento(pendenciasRecebimento)
                    .pendencias_pagamento(pendenciasPagamento)
                    .build();

        } catch (Exception e) {
            log.error("Erro ao buscar resumo financeiro", e);
            // Retornar valores zerados em caso de erro
            return FinanceiroResumoDTO.builder()
                    .recebimentos(BigDecimal.ZERO)
                    .total_recebido(BigDecimal.ZERO)
                    .pagamentos(BigDecimal.ZERO)
                    .total_pago(BigDecimal.ZERO)
                    .pendencias_recebimento(BigDecimal.ZERO)
                    .pendencias_pagamento(BigDecimal.ZERO)
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public PendenciasDTO getPendencias(boolean somenteAtivos) {
        try {
            Long semPrecoUnificado = produtoRepository.countProdutosSemPrecoUnificado(
                    Arrays.asList(TipoPrecificacao.SIMPLES, TipoPrecificacao.UNIFICADA),
                    somenteAtivos
            );

            Long semPrecoIndividual = produtoRepository.countProdutosIndividuaisSemPreco(
                    TipoPrecificacao.INDIVIDUAL,
                    somenteAtivos
            );

            Long produtosSemPreco = (semPrecoUnificado != null ? semPrecoUnificado : 0L)
                    + (semPrecoIndividual != null ? semPrecoIndividual : 0L);

            Long produtosSemEstoque = produtoRepository.countProdutosSemEstoque(somenteAtivos);
            Long total = (produtosSemPreco != null ? produtosSemPreco : 0L)
                    + (produtosSemEstoque != null ? produtosSemEstoque : 0L);

            return PendenciasDTO.builder()
                    .total(total)
                    .produtosSemPreco(produtosSemPreco)
                    .produtosSemEstoque(produtosSemEstoque)
                    .geradoEm(LocalDateTime.now())
                    .build();
        } catch (Exception e) {
            log.error("Erro ao calcular pendências do dashboard", e);
            return PendenciasDTO.builder()
                    .total(0L)
                    .produtosSemPreco(0L)
                    .produtosSemEstoque(0L)
                    .geradoEm(LocalDateTime.now())
                    .build();
        }
    }
}
