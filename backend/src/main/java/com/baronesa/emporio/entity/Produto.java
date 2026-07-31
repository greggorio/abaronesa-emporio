package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.LocalPreparacao;
import com.baronesa.emporio.enums.TipoPrecificacao;
import com.baronesa.emporio.enums.TipoProduto;
import com.baronesa.emporio.enums.UnidadeMedida;
import com.baronesa.emporio.enums.UnidadeBase;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Formula;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "produto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(length = 100)
    private String setor;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(unique = true, length = 50)
    private String codigoInterno;

    @Column(length = 50)
    private String codigoBarras;

    @Column(length = 50)
    private String codigoFornecedor;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private TipoProduto tipo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UnidadeMedida unidadeMedida;

    // Unidade base (atômica) para controle de estoque agregado (UN, ML, G)
    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private UnidadeBase unidadeBase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subcategoria_id")
    private Subcategoria subcategoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fornecedor_id")
    private Fornecedor fornecedor;

    // Preços
    @Column(precision = 10, scale = 2)
    private BigDecimal precoCusto;

    @Column(precision = 10, scale = 2)
    private BigDecimal precoVenda;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private TipoPrecificacao tipoPrecificacao = TipoPrecificacao.SIMPLES;

    // Estoque
    @Builder.Default
    @Column(nullable = false)
    private Boolean controlaEstoque = true;

    @Builder.Default
    @Column(name = "controla_validade", nullable = false)
    private Boolean controlaValidade = true;

    @Column(name = "vida_util_dias")
    private Integer vidaUtilDias;

    @Column(precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal estoqueAtual = BigDecimal.ZERO;

    // Flags
    @Builder.Default
    @Column(nullable = false)
    private Boolean ativo = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean vendavel = true;

    // Define se o produto é um insumo (estoque compartilhado por SKUs na unidade base)
    @Builder.Default
    @Column
    private Boolean insumo = false;

    // Define se o produto possui ficha técnica (composição de insumos)
    @Builder.Default
    @Column
    private Boolean temFichaTecnica = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean exibirNoCardapio = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean promocao = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean destaque = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean necessitaPreparacao = true; // Define se o produto precisa de preparação (ex: bebidas = false, pratos = true)

    @Builder.Default
    @Column(nullable = false)
    private Boolean producaoPropria = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private LocalPreparacao localPreparacao; // Define se a preparação é no BAR ou na COZINHA

    @Builder.Default
    private Integer ordem = 0; // Ordem de exibição no cardápio

    // Imagem
    private String imagemPrincipal;

    // Relacionamentos
    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProdutoSKU> skus = new ArrayList<>();

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProdutoMidia> midias = new ArrayList<>();

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Embalagem> embalagens = new ArrayList<>();

    @OneToOne(mappedBy = "produto", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ProdutoFiscal produtoFiscal;

    // Auditoria
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    private LocalDateTime atualizadoEm;

    // Estoque centralizado por produto (na unidade base)
    @OneToOne(mappedBy = "produto", cascade = CascadeType.ALL, orphanRemoval = true)
    private EstoqueProduto estoqueProduto;

    // Estoque agregado dos SKUs (para ordenação/consulta)
    @Formula("(select coalesce(sum(e.quantidade), 0) " +
            "from produto_sku s left join estoque e on e.id = s.estoque_id " +
            "where s.produto_id = id)")
    private Integer estoqueTotal;

    /**
     * Campo auxiliar para ordenação de estoque, refletindo a regra de exibição:
     * - insumo=true  -> estoque centralizado (quantidade_base)
     * - insumo=false -> soma dos estoques dos SKUs
     * Nulls são tratados como 0.
     */
    @Formula("""
        case
            when coalesce(insumo, false) = true then coalesce(
                (select ep.quantidade_base from estoque_produto ep where ep.produto_id = id), 0)
            else coalesce(
                (select sum(e.quantidade) from produto_sku s left join estoque e on e.id = s.estoque_id where s.produto_id = id), 0)
        end
    """)
    private Integer estoqueOrdenacao;

    // Métodos auxiliares
    public void adicionarSKU(ProdutoSKU sku) {
        skus.add(sku);
        sku.setProduto(this);
    }

    public void removerSKU(ProdutoSKU sku) {
        skus.remove(sku);
        sku.setProduto(null);
    }

    public void adicionarMidia(ProdutoMidia midia) {
        midias.add(midia);
        midia.setProduto(this);
    }

    public void removerMidia(ProdutoMidia midia) {
        midias.remove(midia);
        midia.setProduto(null);
    }

    /**
     * Obtém informações fiscais do produto
     */
    public ProdutoFiscal getInformacoesFiscais() {
        return produtoFiscal;
    }

    /**
     * Define informações fiscais do produto
     */
    public void setInformacoesFiscais(ProdutoFiscal fiscal) {
        this.produtoFiscal = fiscal;
        if (fiscal != null) {
            fiscal.setProduto(this);
        }
    }

    public boolean temVariacao() {
        return tipoPrecificacao != TipoPrecificacao.SIMPLES && !skus.isEmpty() && skus.size() > 1;
    }
}
