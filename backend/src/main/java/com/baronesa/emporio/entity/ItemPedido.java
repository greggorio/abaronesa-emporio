package com.baronesa.emporio.entity;

import com.baronesa.emporio.enums.StatusItem;
import com.baronesa.emporio.entity.ProdutoSKU;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "item_pedido")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemPedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private Pedido pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id", nullable = false)
    private Produto produto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id")
    private ProdutoSKU sku;

    @Column(nullable = false)
    private Integer quantidade;

    @Column(name = "preco_unitario", precision = 10, scale = 2, nullable = false)
    private BigDecimal precoUnitario;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusItem status;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_cancelamento_codigo", length = 40)
    private com.baronesa.emporio.enums.MotivoCancelamentoItem motivoCancelamentoCodigo;

    @Column(name = "motivo_cancelamento", length = 255)
    private String motivoCancelamento;

    @Column(length = 20)
    @Builder.Default
    private String estacao = "kitchen"; // MVP: default

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;
}
