package com.baronesa.emporio.service;

import com.baronesa.emporio.dto.SKUOptionDTO;
import com.baronesa.emporio.entity.Embalagem;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.repository.EmbalagemRepository;
import com.baronesa.emporio.repository.ProdutoSKURepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProdutoSKUService {

    private final ProdutoSKURepository produtoSKURepository;
    private final EmbalagemRepository embalagemRepository;

    /**
     * Lista todos os SKUs ativos como options
     */
    @Transactional(readOnly = true)
    public List<SKUOptionDTO> listarOptions() {
        return produtoSKURepository.findAll().stream()
                .filter(sku -> sku.getAtivo() && sku.getProduto().getAtivo())
                .map(this::toOptionDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca SKUs por termo (código, nome, código de barras)
     */
    @Transactional(readOnly = true)
    public List<SKUOptionDTO> buscarOptions(String termo) {
        if (termo == null || termo.trim().isEmpty()) {
            return listarOptions();
        }

        String termoLower = termo.toLowerCase();

        return produtoSKURepository.findAll().stream()
                .filter(sku -> sku.getAtivo() && sku.getProduto().getAtivo())
                .filter(sku ->
                        sku.getSku().toLowerCase().contains(termoLower) ||
                                sku.getProduto().getNome().toLowerCase().contains(termoLower) ||
                                (sku.getCodigoBarras() != null && sku.getCodigoBarras().contains(termo))
                )
                .map(this::toOptionDTO)
                .collect(Collectors.toList());
    }

    /**
     * Busca SKUs com paginação para autocomplete
     */
    @Transactional(readOnly = true)
    public Page<SKUOptionDTO> buscarOptionsPaginado(String termo, Long produtoId, Pageable pageable) {
        // TODO: Implementar query otimizada com Specification
        // Por enquanto, usar implementação simples
        List<SKUOptionDTO> todos = buscarOptions(termo);
        if (produtoId != null) {
            todos = todos.stream()
                    .filter(opt -> {
                        try {
                            // Recupera entidade rapidamente para checar produto (nota: otimização futura com join)
                            Long id = opt.value();
                            ProdutoSKU s = produtoSKURepository.findById(id).orElse(null);
                            return s != null && s.getProduto() != null && produtoId.equals(s.getProduto().getId());
                        } catch (Exception e) { return false; }
                    })
                    .collect(Collectors.toList());
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), todos.size());

        return new PageImpl<>(
                todos.subList(Math.min(start, todos.size()), Math.min(end, todos.size())),
                pageable,
                todos.size()
        );
    }

    /**
     * Busca SKU por ID
     */
    @Transactional(readOnly = true)
    public ProdutoSKU buscarPorId(Long id) {
        return produtoSKURepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SKU não encontrado com ID: " + id));
    }

    @Transactional
    public ProdutoSKU atualizarEmbalagem(Long skuId, Long embalagemId) {
        ProdutoSKU sku = buscarPorId(skuId);
        Embalagem embalagem = embalagemRepository.findById(embalagemId)
                .orElseThrow(() -> new RuntimeException("Embalagem não encontrada"));
        if (!embalagem.getProduto().getId().equals(sku.getProduto().getId())) {
            throw new RuntimeException("Embalagem pertence a outro produto");
        }
        sku.setEmbalagem(embalagem);
        return produtoSKURepository.save(sku);
    }

    /**
     * Converte ProdutoSKU para SKUOptionDTO
     */
    private SKUOptionDTO toOptionDTO(ProdutoSKU sku) {
        String label = formatarLabel(sku);

        Integer estoqueVisual;
        if (sku.getProduto() != null && Boolean.TRUE.equals(sku.getProduto().getInsumo())) {
            // Deriva quantidade a partir do estoque central do produto dividido pelo fator da embalagem
            int base = (sku.getProduto().getEstoqueProduto() != null && sku.getProduto().getEstoqueProduto().getQuantidadeBase() != null)
                    ? sku.getProduto().getEstoqueProduto().getQuantidadeBase()
                    : 0;
            int fator = 1;
            if (sku.getEmbalagem() != null && sku.getEmbalagem().getFatorBase() != null && sku.getEmbalagem().getFatorBase() > 0) {
                fator = sku.getEmbalagem().getFatorBase();
            } else if (sku.getProduto().getEmbalagens() != null) {
                for (Embalagem e : sku.getProduto().getEmbalagens()) {
                    if (Boolean.TRUE.equals(e.getPrincipal()) && e.getFatorBase() != null && e.getFatorBase() > 0) {
                        fator = e.getFatorBase();
                        break;
                    }
                }
            }
            estoqueVisual = fator > 0 ? (base / fator) : 0;
        } else {
            estoqueVisual = (sku.getEstoque() != null && sku.getEstoque().getQuantidade() != null)
                    ? sku.getEstoque().getQuantidade()
                    : 0;
        }

        return new SKUOptionDTO(
                sku.getId(),
                label,
                sku.getCodigoBarras(),
                sku.getPrecoVenda() != null ? sku.getPrecoVenda().doubleValue() : null,
                estoqueVisual
        );
    }

    /**
     * Formata o label para exibição no select
     * Formato: SKU - Nome do Produto (Variacao) [Estoque: X]
     */
    private String formatarLabel(ProdutoSKU sku) {
        StringBuilder label = new StringBuilder();

        // Código SKU
        label.append(sku.getSku());
        label.append(" - ");

        // Nome do produto
        label.append(sku.getProduto().getNome());

        // Variação (se existir)
        if (sku.getVariacao() != null && !sku.getVariacao().isBlank()) {
            label.append(" (");
            label.append(sku.getVariacao());
            label.append(")");
        }

        // Embalagem (se vinculada)
        if (sku.getEmbalagem() != null) {
            label.append(" [");
            label.append(sku.getEmbalagem().getNome());
            Integer fator = sku.getEmbalagem().getFatorBase();
            if (fator != null) {
                label.append(": ").append(fator);
            }
            label.append("]");
        }

        // Estoque
        if (sku.getProduto() != null && Boolean.TRUE.equals(sku.getProduto().getInsumo())) {
            int base = (sku.getProduto().getEstoqueProduto() != null && sku.getProduto().getEstoqueProduto().getQuantidadeBase() != null)
                    ? sku.getProduto().getEstoqueProduto().getQuantidadeBase()
                    : 0;
            int fator = 1;
            if (sku.getEmbalagem() != null && sku.getEmbalagem().getFatorBase() != null && sku.getEmbalagem().getFatorBase() > 0) {
                fator = sku.getEmbalagem().getFatorBase();
            } else if (sku.getProduto().getEmbalagens() != null) {
                for (Embalagem e : sku.getProduto().getEmbalagens()) {
                    if (Boolean.TRUE.equals(e.getPrincipal()) && e.getFatorBase() != null && e.getFatorBase() > 0) {
                        fator = e.getFatorBase();
                        break;
                    }
                }
            }
            int qtd = fator > 0 ? (base / fator) : 0;
            label.append(" [Estoque: ").append(qtd).append("]");
        } else if (sku.getEstoque() != null) {
            label.append(" [Estoque: ");
            label.append(sku.getEstoque().getQuantidade());
            label.append("]");
        }

        return label.toString();
    }
}
