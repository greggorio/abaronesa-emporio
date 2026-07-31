package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.TarefaValidadeDivergenciaAcao;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tarefa_validade_divergencia")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TarefaValidadeDivergencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "tarefa_id", nullable = false)
    private TarefaValidade tarefa;

    @ManyToOne
    @JoinColumn(name = "sku_id", nullable = false)
    private ProdutoSKU sku;

    @Column(name = "estoque_agregado", nullable = false, precision = 14, scale = 3)
    private BigDecimal estoqueAgregado;

    @Column(name = "soma_lotes", nullable = false, precision = 14, scale = 3)
    private BigDecimal somaLotes;

    @Column(name = "diferenca", nullable = false, precision = 14, scale = 3)
    private BigDecimal diferenca;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "acao_tomada", nullable = false, length = 20)
    private TarefaValidadeDivergenciaAcao acaoTomada = TarefaValidadeDivergenciaAcao.PENDENTE;

    @ManyToOne
    @JoinColumn(name = "movimento_estoque_id")
    private MovimentoEstoque movimentoEstoque;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
