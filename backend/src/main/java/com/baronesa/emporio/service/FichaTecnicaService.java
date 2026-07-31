package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.FichaTecnicaDTO;
import com.baronesa.emporio.dto.FichaTecnicaItemDTO;
import com.baronesa.emporio.dto.FichaTecnicaRequest;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FichaTecnicaService {

    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final FichaTecnicaItemRepository fichaTecnicaItemRepository;
    private final ProdutoRepository produtoRepository;
    private final ProdutoSKURepository produtoSKURepository;

    @Transactional(readOnly = true)
    public FichaTecnicaDTO buscarPorProduto(Long produtoId) {
        return fichaTecnicaRepository.findByProdutoIdWithItens(produtoId)
                .map(this::toDTO)
                .orElseGet(() -> criarFichaTecnicaVazia(produtoId));
    }

    @Transactional
    public FichaTecnicaDTO salvar(FichaTecnicaRequest request) {
        Produto produto = produtoRepository.findById(request.getProdutoId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        FichaTecnica ficha = fichaTecnicaRepository.findByProdutoId(request.getProdutoId())
                .orElseGet(() -> FichaTecnica.builder()
                        .produto(produto)
                        .rendimento(1)
                        .build());

        ficha.setRendimento(request.getRendimento() != null ? request.getRendimento() : 1);
        ficha.setObservacoes(request.getObservacoes());

        // Limpar itens antigos
        ficha.getItens().clear();

        // Adicionar novos itens
        if (request.getItens() != null) {
            for (FichaTecnicaRequest.FichaTecnicaItemRequest itemRequest : request.getItens()) {
                ProdutoSKU insumoSku = produtoSKURepository.findByIdWithProduto(itemRequest.getInsumoSkuId())
                        .orElseThrow(() -> new RuntimeException("SKU do insumo não encontrado"));

                FichaTecnicaItem item = FichaTecnicaItem.builder()
                        .insumoSku(insumoSku)
                        .quantidade(itemRequest.getQuantidade())
                        .ordem(itemRequest.getOrdem() != null ? itemRequest.getOrdem() : 0)
                        .observacao(itemRequest.getObservacao())
                        .build();

                ficha.adicionarItem(item);
            }
        }

        // Calcular custo total
        ficha.calcularCustoTotal();

        // Atualizar preço de custo do produto baseado na ficha técnica
        produto.setPrecoCusto(ficha.getCustoTotal());

        // Salvar
        ficha = fichaTecnicaRepository.save(ficha);
        produtoRepository.save(produto);

        // Marcar produto como tendo ficha técnica
        if (ficha.getItens() != null && !ficha.getItens().isEmpty()) {
            produto.setTemFichaTecnica(true);
            produtoRepository.save(produto);
        }

        log.info("Ficha técnica salva para produto {} - Custo total: {}", produto.getNome(), ficha.getCustoTotal());

        return toDTO(ficha);
    }

    @Transactional(readOnly = true)
    public BigDecimal calcularCusto(Long produtoId) {
        return fichaTecnicaRepository.findByProdutoIdWithItens(produtoId)
                .map(ficha -> {
                    ficha.calcularCustoTotal();
                    return ficha.getCustoTotal();
                })
                .orElse(BigDecimal.ZERO);
    }

    private FichaTecnicaDTO criarFichaTecnicaVazia(Long produtoId) {
        Produto produto = produtoRepository.findById(produtoId)
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        return FichaTecnicaDTO.builder()
                .produtoId(produtoId)
                .produtoNome(produto.getNome())
                .custoTotal(BigDecimal.ZERO)
                .rendimento(1)
                .itens(new ArrayList<>())
                .build();
    }

    private FichaTecnicaDTO toDTO(FichaTecnica ficha) {
        List<FichaTecnicaItemDTO> itensDTO = ficha.getItens() != null
                ? ficha.getItens().stream()
                .map(this::toItemDTO)
                .collect(Collectors.toList())
                : new ArrayList<>();

        return FichaTecnicaDTO.builder()
                .id(ficha.getId())
                .produtoId(ficha.getProduto().getId())
                .produtoNome(ficha.getProduto().getNome())
                .custoTotal(ficha.getCustoTotal())
                .rendimento(ficha.getRendimento())
                .observacoes(ficha.getObservacoes())
                .itens(itensDTO)
                .criadoEm(ficha.getCriadoEm())
                .atualizadoEm(ficha.getAtualizadoEm())
                .build();
    }

    private FichaTecnicaItemDTO toItemDTO(FichaTecnicaItem item) {
        ProdutoSKU sku = item.getInsumoSku();
        Produto produto = sku.getProduto();

        Integer estoqueDisponivel = sku.getEstoque() != null ? sku.getEstoque().getQuantidade() : 0;

        return FichaTecnicaItemDTO.builder()
                .id(item.getId())
                .insumoSkuId(sku.getId())
                .insumoSkuCodigo(sku.getSku())
                .insumoProdutoId(produto.getId())
                .insumoProdutoNome(produto.getNome())
                .insumoVariacao(sku.getVariacao())
                .embalagemId(sku.getEmbalagem() != null ? sku.getEmbalagem().getId() : null)
                .embalagemNome(sku.getEmbalagem() != null ? sku.getEmbalagem().getNome() : null)
                .fatorBase(sku.getEmbalagem() != null ? sku.getEmbalagem().getFatorBase() : null)
                .quantidade(item.getQuantidade())
                .custoUnitario(resolverCustoUnitario(sku, produto))
                .custoTotal(item.calcularCusto())
                .ordem(item.getOrdem())
                .observacao(item.getObservacao())
                .estoqueDisponivel(estoqueDisponivel)
                .build();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> buscarInsumosDisponiveis(String search) {
        // Buscar todos os SKUs ativos de produtos marcados como insumo
        List<ProdutoSKU> skus = produtoSKURepository.findAll().stream()
                .filter(sku -> Boolean.TRUE.equals(sku.getAtivo()))
                .filter(sku -> sku.getProduto() != null && Boolean.TRUE.equals(sku.getProduto().getAtivo()))
                .filter(sku -> Boolean.TRUE.equals(sku.getProduto().getInsumo())) // Apenas insumos
                .collect(Collectors.toList());

        // Filtrar por busca se fornecida
        if (search != null && !search.trim().isEmpty()) {
            String searchLower = search.toLowerCase();
            skus = skus.stream()
                    .filter(sku ->
                            sku.getProduto().getNome().toLowerCase().contains(searchLower) ||
                            (sku.getSku() != null && sku.getSku().toLowerCase().contains(searchLower)) ||
                            (sku.getCodigoBarras() != null && sku.getCodigoBarras().toLowerCase().contains(searchLower)) ||
                            (sku.getVariacao() != null && sku.getVariacao().toLowerCase().contains(searchLower))
                    )
                    .collect(Collectors.toList());
        }

        // Limitar a 50 resultados
        return skus.stream()
                .limit(50)
                .map(sku -> {
                    Produto produto = sku.getProduto();
                    Map<String, Object> item = new LinkedHashMap<>();

                    // Informações do SKU
                    item.put("value", sku.getId());
                    item.put("label", formatarLabelSku(sku));

                    // Informações do produto
                    item.put("produtoId", produto.getId());
                    item.put("descricao", produto.getNome());
                    item.put("variacao", sku.getVariacao());

                    // Custo
                    BigDecimal precoCusto = resolverCustoUnitario(sku, produto);
                    item.put("precoCusto", precoCusto != null ? precoCusto.doubleValue() : 0.0);

                    // Estoque vem do SKU
                    Integer estoqueAtual = sku.getEstoque() != null ? sku.getEstoque().getQuantidade() : 0;
                    item.put("estoqueAtual", estoqueAtual);

                    // Unidade
                    item.put("unidade", produto.getUnidadeBase() != null ? produto.getUnidadeBase().name() : null);

                    return item;
                })
                .collect(Collectors.toList());
    }

    private String formatarLabelSku(ProdutoSKU sku) {
        StringBuilder label = new StringBuilder(sku.getProduto().getNome());

        if (sku.getVariacao() != null && !sku.getVariacao().trim().isEmpty()) {
            label.append(" - ").append(sku.getVariacao());
        }

        if (sku.getEmbalagem() != null && sku.getEmbalagem().getNome() != null) {
            label.append(" (").append(sku.getEmbalagem().getNome()).append(")");
        }

        return label.toString();
    }

    private BigDecimal resolverCustoUnitario(ProdutoSKU sku, Produto produto) {
        BigDecimal custoSku = sku.getPrecoCusto();
        if (custoSku != null && custoSku.compareTo(BigDecimal.ZERO) > 0) {
            return custoSku;
        }

        BigDecimal custoProduto = produto != null ? produto.getPrecoCusto() : null;
        if (custoProduto != null && custoProduto.compareTo(BigDecimal.ZERO) > 0) {
            return custoProduto;
        }

        return BigDecimal.ZERO;
    }
}
