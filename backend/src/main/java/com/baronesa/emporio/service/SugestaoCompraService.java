package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.SugestaoCompraItemDTO;
import com.baronesa.emporio.entity.*;
import com.baronesa.emporio.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SugestaoCompraService {

    private final ProdutoRepository produtoRepository;
    private final ProdutoSKURepository skuRepository;
    private final EstoqueProdutoRepository estoqueProdutoRepository;

    @Transactional(readOnly = true)
    public List<SugestaoCompraItemDTO> gerarSugestoes(boolean somenteCriticos) {
        // Buscar todos produtos ativos que controlam estoque
        List<Produto> produtos = produtoRepository.findAll();

        List<SugestaoCompraItemDTO> sugestoes = new ArrayList<>();

        for (Produto p : produtos) {
            if (Boolean.FALSE.equals(p.getAtivo()) || Boolean.FALSE.equals(p.getControlaEstoque())) {
                continue;
            }

            if (Boolean.TRUE.equals(p.getInsumo())) {
                // INSUMO: usar estoque_produto na base
                EstoqueProduto ep = p.getEstoqueProduto();
                if (ep == null) {
                    continue;
                }
                int atualBase = ep.getQuantidadeBase() == null ? 0 : ep.getQuantidadeBase();
                int minBase = ep.getEstoqueMinimoBase() == null ? 0 : ep.getEstoqueMinimoBase();

                // Requisito: considerar apenas quando min > 0 e estoque atual < mínimo
                boolean critico = (minBase > 0) && (atualBase < minBase);
                if (!critico) {
                    // Sempre filtrar fora itens com mínimo zero ou não críticos
                    continue;
                }

                // escolher embalagem principal se existir
                Embalagem embalagem = null;
                if (p.getEmbalagens() != null) {
                    embalagem = p.getEmbalagens().stream()
                            .filter(e -> Boolean.TRUE.equals(e.getPrincipal()))
                            .findFirst()
                            .orElse(p.getEmbalagens().stream().findFirst().orElse(null));
                }
                Integer fator = embalagem != null ? embalagem.getFatorBase() : 1;
                if (fator == null || fator <= 0) fator = 1;

                int deficitBase = Math.max(0, minBase - atualBase);
                int pacotes = (int) Math.ceil(deficitBase / (double) fator);

                sugestoes.add(new SugestaoCompraItemDTO(
                        p.getId(),
                        p.getNome(),
                        true,
                        null,
                        null,
                        embalagem != null ? embalagem.getId() : null,
                        embalagem != null ? embalagem.getNome() : null,
                        fator,
                        atualBase,
                        minBase,
                        null,
                        null,
                        BigDecimal.valueOf(pacotes),
                        p.getPrecoCusto(),
                        "ABAIXO_DO_MINIMO"
                ));

            } else {
                // VENDÁVEL: avaliar por SKU
                List<ProdutoSKU> skus = p.getSkus();
                if (skus == null || skus.isEmpty()) continue;
                for (ProdutoSKU sku : skus) {
                    Estoque est = sku.getEstoque();
                    int atual = (est != null && est.getQuantidade() != null) ? est.getQuantidade() : 0;
                    int min = (est != null && est.getEstoqueMinimo() != null) ? est.getEstoqueMinimo() : 0;
                    // Requisito: considerar apenas quando min > 0 e estoque atual < mínimo
                    boolean critico = (min > 0) && (atual < min);
                    if (!critico) continue;

                    int deficit = Math.max(0, min - atual);
                    BigDecimal sugerida = BigDecimal.valueOf(deficit);

                    Embalagem emb = sku.getEmbalagem();
                    sugestoes.add(new SugestaoCompraItemDTO(
                            p.getId(),
                            p.getNome(),
                            false,
                            sku.getId(),
                            sku.getSku(),
                            emb != null ? emb.getId() : null,
                            emb != null ? emb.getNome() : null,
                            emb != null ? emb.getFatorBase() : null,
                            null,
                            null,
                            atual,
                            min,
                            sugerida,
                            sku.getPrecoCusto(),
                            "ABAIXO_DO_MINIMO"
                    ));
                }
            }
        }

        // Ordenar: críticos primeiro, depois por nome
        return sugestoes.stream()
                .sorted(Comparator.comparing((SugestaoCompraItemDTO s) -> s.motivo() != null && s.motivo().equals("ABAIXO_DO_MINIMO") ? 0 : 1)
                        .thenComparing(SugestaoCompraItemDTO::produtoNome, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }
}
