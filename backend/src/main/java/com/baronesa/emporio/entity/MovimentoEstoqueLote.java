package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "movimento_estoque_lote")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimentoEstoqueLote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "movimento_estoque_id", nullable = false)
    private MovimentoEstoque movimentoEstoque;

    @ManyToOne
    @JoinColumn(name = "estoque_lote_id", nullable = false)
    private EstoqueLote estoqueLote;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}