package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.relatorios.*;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import com.baronesa.emporio.util.ConfigManager;
import com.baronesa.emporio.repository.ProdutoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelatorioVendasProdutoService {

    private final ItemPedidoRepository itemPedidoRepository;
    private final ProdutoRepository produtoRepository;
    private final PdfGeneratorService pdfGeneratorService;
    private final ConfigManager configManager;

    @Transactional(readOnly = true)
    public byte[] gerarRelatorioPdf(LocalDate dataInicio, LocalDate dataFim, Long produtoId, boolean detalhado) {
        LocalDateTime inicio = dataInicio.atStartOfDay();
        LocalDateTime fim = dataFim.atTime(LocalTime.MAX);
        
        String produtoNome = null;
        if (produtoId != null) {
            produtoNome = produtoRepository.findById(produtoId)
                    .map(p -> p.getNome())
                    .orElse("Produto não encontrado");
        }

        if (detalhado) {
            return gerarRelatorioAnalitico(dataInicio, dataFim, inicio, fim, produtoId, produtoNome);
        } else {
            return gerarRelatorioSintetico(dataInicio, dataFim, inicio, fim, produtoId, produtoNome);
        }
    }

    private byte[] gerarRelatorioSintetico(LocalDate dataInicio, LocalDate dataFim, LocalDateTime inicio, LocalDateTime fim, Long produtoId, String produtoNome) {
        List<Object[]> resultados = itemPedidoRepository.findVendasPorProduto(inicio, fim, produtoId);
        
        BigDecimal faturamentoTotal = BigDecimal.ZERO;
        long totalItens = 0;
        List<ProdutoVendaDTO> produtos = new ArrayList<>();

        for (Object[] row : resultados) {
            BigDecimal valorTotal = toBigDecimal(row[3]);
            long quantidade = toLong(row[2]);
            
            faturamentoTotal = faturamentoTotal.add(valorTotal);
            totalItens += quantidade;

            produtos.add(ProdutoVendaDTO.builder()
                    .produtoId(toLong(row[0]))
                    .nome((String) row[1])
                    .quantidade(quantidade)
                    .valorTotal(valorTotal)
                    .valorUnitarioMedio(toBigDecimal(row[4]))
                    .build());
        }

        for (ProdutoVendaDTO p : produtos) {
            if (faturamentoTotal.compareTo(BigDecimal.ZERO) > 0) {
                p.setPercentualParticipacao(p.getValorTotal()
                        .multiply(new BigDecimal("100"))
                        .divide(faturamentoTotal, 2, RoundingMode.HALF_UP));
            } else {
                p.setPercentualParticipacao(BigDecimal.ZERO);
            }
        }

        ResumoVendasProdutoDTO resumo = montarResumo(faturamentoTotal, totalItens, (long) produtos.size());

        RelatorioVendasProdutoDTO relatorio = new RelatorioVendasProdutoDTO(
                buscarDadosEmpresa(),
                new RelatorioVendasFiltroDTO(dataInicio, dataFim, produtoNome),
                resumo,
                produtos,
                LocalDateTime.now(),
                "Sistema"
        );

        return gerarPdf("relatorio-vendas-produtos", Map.of("relatorio", relatorio));
    }

    private byte[] gerarRelatorioAnalitico(LocalDate dataInicio, LocalDate dataFim, LocalDateTime inicio, LocalDateTime fim, Long produtoId, String produtoNome) {
        List<Object[]> resultados = itemPedidoRepository.findVendasAnalitico(inicio, fim, produtoId);
        
        BigDecimal faturamentoTotal = BigDecimal.ZERO;
        long totalItens = 0;
        List<VendaItemDetalheDTO> itens = new ArrayList<>();

        for (Object[] row : resultados) {
            BigDecimal valorTotal = toBigDecimal(row[5]);
            long quantidade = toLong(row[3]);
            
            faturamentoTotal = faturamentoTotal.add(valorTotal);
            totalItens += quantidade;

            itens.add(VendaItemDetalheDTO.builder()
                    .dataHora(toLocalDateTime(row[0]))
                    .cupomId(toLong(row[1]))
                    .produtoNome((String) row[2])
                    .quantidade(quantidade)
                    .precoUnitario(toBigDecimal(row[4]))
                    .valorTotal(valorTotal)
                    .build());
        }

        ResumoVendasProdutoDTO resumo = montarResumo(faturamentoTotal, totalItens, (long) itens.size());

        RelatorioVendasAnaliticoDTO relatorio = new RelatorioVendasAnaliticoDTO(
                buscarDadosEmpresa(),
                new RelatorioVendasFiltroDTO(dataInicio, dataFim, produtoNome),
                resumo,
                itens,
                LocalDateTime.now(),
                "Sistema"
        );

        return gerarPdf("relatorio-vendas-produtos-analitico", Map.of("relatorio", relatorio));
    }

    private ResumoVendasProdutoDTO montarResumo(BigDecimal faturamentoTotal, long totalItens, long quantidadeRegistros) {
        return ResumoVendasProdutoDTO.builder()
                .faturamentoTotal(faturamentoTotal)
                .totalItensVendidos(totalItens)
                .quantidadeProdutosDistintos(quantidadeRegistros)
                .ticketMedio(totalItens > 0 ? faturamentoTotal.divide(new BigDecimal(totalItens), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .build();
    }

    private byte[] gerarPdf(String template, Map<String, Object> variables) {
        try {
            return pdfGeneratorService.generatePdfFromTemplate(template, variables);
        } catch (Exception e) {
            log.error("Erro ao gerar PDF do relatório: {}", template, e);
            throw new RuntimeException("Falha ao gerar o arquivo PDF", e);
        }
    }

    private Long toLong(Object val) {
        return val == null ? 0L : ((Number) val).longValue();
    }

    private BigDecimal toBigDecimal(Object val) {
        return val == null ? BigDecimal.ZERO : (BigDecimal) val;
    }

    private LocalDateTime toLocalDateTime(Object val) {
        if (val == null) return null;
        if (val instanceof Timestamp) return ((Timestamp) val).toLocalDateTime();
        if (val instanceof LocalDateTime) return (LocalDateTime) val;
        return null;
    }

    private DadosEmpresaDTO buscarDadosEmpresa() {
        return new DadosEmpresaDTO(
                configManager.getConfig("nfe_razao_social", ""),
                configManager.getConfig("nfe_nome_fantasia", ""),
                configManager.getConfig("nfe_cnpj", ""),
                configManager.getConfig("nfe_inscricao_estadual", ""),
                configManager.getConfig("nfe_logradouro", ""),
                configManager.getConfig("nfe_numero", ""),
                configManager.getConfig("nfe_bairro", ""),
                configManager.getConfig("nfe_municipio", ""),
                configManager.getConfig("nfe_uf", ""),
                configManager.getConfig("nfe_cep", ""),
                configManager.getConfig("nfe_telefone", ""),
                configManager.getConfig("nfe_logo_path", "")
        );
    }
}
