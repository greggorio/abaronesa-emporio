package com.baronesa.emporio.service.list;

import com.baronesa.emporio.config.form.base.BaseFormConfig;
import com.baronesa.emporio.config.i18n.MessageResolver;
import com.baronesa.emporio.dto.ProdutoDTO;
import com.baronesa.emporio.dto.dashboard.ProdutoPendenciaDTO;
import com.baronesa.emporio.entity.Estoque;
import com.baronesa.emporio.entity.Produto;
import com.baronesa.emporio.entity.ProdutoSKU;
import com.baronesa.emporio.enums.TipoPrecificacao;
import com.baronesa.emporio.repository.ProdutoRepository;
import com.baronesa.emporio.service.ProdutoService;
import com.baronesa.emporio.service.base.BaseListService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProdutoListService extends BaseListService<Produto> {

    private final ProdutoRepository produtoRepository;
    private final ProdutoService produtoService;

    @Override
    protected JpaSpecificationExecutor<Produto> getRepository() {
        return produtoRepository;
    }

    @Override
    protected Class<Produto> getEntityClass() {
        return Produto.class;
    }

    @Override
    protected BaseFormConfig getFormConfig(MessageResolver resolver) {
        // Por enquanto retorna null já que não temos FormConfig específico
        // Isso será implementado quando as particularidades forem definidas
        return null;
    }

    @Override
    protected Map<String, String> getFieldMappings() {
        return Map.of(
            "categoria", "categoria.nome",
            "subcategoria", "subcategoria.nome",
            "fornecedor", "fornecedor.nomeFantasia",
            // Ordenação por estoque com a mesma regra de exibição (insumo -> centralizado, demais -> soma SKUs)
            "estoque", "estoqueOrdenacao"
        );
    }

    public Map<String, Object> getFormConfig(Long id) {
        Map<String, Object> config = new HashMap<>();

        if (id != null) {
            ProdutoDTO produto = produtoService.buscarPorId(id);
            config.put("produto", produto);
        }

        return config;
    }

    @Override
    protected Map<String, Object> entityToRow(Produto produto) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", produto.getId());
        row.put("nome", produto.getNome());
        row.put("codigoInterno", produto.getCodigoInterno());
        row.put("tipo", produto.getTipo() != null ? produto.getTipo().getDescricao() : null);
        row.put("categoria", produto.getCategoria() != null ? produto.getCategoria().getNome() : null);
        row.put("subcategoria", produto.getSubcategoria() != null ? produto.getSubcategoria().getNome() : null);
        row.put("fornecedor", produto.getFornecedor() != null ? produto.getFornecedor().getNomeFantasia() : null);
        row.put("unidadeMedida", produto.getUnidadeMedida());
        row.put("precoCusto", produto.getPrecoCusto());
        row.put("precoVenda", produto.getPrecoVenda());
        row.put("estoqueAtual", produto.getEstoqueAtual());

        // Estoque exibido:
        // - Produtos insumo: saldo centralizado (unidade base)
        // - Demais produtos: soma dos estoques por SKU (Formula + fallback manual)
        Integer estoqueExibicao;
        if (Boolean.TRUE.equals(produto.getInsumo())) {
            estoqueExibicao = (produto.getEstoqueProduto() != null && produto.getEstoqueProduto().getQuantidadeBase() != null)
                    ? produto.getEstoqueProduto().getQuantidadeBase()
                    : 0;
        } else {
            int estoqueSkus = calcularEstoqueSkus(produto);
            int estoqueFormula = produto.getEstoqueTotal() != null ? produto.getEstoqueTotal() : 0;
            // Garante que exibimos o maior valor conhecido, priorizando a soma real dos SKUs
            estoqueExibicao = Math.max(estoqueFormula, estoqueSkus);
        }
        row.put("estoque", estoqueExibicao);
        row.put("totalSkus", produto.getSkus() != null ? produto.getSkus().size() : 0);

        // Calcular precoMinimo e precoMaximo baseado nos SKUs
        if (produto.getSkus() != null && !produto.getSkus().isEmpty()) {
            java.util.List<java.math.BigDecimal> precos = produto.getSkus().stream()
                    .filter(sku -> sku.getPrecoVenda() != null)
                    .map(com.baronesa.emporio.entity.ProdutoSKU::getPrecoVenda)
                    .toList();

            if (!precos.isEmpty()) {
                row.put("precoMinimo", precos.stream().min(java.math.BigDecimal::compareTo).orElse(null));
                row.put("precoMaximo", precos.stream().max(java.math.BigDecimal::compareTo).orElse(null));
            } else {
                row.put("precoMinimo", null);
                row.put("precoMaximo", null);
            }
        } else {
            row.put("precoMinimo", null);
            row.put("precoMaximo", null);
        }

        row.put("ativo", produto.getAtivo());
        row.put("exibirNoCardapio", produto.getExibirNoCardapio());
        row.put("promocao", produto.getPromocao());
        row.put("destaque", produto.getDestaque());
        row.put("necessitaPreparacao", produto.getNecessitaPreparacao());
        row.put("localPreparacao", produto.getLocalPreparacao());
        row.put("ordem", produto.getOrdem());
        row.put("criadoEm", produto.getCriadoEm());
        row.put("atualizadoEm", produto.getAtualizadoEm());
        return row;
    }

    /**
     * Lista produtos em pendência (sem preço ou sem estoque), reutilizando as mesmas regras dos contadores
     */
    public Map<String, Object> listarPendencias(String tipo, int pagina, int tamanho, boolean apenasAtivos,
                                                String ordenacao, String direcao) {
        Sort sort = definirOrdenacaoPendencias(ordenacao, direcao);
        Pageable pageable = PageRequest.of(pagina, tamanho, sort);

        String tipoNormalizado = tipo != null ? tipo.toLowerCase() : "sem-preco";
        Page<Produto> page;

        if ("sem-estoque".equals(tipoNormalizado) || "sem_estoque".equals(tipoNormalizado)) {
            page = produtoRepository.findProdutosSemEstoque(apenasAtivos, pageable);
            tipoNormalizado = "sem-estoque";
        } else {
            page = produtoRepository.findProdutosSemPreco(
                    Arrays.asList(TipoPrecificacao.SIMPLES, TipoPrecificacao.UNIFICADA),
                    TipoPrecificacao.INDIVIDUAL,
                    apenasAtivos,
                    pageable
            );
            tipoNormalizado = "sem-preco";
        }

        List<ProdutoPendenciaDTO> itens = page.getContent().stream()
                .map(this::toPendenciaDTO)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("items", itens);
        response.put("totalElementos", page.getTotalElements());
        response.put("totalPaginas", page.getTotalPages());
        response.put("paginaAtual", page.getNumber());
        response.put("tamanhoPagina", page.getSize());
        response.put("tipo", tipoNormalizado);

        return response;
    }

    /**
     * Soma o estoque de todos os SKUs do produto, ignorando SKUs sem estoque vinculado
     */
    private int calcularEstoqueSkus(Produto produto) {
        if (produto.getSkus() == null || produto.getSkus().isEmpty()) {
            return 0;
        }

        return produto.getSkus().stream()
                .map(ProdutoSKU::getEstoque)
                .filter(Objects::nonNull)
                .map(Estoque::getQuantidade)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private Sort definirOrdenacaoPendencias(String ordenacao, String direcao) {
        String campo = (ordenacao != null && !ordenacao.isBlank()) ? ordenacao : "atualizadoEm";

        Sort.Direction direction;
        if (direcao != null && !direcao.isBlank()) {
            try {
                direction = Sort.Direction.fromString(direcao);
            } catch (IllegalArgumentException e) {
                direction = Sort.Direction.DESC;
            }
        } else {
            direction = "nome".equalsIgnoreCase(campo) ? Sort.Direction.ASC : Sort.Direction.DESC;
        }

        if ("dataAtualizacao".equalsIgnoreCase(campo)) {
            campo = "atualizadoEm";
        }

        if (!"nome".equalsIgnoreCase(campo) && !"atualizadoEm".equalsIgnoreCase(campo)) {
            campo = "atualizadoEm";
        }

        return Sort.by(direction, campo);
    }

    private ProdutoPendenciaDTO toPendenciaDTO(Produto p) {
        String sku = p.getSkus().stream()
                .filter(s -> s.getSku() != null && !s.getSku().isBlank())
                .map(ProdutoSKU::getSku)
                .findFirst()
                .orElse(p.getCodigoInterno());

        Double custo = null;
        if (p.getTipoPrecificacao() == TipoPrecificacao.SIMPLES || p.getTipoPrecificacao() == TipoPrecificacao.UNIFICADA) {
            custo = p.getPrecoCusto() != null ? p.getPrecoCusto().doubleValue() : null;
        }
        if (custo == null) {
            custo = p.getSkus().stream()
                    .map(ProdutoSKU::getPrecoCusto)
                    .filter(Objects::nonNull)
                    .map(BigDecimal::doubleValue)
                    .min(Double::compareTo)
                    .orElse(null);
        }

        return ProdutoPendenciaDTO.builder()
                .id(p.getId())
                .nome(p.getNome())
                .codigoInterno(p.getCodigoInterno())
                .sku(sku)
                .categoriaNome(p.getCategoria() != null ? p.getCategoria().getNome() : null)
                .subcategoriaNome(p.getSubcategoria() != null ? p.getSubcategoria().getNome() : null)
                .custo(custo)
                .atualizadoEm(p.getAtualizadoEm())
                .build();
    }
}
