package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.ContaConvidadoResponse;
import com.baronesa.emporio.dto.ContaMesaResponse;
import com.baronesa.emporio.entity.ItemPedido;
import com.baronesa.emporio.entity.SessaoConvidado;
import com.baronesa.emporio.entity.SessaoMesa;
import com.baronesa.emporio.entity.SessaoCobranca;
import com.baronesa.emporio.enums.StatusItem;
import com.baronesa.emporio.enums.StatusCobranca;
import com.baronesa.emporio.enums.TipoCobranca;
import com.baronesa.emporio.exception.NotFoundException;
import com.baronesa.emporio.repository.ConfiguracaoRepository;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import com.baronesa.emporio.repository.PagamentoRepository;
import com.baronesa.emporio.repository.SessaoConvidadoRepository;
import com.baronesa.emporio.repository.SessaoMesaRepository;
import com.baronesa.emporio.repository.SessaoCobrancaRepository;
import com.baronesa.emporio.util.ConfigManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContaService {

    private final SessaoMesaRepository sessaoMesaRepository;
    private final SessaoConvidadoRepository sessaoConvidadoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final ConfiguracaoRepository configuracaoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final SessaoCobrancaRepository sessaoCobrancaRepository;
    private final ConfigManager configManager;
    private final com.baronesa.emporio.repository.PagamentoAlocacaoRepository pagamentoAlocacaoRepository;

    public ContaMesaResponse contaMesa(Long sessaoMesaId) {
        SessaoMesa sessaoMesa = sessaoMesaRepository.findById(sessaoMesaId)
                .orElseThrow(() -> new NotFoundException("Sessão de mesa não encontrada"));
        if (sessaoMesa.getStatus() == com.baronesa.emporio.enums.StatusSessao.CLOSED) {
            throw new com.baronesa.emporio.exception.SessionClosedException("Sessão da mesa encerrada");
        }

        // Buscar TODOS os itens (incluindo cancelados para histórico)
        List<ItemPedido> todosItens = itemPedidoRepository.findByPedido_SessaoMesa(sessaoMesa);

        // Filtrar apenas não-cancelados para cálculos financeiros
        List<ItemPedido> itensNaoCancelados = todosItens.stream()
                .filter(ip -> ip.getStatus() != StatusItem.CANCELED)
                .collect(Collectors.toList());

        long subtotalMesa = somarCentavos(itensNaoCancelados);
        long taxaServicoSugerida = isTaxaServicoAtiva() ? calcularTaxaServico(subtotalMesa) : 0;
        long descontos = 0; // MVP: 0
        PagosResumo pagosMesa = somaPagosMesaDetalhado(sessaoMesa);
        long pagoBase = pagosMesa.baseCentavos();
        long pagoTaxa = pagosMesa.taxaCentavos();
        long pagoCouvert = pagosMesa.couvertCentavos();
        long pagoTotal = pagoBase + pagoTaxa;
        long devidoConsumo = Math.max(0, subtotalMesa - pagoBase); // fechamento deve considerar apenas consumo
        long taxaPendente = Math.max(0, taxaServicoSugerida - pagoTaxa);
        long totalMesa = subtotalMesa + taxaServicoSugerida - descontos;

        // Verificar se couvert artístico está ativo
        boolean couvertAtivo = configManager.getBooleanConfig("couvert_artistico_ativo", false);

        long couvertTotal = 0;
        long couvertPagoTotal = 0;
        long couvertDevidoTotal = 0;

        Map<Long, Long> couvertPorConvidado = new HashMap<>();

        if (couvertAtivo) {
            // Calcular couverts ativos e não isentos apenas se couvert estiver ativo
            List<SessaoCobranca> cobrancas = sessaoCobrancaRepository.findBySessaoMesaIdAndStatus(sessaoMesaId, StatusCobranca.ATIVA);

            for (SessaoCobranca cobranca : cobrancas) {
                if (cobranca.getTipo() == TipoCobranca.COUVERT_ARTISTICO && !cobranca.getIsento()) {
                    long valorCentavos = cobranca.getValor().movePointRight(2).longValue();
                    couvertTotal += valorCentavos;
                    couvertPorConvidado.merge(cobranca.getSessaoConvidadoId(), valorCentavos, Long::sum);
                }
            }
        }

        Map<SessaoConvidado, List<ItemPedido>> porConvidado = itensNaoCancelados.stream()
                .collect(Collectors.groupingBy(ip -> ip.getPedido().getSessaoConvidado()));
        List<SessaoConvidado> convidadosSessao = sessaoConvidadoRepository.findBySessaoMesa_Id(sessaoMesaId);
        Map<Long, Long> couvertPagoPorConvidado = new HashMap<>();
        if (couvertAtivo && couvertTotal > 0) {
            // Apenas pagamentos com sessaoConvidado (ou alocações) contam para o convidado
            for (var p : pagamentoRepository.findBySessaoMesaAndStatus(sessaoMesa, com.baronesa.emporio.enums.StatusPagamento.PAID)) {
                long valorCouvert = valorCouvertCentavos(p);
                if (valorCouvert <= 0) continue;
                if (p.getSessaoConvidado() != null) {
                    couvertPagoPorConvidado.merge(p.getSessaoConvidado().getId(), valorCouvert, Long::sum);
                }
            }
        }
        List<ContaMesaResponse.Pessoa> pessoas = new ArrayList<>();
        for (SessaoConvidado convidado : convidadosSessao) {
            List<ItemPedido> itensPessoa = porConvidado.getOrDefault(convidado, java.util.Collections.emptyList());
            long subtotal = somarCentavos(itensPessoa);
            long ajustes = 0; // MVP
            PagosResumo pagosPessoa = somaPagosConvidadoDetalhado(convidado);
            long pagoPessoaBase = pagosPessoa.baseCentavos();
            long pagoPessoaTaxa = pagosPessoa.taxaCentavos();
            long pagoPessoaTotal = pagoPessoaBase + pagoPessoaTaxa;
            long devidoPessoa = Math.max(0, subtotal + ajustes - pagoPessoaBase);

            long taxaSugPessoa = 0;
            if (taxaServicoSugerida > 0 && subtotalMesa > 0) {
                taxaSugPessoa = (taxaServicoSugerida * subtotal) / subtotalMesa; // proporcional ao subtotal
            }
            long taxaPendPessoa = Math.max(0, taxaSugPessoa - pagoPessoaTaxa);

            long couvertPessoa = couvertPorConvidado.getOrDefault(convidado.getId(), 0L);
            long couvertPagoPessoa = Math.min(couvertPessoa, couvertPagoPorConvidado.getOrDefault(convidado.getId(), 0L));
            long couvertDevidoPessoa = Math.max(0, couvertPessoa - couvertPagoPessoa);
            long devidoTotalPessoa = devidoPessoa + taxaPendPessoa + couvertDevidoPessoa;

            pessoas.add(new ContaMesaResponse.Pessoa(
                    convidado.getId(),
                    convidado.getNomeExibicao(),
                    subtotal,
                    ajustes,
                    pagoPessoaTotal,
                    devidoPessoa,
                    pagoPessoaBase,
                    pagoPessoaTaxa,
                    taxaSugPessoa,
                    taxaPendPessoa,
                    couvertPessoa,
                    couvertPagoPessoa,
                    couvertDevidoPessoa,
                    devidoTotalPessoa
            ));
        }

        if (couvertAtivo) {
            couvertPagoTotal = Math.min(couvertTotal, pagoCouvert);
            couvertDevidoTotal = Math.max(0, couvertTotal - couvertPagoTotal);
        }

        long devidoTotal = devidoConsumo + taxaPendente + couvertDevidoTotal;
        boolean selfCheckoutLiberado = Boolean.TRUE.equals(sessaoMesa.getSelfCheckoutLiberado());

        return new ContaMesaResponse(
                totalMesa,                  // total com taxa sugerida
                taxaServicoSugerida,        // taxa sugerida (compat)
                descontos,
                pagoTotal,                  // pago base + taxa (compat)
                devidoConsumo,              // devido de consumo (base) - NÃO ALTERADO
                pessoas,
                subtotalMesa,
                pagoBase,
                pagoTaxa,
                pagoTaxa,                   // alias para pagoTaxaServicoCentavos
                taxaPendente,
                couvertTotal,               // total de couvert lançado
                couvertPagoTotal,           // pago de couvert
                couvertDevidoTotal,         // devido de couvert
                devidoTotal,                // devido total (base + taxa + couvert)
                selfCheckoutLiberado
        );
    }

    // Nenhum método adicional necessário para cálculo de pagamento de couvert
    // pois pagoCouvertCentavos é 0 (por enquanto)

    public ContaConvidadoResponse contaConvidado(Long sessaoConvidadoId) {
        SessaoConvidado convidado = sessaoConvidadoRepository.findById(sessaoConvidadoId)
                .orElseThrow(() -> new NotFoundException("Convidado não encontrado"));
        if (convidado.getSessaoMesa() != null && convidado.getSessaoMesa().getStatus() == com.baronesa.emporio.enums.StatusSessao.CLOSED) {
            throw new com.baronesa.emporio.exception.SessionClosedException("Sessão da mesa encerrada");
        }

        // Buscar TODOS os itens (incluindo cancelados para histórico)
        List<ItemPedido> todosItens = itemPedidoRepository.findByPedido_SessaoConvidado(convidado);

        // Filtrar apenas não-cancelados para cálculos financeiros
        List<ItemPedido> itensNaoCancelados = todosItens.stream()
                .filter(ip -> ip.getStatus() != StatusItem.CANCELED)
                .collect(Collectors.toList());

        long subtotal = somarCentavos(itensNaoCancelados);
        long ajustes = 0; // MVP
        PagosResumo pagos = somaPagosConvidadoDetalhado(convidado);
        long pagoBase = pagos.baseCentavos(); // consumo
        long devidoBase = Math.max(0, subtotal + ajustes - pagoBase);

        boolean couvertAtivo = configManager.getBooleanConfig("couvert_artistico_ativo", false);
        long couvertTotal = 0;
        long couvertPago = 0;
        long couvertDevido = 0;
        if (couvertAtivo) {
            List<SessaoCobranca> cobrancas = sessaoCobrancaRepository.findBySessaoConvidadoIdAndStatus(
                    convidado.getId(), StatusCobranca.ATIVA);
            for (SessaoCobranca cobranca : cobrancas) {
                if (cobranca.getTipo() == TipoCobranca.COUVERT_ARTISTICO && !cobranca.getIsento()) {
                    long valorCentavos = cobranca.getValor().movePointRight(2).longValue();
                    couvertTotal += valorCentavos;
                }
            }
            couvertPago = Math.min(couvertTotal, pagos.couvertCentavos());
            couvertDevido = Math.max(0, couvertTotal - couvertPago);
        }

        // Retornar TODOS os itens (incluindo cancelados) no DTO para histórico
        List<ContaConvidadoResponse.Item> itensDto = todosItens.stream().map(ip -> {
            String nome = ip.getProduto().getNome();
            String variacao = (ip.getSku() != null && ip.getSku().getVariacao() != null) ? ip.getSku().getVariacao() : null;
            if (variacao != null && !variacao.isBlank()) {
                nome = nome + " (" + variacao + ")";
            }
            return new ContaConvidadoResponse.Item(
                    ip.getPedido().getId(),
                    ip.getPedido().getStatus() != null ? ip.getPedido().getStatus().name().toLowerCase() : "",
                    ip.getPedido().getCriadoEm(),
                    ip.getId(),
                    ip.getProduto().getId(),
                    nome,
                    ip.getQuantidade(),
                    ip.getPrecoUnitario().movePointRight(2).longValue(),
                    ip.getStatus().name().toLowerCase(),
                    ip.getObservacoes()
            );
        }).collect(Collectors.toList());

        Long grupoClienteId = null;
        String grupoClienteDescricao = null;
        if (convidado.getUsuario() != null
                && convidado.getUsuario().getPerfilCliente() != null
                && convidado.getUsuario().getPerfilCliente().getGrupoCliente() != null) {
            grupoClienteId = convidado.getUsuario().getPerfilCliente().getGrupoCliente().getId();
            grupoClienteDescricao = convidado.getUsuario().getPerfilCliente().getGrupoCliente().getDescricao();
        }

        return new ContaConvidadoResponse(
                convidado.getId(),
                convidado.getNomeExibicao(),
                grupoClienteId,
                grupoClienteDescricao,
                subtotal,
                ajustes,
                pagoBase,
                devidoBase + couvertDevido,
                itensDto
        );
    }

    private long somarCentavos(List<ItemPedido> itens) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemPedido ip : itens) {
            BigDecimal line = ip.getPrecoUnitario().multiply(BigDecimal.valueOf(ip.getQuantidade()));
            total = total.add(line);
        }
        return total.movePointRight(2).longValue();
    }

    private long calcularTaxaServico(long subtotalCentavos) {
        String valor = configuracaoRepository.findValorByChave("taxa_servico_percentual");
        if (valor == null || valor.isBlank()) return 0;
        try {
            BigDecimal pct = new BigDecimal(valor.trim()); // ex.: 10 para 10%
            BigDecimal subtotal = BigDecimal.valueOf(subtotalCentavos, 2);
            BigDecimal fee = subtotal.multiply(pct).divide(BigDecimal.valueOf(100));
            return fee.movePointRight(2).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private PagosResumo somaPagosMesaDetalhado(SessaoMesa sessaoMesa) {
        long base = 0;
        long taxa = 0;
        long couvert = 0;
        for (var p : pagamentoRepository.findBySessaoMesaAndStatus(sessaoMesa, com.baronesa.emporio.enums.StatusPagamento.PAID)) {
            base += valorBaseCentavos(p);
            taxa += valorTaxaCentavos(p);
            couvert += valorCouvertCentavos(p);
        }
        return new PagosResumo(base, taxa, couvert);
    }

    private PagosResumo somaPagosConvidadoDetalhado(SessaoConvidado convidado) {
        long base = 0;
        long taxa = 0;
        long couvert = 0;
        for (var p : pagamentoRepository.findBySessaoConvidadoAndStatus(convidado, com.baronesa.emporio.enums.StatusPagamento.PAID)) {
            base += valorBaseCentavos(p);
            taxa += valorTaxaCentavos(p);
            couvert += valorCouvertCentavos(p);
        }
        // Alocações de pagamentos de mesa (sessaoConvidado null) para este convidado
        try {
            java.util.List<com.baronesa.emporio.entity.PagamentoAlocacao> alocacoes = pagamentoAlocacaoRepository.findBySessaoConvidado(convidado);
            if (alocacoes != null && !alocacoes.isEmpty()) {
                // Agrupar por pagamento para distribuir taxa e couvert proporcionalmente ao valor alocado
                java.util.Map<com.baronesa.emporio.entity.Pagamento, java.util.List<com.baronesa.emporio.entity.PagamentoAlocacao>> porPagamento =
                        alocacoes.stream().collect(java.util.stream.Collectors.groupingBy(com.baronesa.emporio.entity.PagamentoAlocacao::getPagamento));

                for (var entry : porPagamento.entrySet()) {
                    com.baronesa.emporio.entity.Pagamento pag = entry.getKey();
                    if (pag.getStatus() != com.baronesa.emporio.enums.StatusPagamento.PAID) continue;
                    java.util.List<com.baronesa.emporio.entity.PagamentoAlocacao> lista = entry.getValue();
                    long totalAlocadoPagamento = lista.stream()
                            .map(a -> a.getValor().movePointRight(2).longValue())
                            .reduce(0L, Long::sum);
                    long taxaTotal = valorTaxaCentavos(pag);
                    long couvertTotal = valorCouvertCentavos(pag);
                    long restanteTaxa = taxaTotal;
                    long restanteCouvert = couvertTotal;
                    for (int i = 0; i < lista.size(); i++) {
                        var al = lista.get(i);
                        long baseAloc = al.getValor().movePointRight(2).longValue();
                        base += baseAloc;
                        // Distribuir taxa proporcional ao base alocado
                        long taxaShare = (i == lista.size() - 1)
                                ? Math.max(0, restanteTaxa)
                                : Math.floorDiv(taxaTotal * baseAloc, Math.max(1, totalAlocadoPagamento));
                        restanteTaxa = Math.max(0, restanteTaxa - taxaShare);
                        taxa += taxaShare;
                        // Distribuir couvert proporcional ao base alocado (mesma regra)
                        long couvertShare = (i == lista.size() - 1)
                                ? Math.max(0, restanteCouvert)
                                : Math.floorDiv(couvertTotal * baseAloc, Math.max(1, totalAlocadoPagamento));
                        restanteCouvert = Math.max(0, restanteCouvert - couvertShare);
                        couvert += couvertShare;
                    }
                }
            }
        } catch (Exception ignored) {}
        return new PagosResumo(base, taxa, couvert);
    }

    private long valorBaseCentavos(com.baronesa.emporio.entity.Pagamento p) {
        BigDecimal base = p.getValorBase() != null ? p.getValorBase() : p.getValor();
        return base != null ? base.movePointRight(2).longValue() : 0L;
    }

    private long valorTaxaCentavos(com.baronesa.emporio.entity.Pagamento p) {
        BigDecimal taxa = p.getValorTaxaServico();
        return taxa != null ? taxa.movePointRight(2).longValue() : 0L;
    }

    private long valorCouvertCentavos(com.baronesa.emporio.entity.Pagamento p) {
        BigDecimal couvert = p.getValorCouvert();
        return couvert != null ? couvert.movePointRight(2).longValue() : 0L;
    }

    private boolean isTaxaServicoAtiva() {
        try {
            String v = configuracaoRepository.findValorByChave("taxa_servico_ativa");
            if (v == null) return false;
            return "true".equalsIgnoreCase(v) || "1".equals(v.trim()) || "sim".equalsIgnoreCase(v.trim());
        } catch (Exception e) {
            return false;
        }
    }

    private record PagosResumo(long baseCentavos, long taxaCentavos, long couvertCentavos) {}
}
