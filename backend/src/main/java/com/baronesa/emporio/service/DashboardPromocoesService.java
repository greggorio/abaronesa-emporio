package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.DashboardPromocoesDTO;
import com.baronesa.emporio.dto.DashboardPromocaoProdutoDTO;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.repository.ItemPedidoRepository;
import com.baronesa.emporio.repository.ProdutoPromocaoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardPromocoesService {

    @Autowired
    private ProdutoPromocaoRepository produtoPromocaoRepository;

    @Autowired
    private ItemPedidoRepository itemPedidoRepository;

    public DashboardPromocoesDTO getDashboardPromocoes(String periodo) {
        // Contar produtos distintos com promoção ativa
        Long produtosEmPromocao = produtoPromocaoRepository.countDistinctProdutosAtivos();

        // Buscar produtos distintos com promoção ativa
        List<Produto> produtosAtivos = produtoPromocaoRepository.findDistinctProdutosAtivos();

        // Preparar dados para cálculo de vendas - período selecionado
        LocalDateTime endDate = LocalDateTime.now();
        LocalDateTime startDate = resolveInicioPeriodo(periodo, endDate);

        // Obter IDs dos produtos para consulta de vendas
        List<Long> produtoIds = new ArrayList<>();

        for (Produto produto : produtosAtivos) {
            produtoIds.add(produto.getId());
        }

        // Obter dados de vendas para os produtos em promoção
        Map<Long, Object[]> vendasMap = new HashMap<>();
        if (!produtoIds.isEmpty()) {
            List<Object[]> vendasData = itemPedidoRepository.findVendasByProdutoIds(produtoIds, startDate, endDate);
            for (Object[] venda : vendasData) {
                vendasMap.put(((Number) venda[0]).longValue(), venda);
            }
        }

        // Obter dados de impacto das promoções
        List<Object[]> impactoData = produtoIds.isEmpty()
                ? new ArrayList<>()
                : itemPedidoRepository.findImpactoVendasByProdutoIds(produtoIds, startDate, endDate);

        // Calcular impacto total de vendas em promoção
        BigDecimal impactoVendas = BigDecimal.ZERO;
        for (Object[] impacto : impactoData) {
            impactoVendas = impactoVendas.add((BigDecimal) impacto[1]);
        }

        // Obter dados de vendas promocionais e normais
        Map<Long, Object[]> vendasPromocionaisNormaisMap = new HashMap<>();
        if (!produtoIds.isEmpty()) {
            List<Object[]> vendasPromocionaisNormaisData = itemPedidoRepository.findVendasPromocionaisNormaisByProdutoIds(produtoIds, startDate, endDate);
            for (Object[] dados : vendasPromocionaisNormaisData) {
                vendasPromocionaisNormaisMap.put(((Number) dados[0]).longValue(), dados);
            }
        }

        // Calcular percentuais de vendas promocionais e normais
        Integer totalPromocional = 0;
        Integer totalNormal = 0;

        for (Object[] dados : vendasPromocionaisNormaisMap.values()) {
            totalPromocional += ((Number) dados[1]).intValue();
            totalNormal += ((Number) dados[2]).intValue();
        }

        Integer vendasPromocao = 0;
        Integer vendasNormais = 0;

        Integer totalVendasGeral = totalPromocional + totalNormal;
        if (totalVendasGeral > 0) {
            vendasPromocao = Math.round(((float) totalPromocional / totalVendasGeral) * 100);
            vendasNormais = 100 - vendasPromocao; // Garantir que a soma seja 100
        }

        // Montar a lista de produtos em promoção com dados reais de vendas
        List<DashboardPromocaoProdutoDTO> produtosEmPromocaoLista = new ArrayList<>();
        BigDecimal totalVendidoGeral = BigDecimal.ZERO;

        for (Produto produto : produtosAtivos) {
            DashboardPromocaoProdutoDTO produtoDTO = new DashboardPromocaoProdutoDTO();
            produtoDTO.setNome(produto.getNome());

            // Determinar o precoOriginal baseado no tipo de precificação
            BigDecimal precoOriginal = getPrecoOriginal(produto);
            produtoDTO.setPrecoOriginal(precoOriginal);

            // Preencher dados com base nas vendas dos últimos 7 dias
            Object[] vendaData = vendasMap.get(produto.getId());
            if (vendaData != null) {
                BigDecimal total = (BigDecimal) vendaData[1];
                Integer vendas = ((Number) vendaData[2]).intValue();
                BigDecimal precoComDesconto = null;
                if (total != null && vendas != null && vendas > 0) {
                    precoComDesconto = total.divide(BigDecimal.valueOf(vendas), 2, RoundingMode.HALF_UP);
                }

                produtoDTO.setTotal(total != null ? total : BigDecimal.ZERO);
                produtoDTO.setVendas(vendas != null ? vendas : 0);
                produtoDTO.setPrecoComDesconto(precoComDesconto != null ? precoComDesconto : BigDecimal.ZERO);

                // Calcular desconto percentual
                if (precoOriginal != null && precoOriginal.compareTo(BigDecimal.ZERO) > 0 && precoComDesconto != null) {
                    BigDecimal descontoPercentual = precoOriginal.subtract(precoComDesconto)
                            .multiply(BigDecimal.valueOf(100))
                            .divide(precoOriginal, 2, RoundingMode.HALF_UP);
                    // Converter para negativo para UI
                    descontoPercentual = descontoPercentual.negate();
                    produtoDTO.setDesconto(descontoPercentual.intValue());
                } else {
                    produtoDTO.setDesconto(0);
                }
            } else {
                // Caso não haja vendas, usar valores padrão
                produtoDTO.setTotal(BigDecimal.ZERO);
                produtoDTO.setVendas(0);
                produtoDTO.setPrecoComDesconto(precoOriginal != null ? precoOriginal : BigDecimal.ZERO);
                produtoDTO.setDesconto(0);
            }

            totalVendidoGeral = totalVendidoGeral.add(produtoDTO.getTotal() != null ? produtoDTO.getTotal() : BigDecimal.ZERO);

            produtosEmPromocaoLista.add(produtoDTO);
        }

        produtosEmPromocaoLista.sort((a, b) -> {
            BigDecimal totalA = a.getTotal() != null ? a.getTotal() : BigDecimal.ZERO;
            BigDecimal totalB = b.getTotal() != null ? b.getTotal() : BigDecimal.ZERO;
            return totalB.compareTo(totalA);
        });

        // Calcular progressWidth baseado na participação do produto no total vendido
        if (totalVendidoGeral.compareTo(BigDecimal.ZERO) > 0) {
            for (DashboardPromocaoProdutoDTO produtoDTO : produtosEmPromocaoLista) {
                BigDecimal progressWidth = produtoDTO.getTotal()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(totalVendidoGeral, 2, RoundingMode.HALF_UP);
                produtoDTO.setProgressWidth(progressWidth.intValue());
            }
        } else {
            for (DashboardPromocaoProdutoDTO produtoDTO : produtosEmPromocaoLista) {
                produtoDTO.setProgressWidth(0);
            }
        }

        // Calcular desconto médio com base nas promoções ativas
        List<Object[]> descontosData = produtoPromocaoRepository.findDescontoMedioByProduto();
        BigDecimal descontoMedio = BigDecimal.ZERO;

        if (!descontosData.isEmpty()) {
            BigDecimal somaDescontos = BigDecimal.ZERO;
            for (Object[] dados : descontosData) {
                BigDecimal desconto = (BigDecimal) dados[1];
                if (desconto != null) {
                    somaDescontos = somaDescontos.add(desconto);
                }
            }
            descontoMedio = somaDescontos.divide(BigDecimal.valueOf(descontosData.size()), 2, RoundingMode.HALF_UP);
            // Converter para negativo para UI
            descontoMedio = descontoMedio.negate();
        }

        DashboardPromocoesDTO response = new DashboardPromocoesDTO(
            Math.toIntExact(produtosEmPromocao), // produtosEmPromocao - usando o valor real do banco
            descontoMedio.intValue(), // descontoMedio - calculado com base em promoções ativas
            impactoVendas, // impactoVendas - calculado com base em vendas reais
            vendasPromocao, // vendasPromocao - calculado com base em vendas reais
            vendasNormais, // vendasNormais - calculado com base em vendas reais
            produtosEmPromocaoLista // produtosEmPromocaoLista - com dados reais de vendas
        );

        return response;
    }

    private LocalDateTime resolveInicioPeriodo(String periodo, LocalDateTime endDate) {
        if (periodo == null) {
            return endDate.minusDays(7);
        }
        switch (periodo.toLowerCase()) {
            case "hoje":
                return endDate.toLocalDate().atStartOfDay();
            case "30d":
                return endDate.minusDays(30);
            case "7d":
            default:
                return endDate.minusDays(7);
        }
    }

    private BigDecimal getPrecoOriginal(Produto produto) {
        if (produto.getTipoPrecificacao().equals(com.baronesa.emporio.enums.TipoPrecificacao.SIMPLES)) {
            // Para produtos com precificação simples, usar precoVenda do produto
            return produto.getPrecoVenda();
        } else {
            // Para produtos com variações, usar o preço do SKU principal ou o primeiro SKU
            BigDecimal precoOriginal = null;
            for (com.baronesa.emporio.entity.ProdutoSKU sku : produto.getSkus()) {
                if (sku.getPrincipal() != null && sku.getPrincipal() && sku.getAtivo()) {
                    precoOriginal = sku.getPrecoVenda();
                    break;
                }
            }

            // Se não encontrar SKU principal ativo, usar o primeiro SKU ativo
            if (precoOriginal == null) {
                for (com.baronesa.emporio.entity.ProdutoSKU sku : produto.getSkus()) {
                    if (sku.getAtivo() && sku.getPrecoVenda() != null) {
                        precoOriginal = sku.getPrecoVenda();
                        break;
                    }
                }
            }

            // Se ainda não encontrar, usar precoVenda do produto como fallback
            if (precoOriginal == null) {
                precoOriginal = produto.getPrecoVenda();
            }

            return precoOriginal;
        }
    }
}
