package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "estoque_lote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EstoqueLote {

    public static final String DEFAULT_LOTE = "SEM_LOTE";
    public static final LocalDate DEFAULT_DATA_VALIDADE = LocalDate.of(1900, 1, 1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "produto_sku_id", nullable = false)
    private ProdutoSKU produtoSku;

    @Column(name = "lote", length = 100)
    private String lote;

    @Column(name = "data_validade")
    private LocalDate dataValidade;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
