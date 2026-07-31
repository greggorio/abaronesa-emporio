package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "ficha_tecnica_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FichaTecnicaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ficha_tecnica_id", nullable = false)
    private FichaTecnica fichaTecnica;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_sku_id", nullable = false)
    private ProdutoSKU insumoSku;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantidade;

    @Column(nullable = false)
    @Builder.Default
    private Integer ordem = 0;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    // Método para calcular custo do item
    public BigDecimal calcularCusto() {
        if (insumoSku == null || quantidade == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal precoCusto = insumoSku.getPrecoCusto();
        if (precoCusto == null || precoCusto.compareTo(BigDecimal.ZERO) <= 0) {
            // Fallback para preço de custo do produto
            precoCusto = insumoSku.getProduto() != null
                    ? insumoSku.getProduto().getPrecoCusto()
                    : null;
        }

        if (precoCusto == null || precoCusto.compareTo(BigDecimal.ZERO) <= 0) {
            precoCusto = BigDecimal.ZERO;
        }

        return precoCusto.multiply(quantidade);
    }
}
