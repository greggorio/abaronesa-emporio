package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.TarefaValidadeItemAcao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tarefa_validade_item")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarefaValidadeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tarefa_id", nullable = false)
    private TarefaValidade tarefa;

    @ManyToOne
    @JoinColumn(name = "sku_id", nullable = false)
    private ProdutoSKU sku;

    @Column(name = "lote", length = 100)
    private String lote;

    @Column(name = "data_validade")
    private LocalDate dataValidade;

    @Column(name = "quantidade", nullable = false, precision = 14, scale = 3)
    private BigDecimal quantidade;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "acao", nullable = false, length = 10)
    private TarefaValidadeItemAcao acao = TarefaValidadeItemAcao.SET;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
