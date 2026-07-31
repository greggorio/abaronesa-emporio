package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.MovimentoEstoqueDTO;
import com.baronesa.emporio.dto.MovimentoEstoqueRequest;
import com.baronesa.emporio.dto.ProducaoDTO;
import com.baronesa.emporio.dto.ProducaoRequest;
import com.baronesa.emporio.entity.FichaTecnica;
import com.baronesa.emporio.entity.FichaTecnicaItem;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.enums.TipoMovimentoEstoque;
import com.baronesa.emporio.repository.FichaTecnicaRepository;
import com.baronesa.emporio.repository.ProdutoRepository;
import com.baronesa.emporio.repository.ProdutoSKURepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProducaoService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoSKURepository produtoSKURepository;
    private final FichaTecnicaRepository fichaTecnicaRepository;
    private final MovimentoEstoqueService movimentoEstoqueService;

    @Transactional
    public ProducaoDTO produzir(ProducaoRequest request) {
        if (request.getProdutoId() == null) {
            throw new RuntimeException("Produto não informado para produção");
        }

        Produto produto = produtoRepository.findById(request.getProdutoId())
                .orElseThrow(() -> new RuntimeException("Produto não encontrado"));

        if (!Boolean.TRUE.equals(produto.getProducaoPropria())) {
            throw new RuntimeException("Produto não está configurado como produção própria");
        }

        if (!Boolean.TRUE.equals(produto.getTemFichaTecnica())) {
            throw new RuntimeException("Produto não possui ficha técnica");
        }

        if (produto.getControlaEstoque() != null && !produto.getControlaEstoque()) {
            throw new RuntimeException("Produto não controla estoque");
        }

        FichaTecnica ficha = fichaTecnicaRepository.findByProdutoIdWithItens(produto.getId())
                .orElseThrow(() -> new RuntimeException("Ficha técnica não encontrada"));

        if (ficha.getItens() == null || ficha.getItens().isEmpty()) {
            throw new RuntimeException("Ficha técnica sem ingredientes");
        }

        ProdutoSKU skuProduzido = resolverSkuProduzido(produto, request.getSkuId());

        String referencia = "PRODUCAO PRODUTO #" + produto.getId();
        String observacaoBase = request.getObservacao() != null ? request.getObservacao() :
                String.format("Produção própria: %s", produto.getNome());

        List<MovimentoEstoqueDTO> movimentosInsumos = new ArrayList<>();
        for (FichaTecnicaItem ingrediente : ficha.getItens()) {
            BigDecimal quantidade = ingrediente.getQuantidade() != null ? ingrediente.getQuantidade() : BigDecimal.ZERO;

            MovimentoEstoqueRequest movimentoRequest = MovimentoEstoqueRequest.builder()
                    .skuId(ingrediente.getInsumoSku().getId())
                    .tipoMovimento(TipoMovimentoEstoque.CONSUMO_PRODUCAO.getCodigo())
                    .quantidade(quantidade)
                    .observacao(String.format("Consumo produção: %s", produto.getNome()))
                    .documentoReferencia(referencia)
                    .build();

            movimentosInsumos.add(movimentoEstoqueService.movimentarEstoque(movimentoRequest));
        }

        MovimentoEstoqueDTO movimentoProduto = movimentoEstoqueService.movimentarEstoque(
                MovimentoEstoqueRequest.builder()
                        .skuId(skuProduzido.getId())
                        .tipoMovimento(TipoMovimentoEstoque.PRODUCAO.getCodigo())
                        .quantidade(BigDecimal.ONE)
                        .observacao(observacaoBase)
                        .documentoReferencia(referencia)
                        .build()
        );

        log.info("Produção registrada para produto {} (SKU {}): {} insumos consumidos",
                produto.getNome(), skuProduzido.getId(), movimentosInsumos.size());

        return ProducaoDTO.builder()
                .produtoId(produto.getId())
                .skuId(skuProduzido.getId())
                .quantidade(1)
                .movimentoProduto(movimentoProduto)
                .movimentosInsumos(movimentosInsumos)
                .build();
    }

    private ProdutoSKU resolverSkuProduzido(Produto produto, Long skuId) {
        if (skuId != null) {
            ProdutoSKU sku = produtoSKURepository.findByIdWithProduto(skuId)
                    .orElseThrow(() -> new RuntimeException("SKU não encontrado"));
            if (sku.getProduto() == null || !sku.getProduto().getId().equals(produto.getId())) {
                throw new RuntimeException("SKU informado não pertence ao produto");
            }
            return sku;
        }

        if (produto.getSkus() != null && produto.getSkus().size() == 1) {
            return produto.getSkus().get(0);
        }

        throw new RuntimeException("Produto possui múltiplos SKUs; informe o SKU a produzir");
    }
}
