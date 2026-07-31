package com.baronesa.emporio.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Table(name = "movimento_estoque")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovimentoEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sku_id", nullable = false)
    private ProdutoSKU sku;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(nullable = false)
    private Integer tipoMovimento;

    @Column(nullable = false)
    private BigDecimal quantidade;

    @Column(nullable = false)
    private BigDecimal estoqueAnterior;

    @Column(nullable = false)
    private BigDecimal estoqueAtual;

    // Valores na unidade base do produto (aplicável quando produto é INSUMO)
    // Ex.: ml para bebidas, g para ingredientes sólidos
    private Integer quantidadeBase;
    private Integer estoqueAnteriorBase;
    private Integer estoqueAtualBase;

    @Column(length = 500)
    private String observacao;

    // Referências para rastreabilidade
    private Long vendaId;
    private Long recebimentoId;
    private Long consignacaoId;

    // Vínculo direto com item do pedido
    private Long itemPedidoId;

    // Referência ao movimento original que este estorna
    private Long movimentoOrigemId;

    // Campos de controle
    @Column(length = 100)
    private String documentoReferencia; // NF, Cupom, etc

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime dataMovimento;

    // Novos campos para múltiplos depósitos (futuro)
    private Long localOrigemId;
    private Long localDestinoId;
}
