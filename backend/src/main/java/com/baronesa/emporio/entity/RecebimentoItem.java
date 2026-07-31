package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "recebimento_item")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"recebimento", "produto"})
@EqualsAndHashCode(exclude = {"recebimento", "produto"})
public class RecebimentoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recebimento_id", nullable = false)
    @JsonBackReference
    private RecebimentoMercadoria recebimento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id")
    private ProdutoSKU sku;

    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "custo_unitario", nullable = false, precision = 15, scale = 4)
    private BigDecimal custoUnitario;

    @Column(name = "valor_total", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(length = 50)
    private String lote;

    @Column(name = "data_validade")
    private LocalDate dataValidade;

    @Column(name = "codigo_produto_fornecedor", length = 50)
    private String codigoProdutoFornecedor;

    @Column(name = "descricao_nfe", length = 255)
    private String descricaoNfe;

    @Column(length = 8)
    private String ncm;

    @Column(length = 4)
    private String cfop;

    @Column(length = 10)
    private String unidade;

    @PrePersist
    @PreUpdate
    public void calcularValorTotal() {
        if (quantidade != null && custoUnitario != null) {
            this.valorTotal = quantidade.multiply(custoUnitario)
                    .setScale(2, BigDecimal.ROUND_HALF_UP);
        } else {
            this.valorTotal = BigDecimal.ZERO;
        }
    }

    // Métodos auxiliares
    public boolean isValid() {
        return produto != null &&
                quantidade != null &&
                quantidade.compareTo(BigDecimal.ZERO) > 0 &&
                custoUnitario != null &&
                custoUnitario.compareTo(BigDecimal.ZERO) >= 0;
    }

    public void updateFromNfe(String codigoFornecedor, String descricao, String ncm, String cfop, String unidade) {
        this.codigoProdutoFornecedor = codigoFornecedor;
        this.descricaoNfe = descricao;
        this.ncm = ncm;
        this.cfop = cfop;
        this.unidade = unidade;
    }
}